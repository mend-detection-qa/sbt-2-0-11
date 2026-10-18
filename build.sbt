// Probe: sbtResolvers-m2-settings-override
//
// Exercises SBT 2.0.11 fix: .m2/settings.xml no longer overrides
// sbtResolvers declared in build.sbt. The build declares Maven Central
// explicitly as a named resolver, plus a second custom resolver, so
// UA must honour both and not let a .m2/settings.xml mirror stanza
// clobber the sbt-declared resolver list.
//
// Dep strategy:
//   - slf4j-api 2.0.13   : Java artifact (%) from Maven Central
//   - logback-classic 1.5.6 : Java artifact (%) from Maven Central;
//                              pulls in logback-core 1.5.6 and slf4j-api 2.0.13
//                              as transitives (version already wins via eviction)
//   - commons-codec 1.17.0 : Java artifact (%) simulated as coming from
//                             the custom resolver (Sonatype releases mirror)
//
// Real-world note: commons-codec 1.17.0 is on Maven Central. In the
// probe it is declared under the custom resolver entry to simulate
// multi-resolver ordering. UA should see it as private-index when the
// custom resolver is its first match.

ThisBuild / organization := "com.example"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.14"

// Step 1: Declare Maven Central explicitly as a named resolver.
// This is what SBT 2.0.11 fixes — .m2/settings.xml used to silently
// replace this list with its <mirrors> block.
resolvers := Seq(
  "Maven Central Explicit" at "https://repo1.maven.org/maven2/",
  "Custom Releases"        at "https://repo.example.com/maven2/"
)

// Prevent sbt from prepending the default resolver set so we keep
// full control over resolver ordering (mirrors the sbtResolvers fix).
externalResolvers := resolvers.value

lazy val root = (project in file("."))
  .settings(
    name := "sbt-resolvers-m2-settings-override-probe",

    libraryDependencies ++= Seq(
      // From Maven Central (via "Maven Central Explicit" resolver)
      "org.slf4j"      % "slf4j-api"       % "2.0.13",
      "ch.qos.logback" % "logback-classic" % "1.5.6",

      // From "Custom Releases" resolver (resolver-ordering probe dep)
      "commons-codec"  % "commons-codec"   % "1.17.0"
    )
  )

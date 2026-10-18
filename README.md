# sbtResolvers-m2-settings-override

Probe for SBT 2.0.11 `registry_source` fix: `.m2/settings.xml`
no longer overrides `sbtResolvers` declared in `build.sbt`.

## Feature exercised

SBT 2.0.11 fixed a bug where a `<mirror>` stanza in
`~/.m2/settings.xml` would silently replace the `resolvers`
(i.e. `sbtResolvers`) declared in `build.sbt`, routing all artifact
resolution through the mirror URL. This broke Mend UA's ability to
pick up dependencies from the project's intended resolvers, and could
cause UA to miss artifacts entirely (if the mirror does not carry
them) or attribute them to the wrong source.

This probe exercises:

1. Maven Central declared **explicitly** as a named resolver
   (`"Maven Central Explicit" at "https://repo1.maven.org/maven2/"`).
2. A second custom resolver (`"Custom Releases" at
   "https://repo.example.com/maven2/"`) with a dep attributed to it
   to exercise resolver ordering.
3. `externalResolvers := resolvers.value` to prevent sbt from
   prepending the default resolver set — keeps the declared list
   authoritative.
4. A stub `.m2/settings.xml` (in `probe-root/.m2/`) with a
   `<mirrorOf>*</mirrorOf>` entry pointing at the custom resolver
   URL, so the QA harness can copy it to `~/.m2/settings.xml` to
   reproduce the pre-fix bug environment.

## Dependencies declared

| Artifact | Version | Resolver | `%`/`%%` |
|---|---|---|---|
| `org.slf4j:slf4j-api` | `2.0.13` | Maven Central Explicit | `%` (Java) |
| `ch.qos.logback:logback-classic` | `1.5.6` | Maven Central Explicit | `%` (Java) |
| `commons-codec:commons-codec` | `1.17.0` | Custom Releases | `%` (Java) |

## Transitive dependencies

`logback-classic 1.5.6` pulls in:

- `ch.qos.logback:logback-core 1.5.6` (compile)
- `org.slf4j:slf4j-api 2.0.13` (compile — same version as direct
  dep, no eviction needed; highest-wins resolves to 2.0.13)

`commons-codec 1.17.0` has no transitive managed deps beyond
`slf4j-api` (not in its dependency set at this version).

## Expected dependency tree

```
root: sbt-resolvers-m2-settings-override-probe 0.1.0-SNAPSHOT
  ├── slf4j-api 2.0.13 (registry / main)
  ├── logback-classic 1.5.6 (registry / main)
  │   ├── logback-core 1.5.6 (registry / main)
  │   └── slf4j-api 2.0.13 (registry / main) [already listed above]
  └── commons-codec 1.17.0 (private-index / main)
```

Eviction: `slf4j-api` 2.0.13 is declared directly and also pulled
transitively via `logback-classic`. Coursier (highest-wins) resolves
to 2.0.13 in both paths — no eviction needed; both paths agree.

## Mend config

Bucket A — `.whitesource` is always required for `scala-sbt` (no
dynamic version detection from the manifest).

```json
{
  "scanSettings": {
    "configMode": "LOCAL",
    "versioning": {
      "sbt": "2.0.11",
      "scala": "2.13.14",
      "java": "17.0.10"
    }
  }
}
```

`configMode` is `"LOCAL"` (not `"AUTO"`) because a real
`whitesource.config` is present in the probe root. The
`whitesource.config` sets:

```properties
sbt.resolveDependencies=true
sbt.runPreStep=true
sbt.newSbtResolution=true
sbt.ignoreSourceFiles=true
```

`sbt.newSbtResolution=true` activates the new resolver path (dot-file
+ lock-file based), which is the code path affected by the SBT
2.0.11 fix. `sbt.runPreStep=true` ensures UA invokes `sbt compile`
before scanning, generating `resolved.xml.xml` and dot files.

## Resolver knowledge notes

From `scala-sbt.md` (fetched 2026-10-18, SHA
`5e9eba4650880dc15b52fea839f918c1d9fb9afd`):

- **New SBT Resolver** (active when `sbt.newSbtResolution=true`):
  injects a dependency-tree plugin, runs `dependencyDot` per scope,
  parses the resulting `.dot` files. This is the path where the
  `sbtResolvers` override bug manifests — if UA's sbt invocation
  resolves through the wrong resolver list, the dot file will either
  be empty or contain wrong artifacts.
- **Old SBT Resolver** (default): falls back to `resolved.xml.xml`
  in Ivy cache. The same bug can affect it differently — Ivy uses its
  own resolver chain which may also be overridden by `.m2/settings.xml`
  mirror config via the `MavenLocal` resolver.
- `scala-library` is always excluded from the UA output. Do not
  include it in `expected-tree.json`.

## QA harness instructions

To reproduce the **pre-fix (buggy) scenario**:

1. Copy `probe-root/.m2/settings.xml` to `~/.m2/settings.xml` on the
   scan host before running UA.
2. Use sbt < 2.0.11 (pin `sbt: "1.10.7"` in `.whitesource.versioning`
   for the regression pair).
3. Expected outcome: UA misses `slf4j-api` and `logback-*` (not
   served by `https://repo.example.com/maven2/`), or all deps are
   reported without resolver-source attribution.

To reproduce the **post-fix (correct) scenario**:

1. Same `.m2/settings.xml` in place.
2. Use sbt 2.0.11 (`.whitesource.versioning.sbt: "2.0.11"`).
3. Expected outcome: tree matches `expected-tree.json` exactly.

## Probe metadata

| Field | Value |
|---|---|
| Pattern | `sbtResolvers-m2-settings-override` |
| PM | sbt |
| PM version tested | 2.0.11 |
| Schema version | 1.2 |
| Categories | registry_source |
| Source | SBT 2.0.11 release notes |
| Status | Added 2026-10-18 (untested against live UA scan) |
| Generated at | 2026-10-18T00:00:00Z |

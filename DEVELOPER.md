
# jacoco-method-filter

**Scala-based bytecode rewriter for Java/Scala projects** that injects an annotation whose simple name contains `Generated` into selected methods *before* JaCoCo reporting.  
Since **JaCoCo ≥ 0.8.2** ignores classes and methods annotated with an annotation whose simple name contains `Generated` (with retention `CLASS` or `RUNTIME`), this lets you **filter coverage at the method level** without touching your source code — and keep **HTML and XML numbers consistent**.

- [How to Build](#how-to-build)
- [Installation](#installation)
  - [Local Development](#local-development)
  - [Release to Public](#release-to-public)

---

## How to Build

Requirements: JDK 17+ and sbt.

```bash
sbt +compile
```

---

## Installation

### Local Development

If you want to try the plugin and core library locally before release, publish them to your local Ivy/Maven repositories:

```bash
# from jacoco-method-filter repo root
sbt "project rewriterCore" +publishLocal   # publishes jacoco-method-filter-core for all Scala versions
sbt "project sbtPlugin"    publishLocal    # publishes jacoco-method-filter-sbt for sbt 1.x (Scala 2.12)
```

Artifacts will appear in:

- ~/.ivy2/local/MoranaApps/...
- ~/.m2/repository/MoranaApps/...

#### sbt (local snapshot)

```scala
// project/plugins.sbt
resolvers += Resolver.ivyLocal
addSbtPlugin("MoranaApps" % "jacoco-method-filter-sbt" % "0.1.0-SNAPSHOT")

// build.sbt
enablePlugins(morana.coverage.JacocoFilterPlugin)
libraryDependencies += "MoranaApps" %% "jacoco-method-filter-core" % "0.1.0-SNAPSHOT"
```

#### Maven (local snapshot)

```xml
<repositories>
  <repository>
    <id>local-ivy</id>
    <url>file://${user.home}/.ivy2/local</url>
  </repository>
  <repository>
    <id>local-maven</id>
    <url>file://${user.home}/.m2/repository</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>MoranaApps</groupId>
    <artifactId>jacoco-method-filter-core_2.13</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
</dependencies>
```

### Release to Public

To make the library and plugin usable without `resolvers += Resolver.ivyLocal`:

1. **Set up Sonatype OSSRH** (or your organization’s Nexus/Artifactory).
  - Configure `~/.sbt/sonatype.sbt` with credentials.
  - Add `publishTo := sonatypePublishToBundle.value` in `build.sbt`.
2. **Metadata**
  - Add `licenses`, `scmInfo`, and `developers` to `build.sbt`.
  - Use semantic versioning (e.g. `1.0.0`).
3. **Release**
```bash
sbt +clean +test
sbt +publishSigned           # publish core
sbt "project sbtPlugin" publishSigned
sbt sonatypeBundleRelease    # close & release staging repo
```
4. After release, artifacts are available on Maven Central:
- `MoranaApps:jacoco-method-filter-core_2.13:1.0.0`
- `MoranaApps:jacoco-method-filter-sbt:sbtVersion=1.0;scalaVersion=2.12:1.0.0`

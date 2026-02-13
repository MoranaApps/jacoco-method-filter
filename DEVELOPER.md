
# jacoco-method-filter

**Scala-based bytecode rewriter for Java/Scala projects** that injects an annotation whose simple name contains
`Generated` into selected methods *before* JaCoCo reporting. Since **JaCoCo ≥ 0.8.2** ignores classes and methods
annotated with an annotation whose simple name contains `Generated` (with retention `CLASS` or `RUNTIME`), this lets
you **filter coverage at the method level** without touching your source code — and keep **HTML and XML numbers
consistent**.

- [How to Build](#how-to-build)
- [Installation](#installation)
  - [Local Development](#local-development)

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
sbt "project sbtPlugin"    publishLocal    # publishes jacoco-method-filter-sbt sbt plugin

# Maven plugin (requires core in local M2 first)
sbt "project rewriterCore" ++2.12.21 publishM2   # publish core to ~/.m2/repository
cd maven-plugin && mvn install && cd ..           # build and install Maven plugin locally
```

Artifacts will appear in:

- ~/.ivy2/local/io.github.moranaapps/...
- ~/.m2/repository/io/github/moranaapps/...

#### sbt (local snapshot)

```scala
// project/plugins.sbt
resolvers += Resolver.defaultLocal
addSbtPlugin("io.github.moranaapps" % "jacoco-method-filter-sbt" % "2.0.0")

// build.sbt
enablePlugins(morana.coverage.JacocoFilterPlugin)
libraryDependencies += "io.github.moranaapps" %% "jacoco-method-filter-core" % "2.0.0"
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
    <groupId>io.github.moranaapps</groupId>
    <artifactId>jacoco-method-filter-core_2.13</artifactId>
    <version>2.0.0</version>
  </dependency>
</dependencies>
```

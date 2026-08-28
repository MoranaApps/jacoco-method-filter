# Release Guide

This document describes how to publish **jacoco-method-filter** artifacts to Maven Central via the
[Central Publisher Portal (CPP)](https://central.sonatype.com).

Releases are fully automated using GitHub Actions.

- [Publish / Release Sonatype](./.github/workflows/release_sonatype.yml) workflow.
  - You only need to bump the version, push a commit, and trigger the workflow.
- [Release - create draft release](./.github/workflows/release_draft.yml) GH draft workflow.
  - You need to manually start the action and provide the version.

---

## 1. Prerequisites (one-time)

- Namespace `io.github.moranaapps` verified in CPP ✅
- GitHub repository has these **Secrets** configured:
  - `SONATYPE_USERNAME` → Central token username (looks like `central:abcd123...`)
  - `SONATYPE_PASSWORD` → Central token password
  - `PGP_SECRET` → ASCII-armored GPG private key (`-----BEGIN PGP PRIVATE KEY BLOCK-----`)
  - `PGP_PASSPHRASE` → passphrase used when generating the key
  - `TOKEN` → GitHub token used by `release_draft.yml` (tag creation + release notes)
- `sbt.version=1.11.0` in `project/build.properties`
- `sbt-pgp` plugin in `project/plugins.sbt`:

```scala
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.1")
```

---

## 2. Prepare the release

- **Bump the version** in all locations listed below. Search for the old version string and replace it with the new one.

  **Core build files** (the version being published):
  1. `build.sbt` (top-level) — sbt core library + sbt plugin:

     ```scala
     ThisBuild / version := "0.2.0"
     ```

  2. `maven-plugin/pom.xml` — Maven plugin (both its own version and the core dependency):

     ```xml
     <version>0.2.0</version>
     ...
     <artifactId>jacoco-method-filter-core_2.12</artifactId>
     <version>0.2.0</version>
     ```

  **Example projects** (consumer-side references to the published artifacts):
  3. `examples/sbt-basic/project/plugins.sbt` — sbt plugin version used in the sbt example
  4. `examples/maven-basic/pom.xml` — Maven plugin version used in the Maven Java example
  5. `examples/maven-scala/pom.xml` — Maven plugin version used in the Maven Scala example

  **Integration-test fixtures** (must match the version being released so CI runs against the new artifacts):
  6. `integration-tests/fixtures/sbt-basic/project/plugins.sbt`
  7. `integration-tests/fixtures/sbt-scala211/project/plugins.sbt`
  8. `integration-tests/test-maven-report-custom.sh` — inline `<version>` in the generated `pom-custom.xml`

  **Docs & defaults** (user-facing references to the current version):
  9. `sbt-plugin/src/main/scala/morana/coverage/JacocoFilterPlugin.scala` — `jmfCoreVersion` default
  10. `DEVELOPER.md` — install snippets
  11. `sbt-plugin/README.md` — install snippet
  12. `maven-plugin/README.md` — install snippets

  **Changelog**:
  13. `CHANGELOG.md` — rename `## [Unreleased]` to `## [<version>] — <date>`, add a fresh empty
      `## [Unreleased]`, and update the link references at the bottom (`[Unreleased]`, new `[<version>]`).

  - Use [semantic versioning](https://semver.org/).
  - Do **not** include `-SNAPSHOT`.

- Commit and push:

```bash
git add -A
git commit -m "Release 0.2.0"
git push origin master
```

---

## 3. Trigger the release workflow

- **1.** Go to **GitHub → Actions → Publish / Release (manual)**.  
- **2.** Click **“Run workflow”** (top-right).  
- **3.** Options:
  - `doRelease`:  
    - `yes` → publish + release immediately to Maven Central  
    - `no` → only stage the bundle; downloadable as artifact

Notes:

- The workflow cross-publishes **Scala 2.11 / 2.12 / 2.13** under **JDK 17**.
- The core library is published as a **fat JAR** with ASM shaded (relocated to `jmf.shaded.asm`).
  The published POM declares exactly 2 runtime dependencies: `scala-library` and `scopt`.
- The **Maven plugin** is built and deployed via `mvn deploy` using the
  `central-publishing-maven-plugin` in the same workflow.
- `release_draft.yml` validates tags against branch `master`.

---

## 4. Verify the release

- **Staging only (`doRelease = no`)**:
  - Download the `sonatype-staging-bundles` artifact from the workflow run.
  - Inspect contents: jars, POMs, sources, javadocs, signatures.

- **Release (`doRelease = yes`)**:
  - Artifacts appear in Central Portal under your namespace within minutes.
  - After ~15–30 minutes they become searchable at [search.maven.org](https://search.maven.org).

Expected coordinates:

- Core library (cross-built fat JAR with shaded ASM; runtime deps: `scala-library`, `scopt`):

```scala
io.github.moranaapps:jacoco-method-filter-core_2.{11,12,13}:<version>
```

- sbt plugin:

```scala
addSbtPlugin("io.github.moranaapps" % "jacoco-method-filter-sbt" % "<version>")
```

- Maven plugin:

```xml
<groupId>io.github.moranaapps</groupId>
<artifactId>jacoco-method-filter-maven-plugin</artifactId>
<version><!-- version --></version>
```

---

## 5. Post-release

- Bump `version` in `build.sbt` and `maven-plugin/pom.xml` to the next **`-SNAPSHOT`**
  (e.g., `0.3.0-SNAPSHOT`) for ongoing dev.
- Commit and push.

```bash
git commit -am "Set version to 0.3.0-SNAPSHOT"
git push origin master
```

---

## 6. Quick checklist

- [ ] Version updated in `build.sbt` and `maven-plugin/pom.xml` (core build)
- [ ] Version updated in `examples/sbt-basic/project/plugins.sbt`, `examples/maven-basic/pom.xml`,
 `examples/maven-scala/pom.xml` (examples)
- [ ] Version updated in `integration-tests/fixtures/sbt-basic/project/plugins.sbt`,
 `integration-tests/fixtures/sbt-scala211/project/plugins.sbt`,
 `integration-tests/test-maven-report-custom.sh` (integration-test fixtures)
- [ ] Version updated in `sbt-plugin/src/main/scala/morana/coverage/JacocoFilterPlugin.scala`
 (`jmfCoreVersion` default), `DEVELOPER.md`, `sbt-plugin/README.md`, `maven-plugin/README.md` (docs)
- [ ] `CHANGELOG.md` updated: `[Unreleased]` → `[<version>] — <date>`, fresh `[Unreleased]`, link refs
- [ ] Commit pushed to `master`
- [ ] Workflow triggered via GitHub Actions
- [ ] Artifacts staged or released successfully (sbt + Maven plugin)
- [ ] Verified on [search.maven.org](https://search.maven.org)

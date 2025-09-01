# Release Guide

This document describes how to publish **jacoco-method-filter** artifacts to Maven Central via the
[Central Publisher Portal (CPP)](https://central.sonatype.com).

Releases are fully automated using GitHub Actions.

- [Publish / Release Sonatype](./.github/workflows/publish-sonatype.yml) workflow.
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
- `sbt.version=1.11.0` in `project/build.properties`
- `sbt-pgp` plugin in `project/plugins.sbt`:

```scala
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.2.1")
```

---

## 2. Prepare the release

1. **Bump the version** in `build.sbt` (top-level):
2. 
```scala
ThisBuild / version := "0.2.0"
```

- Use [semantic versioning](https://semver.org/).
- Do **not** include `-SNAPSHOT`.

2. Commit and push:
3. 
```bash
git add build.sbt
git commit -m "Release 0.2.0"
git push origin master
```

---

## 3. Trigger the release workflow

1. Go to **GitHub → Actions → Publish / Release (manual)**.  
2. Click **“Run workflow”** (top-right).  
3. Options:
- `doRelease`:  
  - `yes` → publish + release immediately to Maven Central  
  - `no` → only stage the bundle; downloadable as artifact
- `runScripted`:  
  - `true` → also run sbt-plugin scripted tests  
  - `false` → skip them (faster)

---

## 4. Verify the release

- **Staging only (`doRelease = no`)**:
  - Download the `sona-staging-bundle` artifact from the workflow run.
  - Inspect contents: jars, POMs, sources, javadocs, signatures.

- **Release (`doRelease = yes`)**:
  - Artifacts appear in Central Portal under your namespace within minutes.
  - After ~15–30 minutes they become searchable at [search.maven.org](https://search.maven.org).

Expected coordinates:

- Core library:
- 
```
io.github.moranaapps:jacoco-method-filter-core_2.13:<version>
```

- sbt plugin:

```scala
addSbtPlugin("io.github.moranaapps" % "jacoco-method-filter-sbt" % "<version>")
```

---

## 5. Post-release

- Bump `version` in `build.sbt` to the next **`-SNAPSHOT`** (e.g., `0.3.0-SNAPSHOT`) for ongoing dev.
- Commit and push.

```bash
git commit -am "Set version to 0.3.0-SNAPSHOT"
git push origin master
```

---

## 6. Quick checklist

- [ ] Version updated in `build.sbt`
- [ ] Commit pushed to `master`
- [ ] Workflow triggered via GitHub Actions
- [ ] Artifacts staged or released successfully
- [ ] Verified on [search.maven.org](https://search.maven.org)

# JaCoCo Method Filter sbt Plugin

sbt plugin for filtering JaCoCo coverage by annotating methods based on configurable rules.

## Table of Contents

- [Requirements](#requirements)
- [Implementation Details](#implementation-details)
- [Installation](#installation)
- [Available Tasks](#available-tasks)
- [Task Details](#task-details)
  - [`jmfInitRules`](#jmfinitrules)
  - [`jmfVerify`](#jmfverify)
  - [`jacocoReport`](#jacocoreport)
  - [`jacocoReportAll`](#jacocoreportall)
  - [`jacocoClean` / `jacocoCleanAll`](#jacococlean--jacococleanall)
- [Settings](#settings)
- [Output Locations](#output-locations)

## Requirements

- Java (the JDK used to run sbt)
- sbt 1.x

## Implementation Details

The plugin executes the coverage flow by:

1. Rewriting compiled class files to mark matched methods as generated
2. Generating JaCoCo HTML, XML, and CSV reports from the filtered classes
3. Providing aggregate helpers (`jacocoReportAll`, `jacocoCleanAll`) for multi-module builds

## Installation

Add to `project/plugins.sbt`:

```scala
addSbtPlugin("io.github.moranaapps" % "jacoco-method-filter-sbt" % "2.0.0")
```

Enable the plugin in your `build.sbt`:

```scala
lazy val myModule = (project in file("my-module"))
  .enablePlugins(JacocoFilterPlugin)
```

Optional: define convenient command aliases (the plugin does not add `jacoco` automatically):

```scala
addCommandAlias("jacoco", "; jacocoOn; clean; test; jacocoReportAll; jacocoOff")
addCommandAlias("jacocoOn", "; set every jacocoPluginEnabled := true")
addCommandAlias("jacocoOff", "; set every jacocoPluginEnabled := false")
```

## Available Tasks

| Task | Description |
|------|-------------|
| `jmfInitRules` | Creates a default `jmf-rules.txt` file in the project root with sensible Scala defaults |
| `jmfVerify` | Scans compiled classes and reports which methods would be excluded/rescued by current rules (read-only) |
| `jacocoReport` | Rewrites classes, runs method filtering, and generates JaCoCo HTML/XML/CSV reports |
| `jacocoReportAll` | Runs `jacocoReport` on all enabled modules under the current aggregate |
| `jacocoClean` | Removes filtered classes and JaCoCo report artifacts |
| `jacocoCleanAll` | Runs `jacocoClean` on all enabled modules under the current aggregate |

## Task Details

### `jmfInitRules`

Creates a `jmf-rules.txt` file from the bundled template with sensible defaults for Scala projects.

```bash
sbt jmfInitRules
```

### `jmfVerify`

Scans compiled classes and reports which methods would be excluded or rescued by your
current rules — without modifying any files.

```bash
sbt jmfVerify
```

Example output:

```text
[verify] EXCLUDED (15 methods):
[verify]   com.example.User
[verify]     #copy(I)Lcom/example/User;    rule-id:case-copy

[verify] RESCUED by include rules (1 method):
[verify]   com.example.Config$
[verify]     #apply(Lcom/example/Config;)Lcom/...;  excl:comp-apply → incl:keep-config-apply

[verify] Summary: 42 classes scanned, 15 methods excluded, 1 method rescued
```

- **Excluded** — matched by an exclusion rule; will be filtered from coverage.
- **Rescued** — matched by an exclusion rule *and* an include rule (`+…`). Because include rules always win, the
 method stays in coverage. The `excl:… → incl:…` trace shows which rules were involved.

### `jacocoReport`

Runs the full coverage pipeline for a single module:

1. Rewrites compiled classes to add `@CoverageGenerated` annotations to matched methods
2. Generates JaCoCo HTML, XML, and CSV reports

```bash
sbt myModule/jacocoReport
```

### `jacocoReportAll`

Runs `jacocoReport` on all modules that have `jacocoPluginEnabled := true` under the current aggregate.

```bash
sbt jacocoReportAll
```

### `jacocoClean` / `jacocoCleanAll`

Removes filtered classes directory and JaCoCo report artifacts.

```bash
sbt jacocoClean       # current module
sbt jacocoCleanAll    # all enabled modules
```

## Settings

**Important:** you do not need to set any of these for the common case.
If you keep a `jmf-rules.txt` file in the build root (or generate it via `jmfInitRules`) and only
toggle `jacocoPluginEnabled`, the defaults are enough.
Only configure the settings below when you want to:

- load rules from a different location (or add a global rules source)
- change output locations
- customize report generation (format, encoding, includes/excludes)
- run in dry-run mode

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `jacocoPluginEnabled` | `Boolean` | `false` | Enable/disable the plugin for this module |
| `jacocoVersion` | `String` | `"0.8.14"` | JaCoCo version to use |
| `jacocoExecFile` | `File` | `target/jacoco/jacoco.exec` | JaCoCo execution data file location |
| `jacocoReportDir` | `File` | `target/jacoco-report` | Output directory for JaCoCo reports |
| `jacocoReportName` | `String` | Derived from module ID and Scala version | Title used for JaCoCo HTML report |
| `jacocoReportFormats` | `Set[String]` | `Set("html", "xml", "csv")` | Report formats to generate (valid: `html`, `xml`, `csv`) |
| `jacocoSourceEncoding` | `String` | `"UTF-8"` | Source file encoding for report generation |
| `jacocoIncludes` | `Seq[String]` | `Seq("**")` | Include patterns for JaCoCo agent (JaCoCo syntax, e.g., `com/example/**`). Used during test execution, not report generation. |
| `jacocoExcludes` | `Seq[String]` | `Seq("scala.*", "java.*", "sun.*", "jdk.*")` | Exclude patterns for JaCoCo agent (JaCoCo syntax). Used during test execution, not report generation. |
| `jacocoAppend` | `Boolean` | `false` | Append to existing .exec file instead of overwriting |
| `jacocoFailOnMissingExec` | `Boolean` | `false` | Fail `jacocoReport` if .exec file is missing (default: warn & skip) |
| `jacocoSetUserDirToBuildRoot` | `Boolean` | `true` | Mimic non-forked runs by setting `-Duser.dir` to the build root for forked tests |
| `jmfGlobalRules` | `Option[String]` | `None` | Global rules source (URL or file path). Loaded when defined. Note: URLs require network access. |
| `jmfLocalRules` | `Option[File]` | `None` | Local rules file. Loaded when defined. |
| `jmfLocalRulesFile` | `File` | `jmf-rules.txt` | Fallback local rules file used only when both `jmfGlobalRules` and `jmfLocalRules` are `None` |
| `jmfDryRun` | `Boolean` | `false` | Dry run mode - logs matches without modifying classes |
| `jmfOutDir` | `File` | `target` | Base output directory; filtered classes are written under `jmfOutDir / "classes-filtered"` |

### Examples

Override the default fallback rules file location:

```scala
// build.sbt
ThisBuild / jmfLocalRulesFile := (ThisBuild / baseDirectory).value / "config" / "jmf-rules.txt"
```

Load both a global rules file (path as a String) and a local rules file:

```scala
// build.sbt
jmfGlobalRules := Some(((ThisBuild / baseDirectory).value / "rules" / "global-rules.txt").getAbsolutePath)
jmfLocalRules  := Some((ThisBuild / baseDirectory).value / "rules" / "local-rules.txt")
```

Load global rules from a URL (requires network access):

```scala
// build.sbt
jmfGlobalRules := Some("https://example.com/jmf-rules.txt")
```

Change output locations:

```scala
// build.sbt
jmfOutDir := target.value / "jmf"
jacocoReportDir := target.value / "coverage"
```

Customize report generation:

```scala
// build.sbt
// Generate only HTML report (skip XML and CSV)
jacocoReportFormats := Set("html")

// Change report title
jacocoReportName := "My Project Coverage Report"

// Use different source encoding
jacocoSourceEncoding := "ISO-8859-1"

// Filter classes during test execution (agent configuration)
// Note: These control which classes are instrumented during tests, NOT which appear in reports
jacocoIncludes := Seq("com/example/core/**", "com/example/api/**")
jacocoExcludes := Seq("com/example/generated/**", "**/*Test*")
```

## Output Locations

After running `jacocoReport`:

- **Filtered classes**: `target/scala-<version>/classes-filtered`
- **HTML report**: `target/scala-<version>/jacoco-report/index.html`
- **XML report**: `target/scala-<version>/jacoco-report/jacoco.xml`
- **CSV report**: `target/scala-<version>/jacoco-report/jacoco.csv`

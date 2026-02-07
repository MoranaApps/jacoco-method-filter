# JaCoCo Method Filter sbt Plugin

sbt plugin for filtering JaCoCo coverage by annotating methods based on configurable rules.

## Table of Contents

- [Requirements](#requirements)
- [Implementation Details](#implementation-details)
- [Installation](#installation)
- [Available Tasks](#available-tasks)
- [Task Details](#task-details)
  - [`jmfInitRules`](#jmfinitrules)
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
addSbtPlugin("io.github.moranaapps" % "jacoco-method-filter-sbt" % "1.2.0")
```

Enable the plugin in your `build.sbt`:

```scala
lazy val myModule = (project in file("my-module"))
  .enablePlugins(JacocoFilterPlugin)`
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

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `jacocoPluginEnabled` | `Boolean` | `false` | Enable/disable the plugin for this module |
| `jmfRulesFile` | `File` | `jmf-rules.txt` | Path to the rules file |
| `jmfDryRun` | `Boolean` | `false` | Dry run mode - logs matches without modifying classes |
| `jmfOutputDirectory` | `File` | `target/jmf/classes-filtered` | Output directory for filtered classes |
| `jacocoReportDirectory` | `File` | `target/jacoco/report` | Output directory for JaCoCo reports |

## Output Locations

After running `jacocoReport`:

- **Filtered classes**: `target/jmf/classes-filtered`
- **HTML report**: `target/jacoco/report/index.html`
- **XML report**: `target/jacoco/report/jacoco.xml`
- **CSV report**: `target/jacoco/report/jacoco.csv`


# jacoco-method-filter

**Scala-based bytecode rewriter for Java/Scala projects** that injects an annotation whose simple name contains
`Generated` into selected methods *before* JaCoCo reporting. Since **JaCoCo ≥ 0.8.2** ignores classes and methods
annotated with an annotation whose simple name contains `Generated` (with retention `CLASS` or `RUNTIME`), this lets
you **filter coverage at the method level** without touching your source code — and keep **HTML and XML numbers
consistent**.

- [Why this exists](#why-this-exists)
- [Goals](#goals)
- [Non-goals](#non-goals)
- [Rules file format](#rules-file-format)
- [Usage — sbt plugin](#usage--sbt-plugin)
- [Usage — Maven](#usage--maven)
- [License](#license)

---

## Why this exists

JaCoCo does not natively support arbitrary **method-level** filtering based on patterns.  
Typical needs include removing **compiler noise** from Scala/Java coverage (e.g., Scala `copy`, `$default$N`,
`$anonfun$*`, or `synthetic/bridge` methods) while keeping **real business logic** visible in coverage metrics.

---

## Goals

- Method-level filtering using a simple **rules file** (globs + flags).
- **No source changes**: the tool annotates bytecode (`.class`) after compile.
- Works locally and in CI with **sbt**, **Maven**, and **GitHub Actions**.
- Supports **Scala and Java** (JVM bytecode).
- Simple flow: `test → rewriter → jacococli report`.

## Non-goals

- Do not modify `jacoco.exec`.
- Do not implement a custom JaCoCo HTML renderer.
- Do not optimize bytecode beyond adding the marker annotation.
- Do not enforce coverage thresholds (leave that to your CI policy).

---

## Rules

The **rules file** tells the rewriter which methods should be marked with  
`@CoverageGenerated` and therefore **excluded from JaCoCo coverage**.

A rule consists of a **class glob**, a **method glob**, an optional **descriptor**,  
and optional **flags/predicates**.

### Quick Syntax

```markdown
<FQCN_glob>#<method_glob>(<descriptor_glob>) [FLAGS and PREDICATES...]
```

- **Class glob** (`FQCN_glob`)  
  Fully qualified class name in dot form. `$` allowed for inner classes.  
  Examples: `com.example.*`, `*.model.*`, `*`
- **Method glob** (`method_glob`)  
  Examples: `copy`, `get*`, `$anonfun$*`, `*_$eq`
- **Descriptor glob** (`(args)ret`)  
  JVM method descriptor. May be omitted (defaults to wildcard).  
  Examples: `(I)I`, `(Ljava/lang/String;)V`, `(*)*`
- **Flags** _(optional)_  
  `public | protected | private | synthetic | bridge | static | abstract`
- **Predicates** _(optional)_
  - `ret:<glob>` → match return type only
  - `id:<string>` → identifier for logs/reports
  - `name-contains:<s>`, `name-starts:<s>`, `name-ends:<s>`

> **Notes**
> 
>- Regex selectors (`re:`) are not supported — **globs only**.
>- **Always use dot-form (**`com.example.Foo`**) for class names**.
>  - Rules written with either dot or slash still match, but all inputs to the matcher must be dot-form.
>- Comments (`# …`) and blank lines are ignored.

### Quick Examples

```text
# Ignore all methods in DTOs
*.dto.*#*(*)

# Ignore Scala case class boilerplate
*.model.*#copy(*)
*.model.*#productArity()
*.model.*#productElement(*)

# Ignore anonymous functions and compiler bridges
*#$anonfun$*
*#*(*):bridge
```

### Ready to Use Rules File

- **Scala project** : the ready to use rules file is at
[jmf_rules.txt](./integration/jmf-rules_for_scala_project.txt).

This file contains both ready-to-use defaults and a detailed syntax guide
to help you adapt the rules to your own project.

---

## Usage — sbt plugin

- Add the plugin to your build:

```scala
// project/plugins.sbt
addSbtPlugin("com.github.sbt" % "sbt-jacoco" % "3.5.0")
addSbtPlugin("MoranaApps" % "jacoco-method-filter-sbt" % "0.1.0-SNAPSHOT")
```

- In your project build:

```scala
enablePlugins(morana.coverage.JacocoFilterPlugin)

// make the tool available at runtime for the plugin to run
libraryDependencies += "MoranaApps" %% "jacoco-method-filter-core" % "0.1.0-SNAPSHOT"

// (optional) overrides
coverageRewriteRules     := baseDirectory.value / "rules" / "coverage-rules.txt"
coverageRewriteOutputDir := target.value / s"scala-${scalaBinaryVersion.value}" / "classes-filtered"
jacocoExec               := target.value / "jacoco.exec"
jacocoCliJar             := baseDirectory.value / "tools" / "jacococli.jar"
```

### Workflow

- **1.** Run tests → `sbt-jacoco` produces `target/jacoco.exec` and unfiltered classes.
- **2.** Rewrite classes according to your rules (adds `@Generated`):

```scala
sbt coverageRewrite
```

- **3.** Generate filtered JaCoCo report:

```scala
sbt coverageReportFiltered
```

- **4.** Or run the full pipeline in one step:

```scala
sbt coverageFiltered
```

#### Output

- Filtered classes: `target/scala-*/classes-filtered`
- HTML report: `target/jacoco-html/`
- XML report: `target/jacoco.xml`

> **Notes**
>
>- Rules file defaults to rules/coverage-rules.txt (relative to project root).
>- You can run in dry mode with:
>
>```scala
>coverageRewriteDryRun := true
>```
>
>- FQCN inputs should be dot-form (com.example.Foo). Rules may use dot or slash globs.

---

## Usage — Maven

To use **jacoco-method-filter** in your project:

1. Add the library as a dependency.
2. Enable the provided Maven profile to rewrite classes and generate filtered JaCoCo reports.
3. Run your build with the profile active.

Full **copy-paste ready** configuration (including the complete `<profile>` block) is documented in  
[Maven Integration](./integration/mvn/profile_integration.md).

Usable commands:

```bash
mvn clean verify -Pcode-coverage                # full pipeline: test → rewrite → report
```

### Outputs

- **Filtered classes** → target/classes-filtered
- **HTML report** → target/jacoco-html/index.html
- **XML report** → target/jacoco.xml

---

## License

Apache License 2.0. See [LICENSE](LICENSE) for details.

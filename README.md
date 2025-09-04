
# jacoco-method-filter

**Scala-based bytecode rewriter for Java/Scala projects** that injects an annotation whose simple name contains
`Generated` into selected methods *before* JaCoCo reporting. Since **JaCoCo ≥ 0.8.2** ignores classes and methods
annotated with an annotation whose simple name contains `Generated` (with retention `CLASS` or `RUNTIME`), this lets
You **filter coverage at the method level** without touching your source code — and keep **HTML and XML numbers
consistent**.

- [Why this exists](#why-this-exists)
- [Goals](#goals)
- [Non-goals](#non-goals)
- [Rules file format](#rules-file-format)
- [Integration](#integration)
  - [With sbt plugin](#with-sbt-plugin)
  - [With Maven](#with-maven)
  - [Customization](#customization)
- [License](#license)

---

## Why this exists

JaCoCo does not natively support arbitrary **method-level** filtering based on patterns.  
Typical needs include removing **compiler noise** from Scala/Java coverage (e.g., Scala `copy`, `$default$N`,
`$anonfun$*`, or `synthetic/bridge` methods) while keeping **real business logic** visible in coverage metrics.

---

## Goals

- Method-level filtering using a simple **rules file** (globs + flags).
- **No source changes**: the tool annotates bytecode (`.class`) after compilation.
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
>- **Always use dot-form (**`com.example.Foo`**) for class names**. Rules written with either dot or slash still
>match, but all inputs to the matcher must be dot-form.
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

## Integration

The integration snippets follow the partially independent versioning dependency on source code:

- **minor** change means change in source code (released to Maven Central) and is applied to all integrations
- **patch** change means change in integration only (released in GItHub repo)
  - the source not change and compatible

### With sbt plugin

- Paste auto plugin sbt files into your `{root}/project` directory:
  - [JacocoBaseKeysPlugin.scala](./integration/sbt/JacocoBaseKeysPlugin.scala)
  - [FilteredJacocoAgentPlugin.scala](./integration/sbt/FilteredJacocoAgentPlugin.scala)

- Paste the default rules config to the project root directory:
  - [jmf-rules for Scala projects](./integration/jmf-rules_for_scala_project.txt)

- In your root `build.sbt`, enable plugin for each module where required:

```scala
.enablePlugins(FilteredJacocoAgentPlugin)
```

#### Register Aliases

##### In `build.sbt`

```scala
// Run activate jacoco + clean + test + per-module reports across the whole build + deactivate jacoco
addCommandAlias("jacoco", "; jacocoOn; clean; test; jacocoReportAll; jacocoOff")
addCommandAlias("jacocoOff",  "; set every jacocoPluginEnabled := false")
addCommandAlias("jacocoOn",   "; set every jacocoPluginEnabled := true")
```

##### In `.sbtrc`

```scala
# Jacoco Aliases
alias jacoco=; jacocoOn; +clean; +test; jacocoReportAll; jacocoOff
alias jacocoOff=; set every jacocoPluginEnabled := false
alias jacocoOn=; set every jacocoPluginEnabled := true
```

#### Run

```bash
sbt jacoco
```

If you need to run module in isolation. (Different java can be required)

```bash
sbt "project xzy" jacoco
```

##### Output

- Filtered classes: `target/scala-*/classes-filtered`
- HTML report: `target/jacoco-html/`
- XML report: `target/jacoco.xml`

> **Notes**
>
>- You can run in dry mode. See the provided plugin code and change:
>
>```scala
>jmfDryRun   := false      # see in `override def projectSettings: Seq[Setting[_]]`
>```
>
>- FQCN inputs should be dot-form (com.example.Foo).

---

### With Maven

To use **jacoco-method-filter** in your project:

1. Add the library as a dependency.
2. Enable the provided Maven profile to rewrite classes and generate filtered JaCoCo reports.
3. Run your build with the profile active.

Full **copy-paste ready** configuration (including the complete `<profile>` block) is documented in  
[Maven Integration](./integration/mvn/profile_integration.md).

Full **copy-paste ready** default rules config to the project root directory:

- [jmf-rules for Scala projects](./integration/jmf-rules_for_scala_project.txt)

Usable commands:

```bash
mvn clean verify -Pcode-coverage                # full pipeline: test → rewrite → report
```

#### Outputs

- **Filtered classes** → target/classes-filtered
- **HTML report** → target/jacoco-html/index.html
- **XML report** → target/jacoco.xml

### Customization

#### No Tests in Module

If your module does not contain tests, JaCoCo will not generate the report.exec file.
In this situation, the generation of the JaCoCo report will be skipped.

---

## License

Apache License 2.0. See [LICENSE](LICENSE) for details.

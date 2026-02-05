
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
- Cross-built for **Scala 2.11**, **2.12**, and **2.13**.
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

### With sbt plugin

**Recommended approach:** Use the published sbt plugin from Maven Central.

> **Quick Start**: See the [minimal working example](./examples/sbt-basic) for a complete setup.

#### 1. Add the plugin to `project/plugins.sbt`

```scala
addSbtPlugin("io.github.moranaapps" % "jacoco-method-filter-sbt" % "1.2.0")
```

#### 2. Add the default rules file to your project root

**Option A (Recommended):** Run the bootstrap task:
```bash
sbt jmfInitRules
```

This creates a `jmf-rules.txt` file with sensible defaults for Scala projects.

**Option B (Manual):** Download [jmf-rules.template.txt](./jmf-rules.template.txt) and place it in your project root directory as `jmf-rules.txt`.

#### 3. Enable the plugin in `build.sbt`

Enable the plugin for each module where coverage filtering is required:

```scala
lazy val myModule = (project in file("my-module"))
  .enablePlugins(JacocoFilterPlugin)
  .settings(
    // your other settings
  )
```

#### 4. Configure Coverage Control (Optional)

In your root `build.sbt`, add command aliases to control coverage across all modules:

```scala
// Run activate jacoco + clean + test + per-module reports across the whole build + deactivate jacoco
addCommandAlias("jacoco", "; jacocoOn; clean; test; jacocoReportAll; jacocoOff")
addCommandAlias("jacocoOff", "; set every jacocoPluginEnabled := false")
addCommandAlias("jacocoOn", "; set every jacocoPluginEnabled := true")
```

Or add to `.sbtrc` for persistent aliases:

```text
# Jacoco Aliases
alias jacoco=; jacocoOn; +clean; +test; jacocoReportAll; jacocoOff
alias jacocoOff=; set every jacocoPluginEnabled := false
alias jacocoOn=; set every jacocoPluginEnabled := true
```

#### 5. Run Coverage

```bash
sbt jacoco
```

For a single module in isolation:

```bash
sbt "project myModule" jacoco
```

#### 6. View Reports

After running coverage, reports are generated in each module's target directory:

- **Filtered classes**: `target/jmf/classes-filtered`
- **HTML report**: `target/jacoco/report/index.html`
- **XML report**: `target/jacoco/report/jacoco.xml`
- **CSV report**: `target/jacoco/report/jacoco.csv`

> **Configuration Notes**
>
> - The plugin is disabled by default. Enable it per-module with `.enablePlugins(JacocoFilterPlugin)` or globally with
 `set every jacocoPluginEnabled := true`.
> - FQCN inputs in rules files should use dot-form (e.g., `com.example.Foo`).
> - To run in dry mode (preview what would be filtered), set `jmfDryRun := true` in your build.

---

#### Legacy/Manual Integration (Advanced Users Only)

For users who need fine-grained control or are working with custom build setups, you can manually copy the plugin
 source files.

**See [integration/sbt/README.md](./integration/sbt/README.md) for detailed instructions and important maintenance
warnings.**

Quick summary:

1. Copy these files into your `{root}/project` directory:
   - [JacocoBaseKeysPlugin.scala](./integration/sbt/JacocoBaseKeysPlugin.scala)
   - [FilteredJacocoAgentPlugin.scala](./integration/sbt/FilteredJacocoAgentPlugin.scala)

2. Add the default rules file to your project root:
   - [jmf-rules for Scala projects](./integration/jmf-rules_for_scala_project.txt)

3. Enable the plugin in `build.sbt`:

   ```scala
   .enablePlugins(FilteredJacocoAgentPlugin)
   ```

**⚠️ Warning:** This approach is harder to maintain and keep in sync with releases. The published plugin
 (described above) is strongly recommended for most users.

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

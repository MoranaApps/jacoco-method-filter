
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
[jmf-rules.template.txt](./jmf-rules.template.txt).

This file contains both ready-to-use defaults and a detailed syntax guide
to help you adapt the rules to your own project.

---

## Integration

### With sbt plugin

Example usage:

```bash
sbt jmfInitRules
sbt jacocoReport
```

Or define a `jacoco` alias in your `build.sbt`:

```scala
addCommandAlias("jacoco", "; jacocoOn; clean; test; jacocoReportAll; jacocoOff")
```

See [`sbt-plugin/README.md`](./sbt-plugin/README.md) for installation, available tasks, and settings.
Examples: [`examples/sbt-basic/`](./examples/sbt-basic/)

---

### With Maven

Example usage:

```bash
mvn jacoco-method-filter:init-rules
mvn clean verify -Pcode-coverage
```

See [`maven-plugin/README.md`](./maven-plugin/README.md) for installation, available goals,
and parameters.
Examples: [`examples/maven-basic/`](./examples/maven-basic/) (Java), [`examples/maven-scala/`](./examples/maven-scala/) (Scala)

---

### Output Locations

Both sbt and Maven integrations produce coverage artifacts in a standardized layout under your project's `target/` directory:

- **Filtered classes**: `target/classes-filtered` — Compiled classes with `@CoverageGenerated` annotations applied
- **JaCoCo HTML report**: `target/jacoco-report/index.html` — Interactive HTML coverage report
- **JaCoCo XML report**: `target/jacoco.xml` — Machine-readable coverage data (for CI/CD)
- **JaCoCo CSV report**: `target/jacoco-report/jacoco.csv` — Coverage data in CSV format (sbt only)

These paths are configurable. See plugin documentation for customization options.

---

### Customization

See plugin documentation for rules file location, dry-run mode, and behavior when JaCoCo execution data is missing:

- [`sbt-plugin/README.md`](./sbt-plugin/README.md)
- [`maven-plugin/README.md`](./maven-plugin/README.md)

---

## License

Apache License 2.0. See [LICENSE](LICENSE) for details.

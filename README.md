
# jacoco-method-filter

**Scala-based bytecode rewriter for Java/Scala projects** that injects an annotation whose simple name contains
`Generated` into selected methods *before* JaCoCo reporting. Since **JaCoCo ≥ 0.8.2** ignores classes and methods
annotated with an annotation whose simple name contains `Generated` (with retention `CLASS` or `RUNTIME`), this lets
you **filter coverage at the method level** without touching your source code — and keep **HTML and XML numbers
consistent**.

Minimum tested JaCoCo version: **0.8.7** (JaCoCo must be **≥ 0.8.2** to ignore `@Generated`).

- [Why this exists](#why-this-exists)
- [Goals](#goals)
- [Non-goals](#non-goals)
- [Quick Start](#quick-start)
- [Rules](#rules)
- [Integration](#integration)
  - [With sbt plugin](#with-sbt-plugin)
  - [With Maven](#with-maven)
  - [Output Locations](#output-locations)
  - [Customization](#customization)
- [Changelog](CHANGELOG.md)
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
- Cross-built for **Scala 2.11 / 2.12 / 2.13** (fat JAR with shaded ASM; runtime deps: `scala-library`, `scopt`).
- Supports **Scala and Java** (JVM bytecode).
- Simple flow: `test → rewriter → jacococli report`.

## Non-goals

- Do not modify `jacoco.exec`.
- Do not implement a custom JaCoCo HTML renderer.
- Do not optimize bytecode beyond adding the marker annotation.
- Do not enforce coverage thresholds (leave that to your CI policy).

---

## Quick Start

```text
# Ignore all methods in DTOs
*.dto.*#*(*)

# Ignore Scala case class boilerplate
*.model.*#copy(*)
*.model.*#productArity()
*.model.*#productElement(*)

# Ignore anonymous functions and compiler bridges
*#$anonfun$*
*#*(*) bridge

# Keep (rescue) specific methods from broad exclusions
+com.example.Config$#apply(*)  id:keep-config-apply
```

---

## Rules

See **[docs/rules-reference.md](docs/rules-reference.md)** for:

- Rule syntax and anatomy
- JVM descriptor type mapping and normalization
- Common pitfalls and Scala name mangling
- Full examples (DTOs, case classes, lambdas, setters, return-type constraints)
- Exclude and include (whitelist) rules
- Global vs. local rules and how they merge
- Verify mode — preview what gets filtered before committing
- Diagnostic and verification workflows
- Global rule safety warnings
- CLI reference

**Ready-to-use rules template:**
- sbt: [`jmf-rules.template.txt`](./jmf-rules.template.txt)
- Maven: [`maven-plugin/src/main/resources/jmf-rules.template.txt`](./maven-plugin/src/main/resources/jmf-rules.template.txt)

---

## Integration

### With sbt plugin

Use this when your build runs on sbt.

```bash
sbt jmfInitRules
sbt jacocoReport
```

Start here for details:

- [`sbt-plugin/README.md`](./sbt-plugin/README.md) — installation, tasks, and settings
- [`examples/sbt-basic/`](./examples/sbt-basic/) — minimal working sbt project

---

### With Maven

Use this when your build runs on Maven.

```bash
mvn jacoco-method-filter:init-rules
mvn clean verify -Pcode-coverage
```

Start here for details:

- [`maven-plugin/README.md`](./maven-plugin/README.md) — installation, goals, and parameters
- [`examples/maven-basic/`](./examples/maven-basic/) (Java)
- [`examples/maven-scala/`](./examples/maven-scala/) (Scala)

---

### Output Locations

Outputs are under `target/`, but exact paths differ by integration:

- **sbt plugin**: `target/scala-<version>/classes-filtered` and `target/scala-<version>/jacoco-report/`
- **Maven plugin**: `target/classes-filtered`, `target/jacoco-report/`, and `target/jacoco.xml`

See plugin READMEs for full defaults and customization options.

---

### Customization

See plugin documentation for rules file location, dry-run mode, and behavior when JaCoCo execution data is missing:

- [`sbt-plugin/README.md`](./sbt-plugin/README.md)
- [`maven-plugin/README.md`](./maven-plugin/README.md)

---

## License

Apache License 2.0. See [LICENSE](LICENSE) for details.

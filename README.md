
# jacoco-method-filter

**Scala-based bytecode rewriter for Java/Scala projects** that injects an annotation whose simple name contains
`Generated` into selected methods *before* JaCoCo reporting. Since **JaCoCo ≥ 0.8.2** ignores classes and methods
annotated with an annotation whose simple name contains `Generated` (with retention `CLASS` or `RUNTIME`), this lets
You **filter coverage at the method level** without touching your source code — and keep **HTML and XML numbers
consistent**.

Minimum tested JaCoCo version: **0.8.7** (JaCoCo must be **≥ 0.8.2** to ignore `@Generated`).

- [Why this exists](#why-this-exists)
- [Goals](#goals)
- [Non-goals](#non-goals)
- [Rules](#rules)
  - [Quick Syntax](#quick-syntax)
  - [Quick Examples](#quick-examples)
  - [Exclude and Include](#exclude-and-include)
  - [Global and Local Rules](#global-and-local-rules)
  - [How rules are merged](#how-rules-are-merged)
  - [Verify: Preview What Gets Filtered](#verify-preview-what-gets-filtered)
  - [Ready to Use Rules File](#ready-to-use-rules-file)
- [Integration](#integration)
  - [With sbt plugin](#with-sbt-plugin)
  - [With Maven](#with-maven)
  - [Output Locations](#output-locations)
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
- Built for **Scala 2.12** (fat JAR with shaded ASM; runtime deps: `scala-library`, `scopt`).
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
*#*(*) bridge

# Ignore synthetic methods and all void-returning setters
*#*(*) synthetic
*#set*(*) ret:V

# Keep (rescue) specific methods from broad exclusions
+com.example.Config$#apply(*)  id:keep-config-apply
```

### Exclude and Include

By default, all rules are **exclusion rules** — they mark methods to be filtered from coverage.

**Include rules** (whitelist) can override exclusions for specific methods. Prefix a rule with `+` to mark it as an inclusion:

```text
# Exclude all companion object apply methods
*$#apply(*)  id:comp-apply

# But keep this one — it has custom business logic
+com.example.Config$#apply(*)  id:keep-config-apply
```

**Resolution logic:**

- A method is **excluded** if any exclusion rule matches AND no inclusion rule matches
- A method is **rescued** (kept in coverage) if both exclusion and inclusion rules match — **include always wins**
- A method is **unaffected** if no exclusion rule matches

### Global and Local Rules

Most users can start with a **single local rules file**.

- **Simple (single file)**: use `--rules jmf-rules.txt` (CLI) or the plugin default `jmf-rules.txt`
- **Advanced (two-layer)**: use **global** rules (shared defaults) + **local** rules (project overrides)

#### Rule sources

- **Global** rules can be a **local path or an HTTP/HTTPS URL**
- **Local** rules are a **local file path**
- The legacy `--rules` flag is also a **local file path**

You can separate organization-wide shared rules from project-specific rules:

| Type | Purpose | Source |
|------|---------|--------|
| **Global** | Org-wide defaults (e.g., always ignore Scala boilerplate) | Path or URL |
| **Local** | Project-specific overrides and additions | Local file |

**Configuration examples:**

**sbt:**

```scala
jmfGlobalRules := Some("https://myorg.com/scala-defaults.txt")
jmfLocalRules := Some(baseDirectory.value / "jmf-local-rules.txt")
```

**Maven:**

```xml
<configuration>
  <globalRules>https://myorg.com/scala-defaults.txt</globalRules>
  <localRules>${project.basedir}/jmf-local-rules.txt</localRules>
</configuration>
```

**CLI:**

```bash
java -cp ... io.moranaapps.jacocomethodfilter.CoverageRewriter \
  --in target/classes \
  --out target/classes-filtered \
  --global-rules https://myorg.com/scala-defaults.txt \
  --local-rules jmf-local-rules.txt
```

**Backward compatible CLI (single file):**

```bash
java -cp ... io.moranaapps.jacocomethodfilter.CoverageRewriter \
  --in target/classes \
  --out target/classes-filtered \
  --rules jmf-rules.txt
```

### How rules are merged

When using global and local rules:

1. **Global rules** are loaded first (from URL or path)
2. **Local rules** are appended
3. If the legacy `--rules` file is also provided, it is appended last
4. During evaluation, **any include rule overrides any exclude rule** for the same method

This lets you:

- Define broad exclusions globally (e.g., `*#copy(*)`)
- Override selectively in local rules (e.g., `+com.example.Config$#copy(*)`)

### Verify: Preview What Gets Filtered

`verify` runs against the **compiled class files** (bytecode) in the given `--in` directory (e.g. `target/classes`),
not against raw source code — so it only reports exclusions/rescues for methods that **actually exist after
compilation**.

> **Important**: Because `verify` scans bytecode, it sees all methods the JVM compiler generated
> (synthetic bridges, anonymous function bodies, default parameter accessors, etc.) alongside your
> hand-written code. Some broad exclusion rules may accidentally match methods you wrote yourself
> (e.g., `apply`). Use include rules (`+...`) to rescue those methods — see below.

Before running coverage, use the **verify** command to preview which methods will be excluded vs. rescued:

**sbt:**

```bash
sbt jmfVerify
```

**Maven:**

```bash
mvn jacoco-method-filter:verify
```

**CLI:**

```bash
java -cp ... io.moranaapps.jacocomethodfilter.CoverageRewriter \
  --verify \
  --in target/classes \
  --rules jmf-rules.txt
```

**Example output:**

```text
[verify] EXCLUDED (15 methods):
[verify]   com.example.User
[verify]     #copy(I)Lcom/example/User;    rule-id:case-copy
[verify]     #apply(...)...                rule-id:comp-apply

[verify] RESCUED by include rules (1 method):
[verify]   com.example.Config$
[verify]     #apply(Lcom/example/Config;)Lcom/...;  excl:comp-apply → incl:keep-config-apply

[verify] Summary: 42 classes scanned, 15 methods excluded, 1 method rescued
```

#### Suggest Include Rules

When using broad exclusion rules, you might accidentally exclude hand-written methods.
Use `--verify-suggest-includes` to get heuristic suggestions for include (rescue) rules:

**CLI:**
```bash
java -cp ... io.moranaapps.jacocomethodfilter.CoverageRewriter \
  --verify \
  --verify-suggest-includes \
  --in target/classes \
  --rules jmf-rules.txt
```

**Example additional output:**
```text
[verify] Suggested include rules (heuristic — review before use):
[verify]   +com.example.Foo#apply(*)
[verify]   +com.example.Config#process(*)

[verify] NOTE: These suggestions are best-effort heuristics based on bytecode analysis.
[verify]       "Human vs generated" cannot be determined perfectly from bytecode.
```

> **Limitations**: The heuristic classifies a method as "likely human" if it is **not**
> synthetic/bridge and its name doesn't match common compiler-generated patterns
> (e.g., `$anonfun$*`, `lambda$*`, `access$*`). Scala macros, compiler plugins,
> and stripped debug info can fool the heuristic. Always review suggestions before adding them.

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

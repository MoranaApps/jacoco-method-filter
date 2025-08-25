
# jacoco-method-filter

**Scala-based bytecode rewriter for Java/Scala projects** that injects an annotation whose simple name contains `Generated` into selected methods *before* JaCoCo reporting.  
Since **JaCoCo ≥ 0.8.2** ignores classes and methods annotated with an annotation whose simple name contains `Generated` (with retention `CLASS` or `RUNTIME`), this lets you **filter coverage at the method level** without touching your source code — and keep **HTML and XML numbers consistent**.

- [Why this exists](#why-this-exists)
- [Goals](#goals)
- [Non-goals](#non-goals)
- [Installation & build](#installation--build)
- [Rules file format](#rules-file-format)
- [Usage — CLI (sbt project)](#usage---cli-sbt-project)
- [Usage — Maven (recipe)](#usage---maven-recipe)
- [CI — GitHub Actions (minimal)](#ci---github-actions-minimal)
- [Safety & troubleshooting](#safety--troubleshooting)
- [Roadmap](#roadmap)
- [License](#license)

---

## Why this exists

JaCoCo does not natively support arbitrary **method-level** filtering based on patterns.  
Typical needs include removing **compiler noise** from Scala/Java coverage (e.g., Scala `copy`, `$default$N`, `$anonfun$*`, or `synthetic/bridge` methods) while keeping **real business logic** visible in coverage metrics.

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

## Installation & build

Requirements: JDK 17+ and sbt.

```bash
sbt +compile
```

---

## Rules file format

A rules file defines **method-filtering rules**, one per line.
Each rule tells the rewriter _which methods should be annotated as `*Generated` and therefore ignored by JaCoCo._

### General Syntax

```
<FQCN_glob>#<method_glob>(<descriptor_glob>) [FLAGS and PREDICATES...]
```

- `FQCN_glob` – fully qualified class name, **glob in dot form (`.`)**. `$` allowed for inner classes.
  - Examples:
    - `*.model.*`, `com.example.*`, `*`
- `method_glob` – method name glob
  - Examples:
    - `copy`
    - `$anonfun$*`
    - `get*`
    - `*_$eq`
- `descriptor_glob` – JVM method descriptor in `(args)ret`.
    - you may omit it entirely.
      - `x.A#m2` ⇒ treated as `x.A#m2(*)*` (wildcard args & return).
    - If provided, short/empty forms normalize as:
      - `""`, `"()"`, `"(*)"` ⇒ all become `"(*)*"` (match any args & return).
      - Examples:
        - `(I)I` → takes int, returns int
        - `(Ljava/lang/String;)V` → takes String, returns void
        - `()` or `(*)` or omitted → any args, any return
- `FLAGS` _(optional)_ – space or comma separated access modifiers.
  - Supported: `public | protected | private | synthetic | bridge | static | abstract`.
- **Predicates** (optional) – fine-grained constraints:
  - `ret:<glob>` → match return type only (e.g. `ret:V`, `ret:I`, `ret:Lcom/example/*;`).
  - `id:<string>` → identifier for logs/reports.
  - `name-contains:<s>` → method name must contain `<s>`.
  - `name-starts:<s>` → method name must start with `<s>`.
  - `name-ends:<s>` → method name must end with `<s>`.

### Examples

```text
# Simple wildcards
*#*(*)
*.dto.*#*(*)

# Scala case class helpers
*.model.*#copy(*)
*.model.*#productArity()
*.model.*#productElement(*)
*.model.*#productPrefix()

# Companion objects and defaults
*.model.*$*#apply(*)
*.model.*$*#unapply(*)
*#*$default$*(*)

# Anonymous/synthetic methods
*#$anonfun$*
*#*(*):synthetic            # any synthetic
*#*(*):bridge               # any bridge

# Setters / fluent APIs
*.dto.*#*_$eq(*)
*.builder.*#with*(*)
*.client.*#with*(*) ret:Lcom/api/client/*

# Return-type constraints
*.jobs.*#*(*):ret:V
*.math.*#*(*):ret:I
*.model.*#*(*):ret:Lcom/example/model/*
```

#### Notes

- Regex selectors (`re:`) are not supported — **globs only**.
- **Always use dot-form (**`com.example.Foo`**) for class names**.
  - Rules written with either dot or slash still match, but all inputs to the matcher must be dot-form.
- Comments (`# …`) and blank lines are ignored.

---

## Usage — CLI (sbt project)

1) Build your project and run tests to produce `jacoco.exec` and compiled classes.

2) Run the rewriter:

```bash
# after you have compiled classes
./scripts/dev-run.sh rules/coverage-rules.sample.txt   target/scala-2.13/classes   target/scala-2.13/classes-filtered   "--dry-run"  # remove this flag to actually annotate
```

3) Generate JaCoCo report via `jacococli` using **filtered classes**:

```bash
java -jar jacococli.jar report target/jacoco.exec   --classfiles target/scala-2.13/classes-filtered   --sourcefiles src/main/scala   --html target/jacoco-html   --xml  target/jacoco.xml
```

> Tip: Download `jacococli.jar` from Maven Central (artifact `org.jacoco:org.jacoco.cli`).

---

## Usage — Maven (recipe)

If you prefer Maven, you can call the CLI via `exec-maven-plugin` **after tests** and then run `jacococli` for the report. (A thin Maven plugin can be added later.)

---

## CI — GitHub Actions (minimal)

```yaml
name: coverage-filtered
on: [push, pull_request]
jobs:
  run:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - uses: coursier/cache-action@v6
      - run: sbt "+compile"
      # In your real project:
      # - run: sbt "test"
      # - run: java -jar jacoco-method-filter.jar --in ... --out ... --rules ...
      # - run: java -jar jacococli.jar report ...
```

---

## Safety & troubleshooting

- **HTML didn’t change?** Ensure `jacococli report` uses `--classfiles` pointing at your `classes-filtered` directory.
- **Don’t filter too broadly.** Start with `$default$N`, `synthetic|bridge`, and `$anonfun$*` in specific packages.
- **Dry-run first.** Verify matched methods before annotating.

---

## License

Apache License 2.0. See [LICENSE](LICENSE) for details.

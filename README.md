
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

One rule per line:

```
<FQCN_glob>#<method_glob>(<descriptor_glob>) [FLAGS]
```

- `FQCN_glob`: fully qualified class name glob (dots or slashes, `$` allowed).
- `method_glob`: method name glob (e.g., `copy`, `apply`, `$anonfun$*`).
- `descriptor_glob`: JVM method descriptor glob (use `(*)` if you don’t care).
- `FLAGS`: optional space- or comma-separated flags from:
    - `public | protected | private | synthetic | bridge`

**Example (Scala-focused):**
```
com.example.model.*#copy(*)
com.example.model.*#productArity()
com.example.model.*#productElement(*)
com.example.model.*#productPrefix()
com.example.model.*$*#apply(*)
com.example.model.*$*#unapply(*)
com.example.*#*$default$*(*)
com.example.dto.*#*_$eq(*)
com.example.service.*#$anonfun$*
com.example.*#*(*):synthetic
```

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

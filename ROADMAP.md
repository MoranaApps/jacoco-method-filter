# Roadmap — jacoco-method-filter

A living roadmap for building, hardening, and shipping a practical method-level coverage filter for JVM (Scala/Java) projects using JaCoCo’s `*Generated`-annotation behavior.

> Status: MVP CLI works (dry-run & write), first rule matched. Next: stabilize rules, generate filtered reports, and package for easy reuse.

---

## 0) High-level goals & non-goals

### Goals
- Robust **method-level filtering** via a simple rules file (globs + flags).
- **No source changes** in measured projects — modify bytecode only.
- Works locally and in CI with **sbt**, **Maven**, **GitHub Actions**.
- Supports **Scala and Java** (JVM bytecode).
- Deterministic, auditable runs (optional CSV/JSON of excluded methods).
- Clear **recipes** for common Scala noise (case classes, default args, anonfun, synthetic/bridge).

### Non-goals
- No editing of `jacoco.exec`.
- No custom HTML renderer for JaCoCo.
- No hard-coded coverage policies/thresholds (leave to CI tools).
- No bytecode “optimizations” beyond adding the marker annotation.

---

## 1) Near-term checklist (ship a usable alpha)

- [ ] **Rules parser** quality-of-life:
    - [ ] Relax validation to allow full descriptors that don’t end with `)`.
    - [ ] Normalize `(*)` → `(*)*` (wildcard return type) for convenience.
    - [ ] Accept both **space** and **colon** forms for flags (e.g. `... #*(*) synthetic` and `...#*(*):synthetic`).
    - [ ] Better error messages (line number, example of valid syntax).

- [ ] **CSV/JSON export** of matches:
    - CLI flag: `--report <file.csv|file.json>`
    - Columns: `fqcn`, `method`, `descriptor`, `accessFlags`, `matchedRule`.

- [ ] **Examples**:
    - [ ] `examples/scala-sample` (small project producing `jacoco.exec`) with typical Scala patterns.
    - [ ] Script to run: `test → filter → jacococli report` and open HTML.

- [ ] **Docs**:
    - [ ] README: add “recipes” with copy-paste rules for Scala.
    - [ ] Troubleshooting (descriptors, flags, source paths).
    - [ ] How to verify injected annotation with `javap`.

---

## 2) sbt plugin (developer ergonomics)

**Module**: `sbt-plugin/`  
**Tasks**
- `coverageRewrite`: wraps CLI
    - configurable keys:
        - `coverageRewriteInputDir` (default: `(Compile / classDirectory)`)
        - `coverageRewriteOutputDir` (default: `target/scala-*/classes-filtered`)
        - `coverageRewriteRules` (default: `rules/coverage-rules.txt`)
        - `coverageRewriteDryRun` (bool)
- `coverageReportFiltered`: runs `jacococli` with the filtered classes
    - keys: `jacocoExec`, `jacocoCliJar`, `sourceRoots`, `htmlOut`, `xmlOut`
- Command alias `coverageFiltered` → `test; coverageRewrite; coverageReportFiltered`

**Milestones**
- [ ] Implement tasks & keys.
- [ ] Integration test against `examples/scala-sample`.
- [ ] Document minimal usage in README.

---

## 3) Maven integration

**Short-term (recipe)**: documented `exec-maven-plugin` + `jacococli` calls.  
**Mid-term (plugin)**: thin `maven-plugin/` module with a goal `coverage-rewrite` and a goal `coverage-report-filtered`.

**Milestones**
- [ ] Provide `pom.xml` snippets (recipe).
- [ ] Optional: implement a simple Maven plugin wrapper.
- [ ] IT against a tiny Maven sample project.

---

## 4) Rules “recipes” for Scala/Java

### Scala
- **Case class**:
    - `*#copy(*)*`
    - `*#productArity()`
    - `*#productElement(*)*`
    - `*#productPrefix()`
- **Companion**:
    - `*$*#apply(*)*` (restrict by package!)
    - `*$*#unapply(*)*`
- **Default argument helpers**:
    - `*#*$default$*(* )*`
- **Anon functions**:
    - `*#$anonfun$*(*)*`
- **Setter**:
    - `*#*_$eq(*)*`
- **Flags**:
    - append `synthetic` and/or `bridge` where appropriate

### Java (typical noise)
- **Lambdas** in synthetic holders (flags: `synthetic`, `bridge`).
- **Generated DTOs** (if they carry a `*Generated` annotation already, rewriter is a no-op).

> Always **scope by package** to avoid filtering real logic.

---

## 5) Testing plan

### Unit
- Rules parsing: globs, flags, colon/space separators, descriptor normalization.
- Matching logic for `public/protected/private/synthetic/bridge` flags.

### Bytecode (integration)
- Load a compiled test `.class` set → run rewriter in-memory → assert the presence of `Lio/moranaapps/jacocomethodfilter/CoverageGenerated;` on matched methods.
- Edge cases: pre-existing `*Generated` annotation should not duplicate.

### End-to-end
- `examples/scala-sample`:
    - run tests to produce `jacoco.exec`,
    - run rewriter (writes `classes-filtered`),
    - run `jacococli report`,
    - assert at least one expected method is excluded (by parsing XML or grepping HTML).

---

## 6) CI (GitHub Actions)

- Matrix: `jdk: [17, 21]`, Scala `[2.12, 2.13, 3.3]` (build-only for now).
- Jobs:
    - **Build & unit tests** for `rewriter-core`.
    - **E2E** job: run sample project, produce and upload `jacoco-html` report artifact.
- Optional caching: `coursier/cache-action` to speed up sbt.

---

## 7) Performance & robustness

- [ ] Streamed I/O; avoid unnecessary writes (write only when matches found).
- [ ] Optional **parallelism** (configurable, per-class-file processing with a bounded pool).
- [ ] Graceful behavior when:
    - rules file is missing/empty (no-op, warn),
    - input dir is empty,
    - output dir is same as input (reject for safety unless `--in-place` flag is explicitly set).

---

## 8) Developer experience (DX)

- [ ] Better CLI help (`--help`) with examples and rules syntax.
- [ ] `--dry-run-exit-code`: non-zero exit when **no rules matched** (optional), or when **any rule invalid**.
- [ ] `--explain <FQCN#method(desc)>`: show which rule(s) would match.
- [ ] `rules validate` command: fast syntax & overlap checks.

---

## 9) Security & safety

- Rewriter only **adds an annotation** — no code transforms.
- Do not permit input = output path by default.
- Keep annotation retention at `CLASS` (not `RUNTIME`) to avoid runtime impacts.

---

## 10) Packaging & publishing

- Add `sbt-assembly` to ship a **fat-jar** for the CLI.
- Optional: publish to **GitHub Packages**; later to **Maven Central**.
    - GroupId suggestion: `io.moranaapps`
    - Artifacts:
        - `jacoco-method-filter-core`
        - `jacoco-method-filter-sbt`
        - optional: `jacoco-method-filter-maven-plugin`

---

## 11) Governance & contributions

- Add `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, Issue/PR templates.
- Labels: `good-first-issue`, `help-wanted`, `area:rules`, `area:cli`, `area:plugin`.
- Changelog: `CHANGELOG.md` with SemVer (`0.y` for now).

---

## 12) Future ideas

- **Regex rules** (optional) alongside globs for advanced cases.
- **Annotation mapping**: add a custom parameter on the injected annotation with `ruleId` for traceability (still retention `CLASS`).
- **Exclude-by-annotation present**: support `annot:<FQCN>` in rules to only filter when another annotation is present (e.g., generated code).
- **IDE integration**: IntelliJ external tool config that runs `coverageFiltered` and opens `jacoco-html`.
- **Rule packs**: publish a curated rules file for Scala 2.12/2.13/3.3 language patterns.
- **Config file**: support optional HOCON/JSON config to declare multiple `in/out/rules` “jobs” in one run.
- **`--fail-on-unmatched` flag** for strict pipelines (every rule must match at least one method).

---

## 13) Milestones (suggested timeline)

- **M1 (Alpha)**: parser QoL + CSV report + example project + docs.
- **M2 (sbt plugin)**: tasks, docs, E2E in CI.
- **M3 (Maven recipe/plugin)**: docs or plugin, E2E sample.
- **M4 (Performance & polish)**: parallelism, safety guards, better help.
- **M5 (Release)**: fat-jar, GitHub Release, optional publishing.

---

## 14) Command snippets (grab & use)

- Dry-run (safe):  
  `sbt 'rewriterCore/run --in <classes> --out <classes-filtered> --rules rules/coverage-rules.txt --dry-run'`

- Annotate/write:  
  `sbt 'rewriterCore/run --in <classes> --out <classes-filtered> --rules rules/coverage-rules.txt'`

- Report (HTML+XML):  
  `java -jar jacococli.jar report <jacoco.exec> --classfiles <classes-filtered> --html <out-html> --xml <out-xml> [--sourcefiles <src> ...]`

---

_Keep this file in the repo root as `ROADMAP.md` and update it as you iterate._

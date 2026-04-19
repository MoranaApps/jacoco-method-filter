# Adoption Feedback — Observations Report

This is a living document tracking all observations from the first real-world adoption of
`jacoco-method-filter` (JMF) in the [balta](https://github.com/AbsaOSS/balta) project.

**Source material:** `adoption_feedback/JMF-NOTES.md` (14 observations, numbered 1-13 with 10a-10n)

---

## Classification Legend

| Code | Meaning |
|------|---------|
| **Code bug** | Defect in JMF source code |
| **Doc bug** | Incorrect or misleading documentation in jmf-rules.template.txt |
| **Missing feature** | Useful capability not yet implemented |
| **User guidance** | Workflow/practice recommendation, not a JMF defect |
| **Config cleanup** | Configuration file maintenance |
| **Global rule addition** | New rule added to the global template |
| **Deep review finding** | Issue found during independent code review (Phase 4) |

## Column Definitions

| Column | Description |
|--------|-------------|
| **Observation** | Title of the observation |
| **Classification** | Category from legend above |
| **Source** | Which JMF-NOTES.md section triggered this |
| **What changed** | Files and lines affected |
| **Why** | Root cause or rationale |
| **How to verify** | Test name, manual step, or sbt command |
| **Status** | Open / Fixed / Doc-only / Won't fix (with reason) |

---

## Observations

### OBS-1: Descriptor format must be JVM internal, not human-readable

| Field | Value |
|-------|-------|
| **Classification** | User guidance |
| **Source** | JMF-NOTES.md section 1 |
| **What changed** | `jmf-rules.template.txt` — added JVM descriptor type mapping table to HowTo section; `AdoptionObservationsSpec.scala` — 4 new tests |
| **Why** | JMF matches against raw JVM bytecode descriptors. Writing human-readable types (e.g., `int` instead of `I`, `java.lang.String` instead of `Ljava/lang/String;`) produces rules that load successfully but silently never match. No code fix is feasible because human-readable type names are valid glob patterns — they just happen to never match JVM descriptors. |
| **How to verify** | `sbt "rewriterCore/testOnly *AdoptionObservationsSpec" -- -t "section 1"` |
| **Status** | Doc-only |

### OBS-2: FQCN globs must start with `*` to match qualified class names

| Field | Value |
|-------|-------|
| **Classification** | User guidance |
| **Source** | JMF-NOTES.md section 2 |
| **What changed** | `jmf-rules.template.txt` — added FQCN wildcard prefix warning to HowTo section; `AdoptionObservationsSpec.scala` — 3 new tests |
| **Why** | Glob matching is exact: `QueryResult` only matches the literal string "QueryResult", not "com.example.db.balta.classes.QueryResult". Users must prefix with `*` to match across packages. No code fix is feasible because bare class names are valid for classes in the default package. |
| **How to verify** | `sbt "rewriterCore/testOnly *AdoptionObservationsSpec" -- -t "section 2"` |
| **Status** | Doc-only |

### OBS-3: Non-matching rules are silently ignored

| Field | Value |
|-------|-------|
| **Classification** | Missing feature |
| **Source** | JMF-NOTES.md section 3 |
| **What changed** | `AdoptionObservationsSpec.scala` — 2 new tests confirming current behavior |
| **Why** | JMF does not report or warn when rules match nothing. A rule can be completely wrong and the tool will load it, count it, and do nothing. This is a deliberate design choice (forward-compatibility: rules may target classes not present in every build). The `--verify` mode partially addresses this by listing what rules match. A future enhancement could add "unmatched rules" reporting to verify mode. |
| **How to verify** | `sbt "rewriterCore/testOnly *AdoptionObservationsSpec" -- -t "section 3"` |
| **Status** | Doc-only — current behavior is intentional; unmatched-rule reporting is a future enhancement |

### OBS-4: Diagnosing a missed-coverage method

| Field | Value |
|-------|-------|
| **Classification** | User guidance |
| **Source** | JMF-NOTES.md section 4 |
| **What changed** | `jmf-rules.template.txt` — added diagnostic/verification workflow to HowTo section |
| **Why** | Users need a clear workflow for diagnosing why a method's coverage is not being filtered. The workflow involves: checking jacoco.xml, getting actual bytecode descriptors via `javap`, and comparing against rule patterns. |
| **How to verify** | Read the "DIAGNOSTIC WORKFLOW" section in `jmf-rules.template.txt` |
| **Status** | Doc-only |

### OBS-5: Scala 2.12 compiler-generated methods that produce coverable bytecode

| Field | Value |
|-------|-------|
| **Classification** | Global rule addition |
| **Source** | JMF-NOTES.md section 5 |
| **What changed** | `AdoptionObservationsSpec.scala` — 4 new tests; global rules were already added in Phase 2 (T2.3) |
| **Why** | Verified that existing global rules cover all patterns listed in section 5: `$anonfun$*` with synthetic flag, `$deserializeLambda$`, `*$extension`, `andThen`/`compose`, and `*$default$*`. All patterns confirmed by tests. |
| **How to verify** | `sbt "rewriterCore/testOnly *AdoptionObservationsSpec" -- -t "section 5"` |
| **Status** | Fixed (globals added in Phase 2) |

### OBS-6: Verifying new rules end-to-end

| Field | Value |
|-------|-------|
| **Classification** | User guidance |
| **Source** | JMF-NOTES.md section 6 |
| **What changed** | `jmf-rules.template.txt` — added end-to-end verification workflow to HowTo section |
| **Why** | Users need a clear workflow for verifying that new rules actually filter the intended methods. The workflow covers: baseline measurement, adding the rule, re-running, and confirming the change. |
| **How to verify** | Read the "VERIFICATION WORKFLOW" section in `jmf-rules.template.txt` |
| **Status** | Doc-only |

### OBS-7: Avoid broad wildcards — prefer `synthetic`/`bridge` flags

| Field | Value |
|-------|-------|
| **Classification** | User guidance |
| **Source** | JMF-NOTES.md section 7 |
| **What changed** | `AdoptionObservationsSpec.scala` — 1 new test; global lambda rule already uses `synthetic` flag |
| **Why** | The `synthetic` flag restricts matching to methods with ACC_SYNTHETIC set, which is exactly what the Scala compiler emits for lifted lambdas. The existing global rule `*#* synthetic name-contains:$anonfun$` already follows this best practice. |
| **How to verify** | `sbt "rewriterCore/testOnly *AdoptionObservationsSpec" -- -t "section 7"` |
| **Status** | Doc-only — best practice already followed in globals |

### OBS-8: Scala reflection in tests — case classes must be top-level

| Field | Value |
|-------|-------|
| **Classification** | User guidance |
| **Source** | JMF-NOTES.md section 8 |
| **What changed** | No code changes — this is a test-authoring guideline, not a JMF issue |
| **Why** | Scala reflection (`currentMirror.reflectClass`) fails on inner classes declared inside test suites. Case classes used in reflection-based tests must be declared at package scope. This is a Scala limitation, not a JMF bug. |
| **How to verify** | N/A — guidance only |
| **Status** | Doc-only |

### OBS-9: Rule file version header

| Field | Value |
|-------|-------|
| **Classification** | User guidance |
| **Source** | JMF-NOTES.md section 9 |
| **What changed** | `AdoptionObservationsSpec.scala` — 3 new tests |
| **Why** | The JMF-NOTES claim that "missing or malformed version headers cause the entire rule file to be skipped silently" is **incorrect** for the current implementation. The `# [jmf:1.0.0]` line in the template is just a comment (starts with `#`). JMF has no version header parsing — files without any special header load normally. A non-comment `[jmf:1.0.0]` would cause a parse error (missing `#` separator). |
| **How to verify** | `sbt "rewriterCore/testOnly *AdoptionObservationsSpec" -- -t "section 9"` |
| **Status** | Doc-only — observation was based on incorrect assumption about JMF behavior |

### OBS-10a: Colon-prefix flag syntax is wrong

| Field | Value |
|-------|-------|
| **Classification** | Doc bug |
| **Source** | JMF-NOTES.md section 10a |
| **What changed** | `jmf-rules.template.txt` — fixed Quick Examples from `*#*(*):synthetic` to `*#*(*) synthetic`; `AdoptionObservationsSpec.scala` — 3 new tests |
| **Why** | The colon before `synthetic`/`bridge` makes it part of the descriptor glob (no whitespace separation). The descriptor becomes `(*):synthetic` which never matches real JVM descriptors like `(I)V`. Rules load without error but silently never match. |
| **How to verify** | `sbt "rewriterCore/testOnly *AdoptionObservationsSpec" -- -t "section 10a"` |
| **Status** | Fixed |

### OBS-10b: `ret:` predicate syntax inconsistent

| Field | Value |
|-------|-------|
| **Classification** | Doc bug |
| **Source** | JMF-NOTES.md section 10b |
| **What changed** | `jmf-rules.template.txt` — standardized all examples to space-separated `ret:` format; `AdoptionObservationsSpec.scala` — 2 new tests |
| **Why** | The tutorial showed `ret:` in both colon-prefixed (`:ret:V`) and space-separated (`ret:V`) forms. Only the space-separated form works because the parser splits on whitespace first. The colon-prefixed form becomes part of the descriptor glob. |
| **How to verify** | `sbt "rewriterCore/testOnly *AdoptionObservationsSpec" -- -t "section 10b"` |
| **Status** | Fixed |

### OBS-10c: FQCN `*` prefix requirement not warned

| Field | Value |
|-------|-------|
| **Classification** | Doc bug |
| **Source** | JMF-NOTES.md section 10c |
| **What changed** | `jmf-rules.template.txt` — added explicit warning about `*` prefix requirement in HowTo section |
| **Why** | Overlaps with OBS-2. The tutorial's Quick Examples all use `*.model.*` (implicitly starting with `*`), but never states that bare class names won't match FQCNs. |
| **How to verify** | Read the "COMMON PITFALLS" section in `jmf-rules.template.txt` |
| **Status** | Fixed |

### OBS-10d: Descriptor examples don't show JVM format

| Field | Value |
|-------|-------|
| **Classification** | Doc bug |
| **Source** | JMF-NOTES.md section 10d |
| **What changed** | `jmf-rules.template.txt` — added JVM descriptor type mapping table to HowTo section |
| **Why** | Overlaps with OBS-1. The ALLOWED SYNTAX section correctly shows JVM format but Quick Examples use only `(*)`, giving the impression type-specific descriptors are rarely needed. |
| **How to verify** | Read the "JVM DESCRIPTOR TYPE MAPPING" section in `jmf-rules.template.txt` |
| **Status** | Fixed |

### OBS-10e: Empty/short descriptor form equivalence

| Field | Value |
|-------|-------|
| **Classification** | Doc bug |
| **Source** | JMF-NOTES.md section 10e |
| **What changed** | `jmf-rules.template.txt` — added normalization table to HowTo; `AdoptionObservationsSpec.scala` — 1 new test |
| **Why** | The tutorial states `(*)` normalizes to `(*)*` but doesn't show the full normalization table: `""`, `()`, `(*)` all normalize to `(*)*`. Users need to understand that all short forms are equivalent wildcards. |
| **How to verify** | `sbt "rewriterCore/testOnly *AdoptionObservationsSpec" -- -t "section 10e"` |
| **Status** | Fixed |

### OBS-10f: `()` normalisation is misleading

| Field | Value |
|-------|-------|
| **Classification** | Doc bug |
| **Source** | JMF-NOTES.md section 10f |
| **What changed** | `jmf-rules.template.txt` — added warning about `()` matching all overloads; `AdoptionObservationsSpec.scala` — 1 new test |
| **Why** | `*#productElement()` reads as "no-arg method" but actually matches `productElement(I)Ljava/lang/Object;` because `()` normalizes to `(*)*`. Users targeting specific overloads must use explicit descriptors. |
| **How to verify** | `sbt "rewriterCore/testOnly *AdoptionObservationsSpec" -- -t "section 10f"` |
| **Status** | Fixed |

### OBS-10g: Quick Example comments use human-readable types

| Field | Value |
|-------|-------|
| **Classification** | Doc bug |
| **Source** | JMF-NOTES.md section 10g |
| **What changed** | `jmf-rules.template.txt` — rewrote Quick Example comments to use JVM descriptor format |
| **Why** | Comments like "Matches `productElement(int)`" contradict the formal syntax requirement for JVM descriptors. Users copying comment text into rules would get silently non-matching rules. |
| **How to verify** | Read the "QUICK EXAMPLES" section in `jmf-rules.template.txt` |
| **Status** | Fixed |

### OBS-10h: `ret:` semicolon inconsistency

| Field | Value |
|-------|-------|
| **Classification** | Doc bug |
| **Source** | JMF-NOTES.md section 10h |
| **What changed** | `jmf-rules.template.txt` — corrected all `ret:` examples to include trailing semicolon for object types; `AdoptionObservationsSpec.scala` — 1 new test |
| **Why** | JVM object type descriptors always end with `;` (e.g., `Ljava/lang/String;`). A `ret:` glob without trailing `;` will fail to match exact object return types. |
| **How to verify** | `sbt "rewriterCore/testOnly *AdoptionObservationsSpec" -- -t "section 10h"` |
| **Status** | Fixed |

### OBS-10i: `id:` listed as optional but should be mandatory

| Field | Value |
|-------|-------|
| **Classification** | Doc bug |
| **Source** | JMF-NOTES.md section 10i |
| **What changed** | `jmf-rules.template.txt` — strengthened `id:` description to "required for traceability" |
| **Why** | Omitting `id:` makes log output unreadable when rules fire. While JMF technically allows rules without `id:`, it should be treated as mandatory in practice. A code-level validation warning is a future enhancement. |
| **How to verify** | Read the PREDICATES section in `jmf-rules.template.txt` |
| **Status** | Fixed (doc); code validation is future enhancement |

### OBS-10j: `#` dual role ambiguity

| Field | Value |
|-------|-------|
| **Classification** | User guidance |
| **Source** | JMF-NOTES.md section 10j |
| **What changed** | `jmf-rules.template.txt` — added note about `#` as comment vs separator; `AdoptionObservationsSpec.scala` — 2 new tests |
| **Why** | `#` serves as both comment marker (start of line) and FQCN/method separator. Inline `# comment` after a rule line is harmless (parsed as unknown tokens and ignored) but could be confusing. Best practice: use dedicated comment lines. |
| **How to verify** | `sbt "rewriterCore/testOnly *AdoptionObservationsSpec" -- -t "section 10j"` |
| **Status** | Doc-only |

### OBS-10k: CONSERVATIVE/STANDARD/AGGRESSIVE labels not applied

| Field | Value |
|-------|-------|
| **Classification** | Doc bug |
| **Source** | JMF-NOTES.md section 10k |
| **What changed** | `jmf-rules.template.txt` — removed references to CONSERVATIVE/STANDARD/AGGRESSIVE tiering from Quick Start; replaced with practical section-based organization (GLOBALS, PROJECT RULES) |
| **Why** | The tiering labels were mentioned in the intro but never applied to examples, making them confusing. The actual template uses GLOBALS RULES / PROJECT RULES sections which are more practical. |
| **How to verify** | Read the HOW TO USE section in `jmf-rules.template.txt` |
| **Status** | Fixed |

### OBS-10l: "dot-form" note contradicts `$` usage

| Field | Value |
|-------|-------|
| **Classification** | Doc bug |
| **Source** | JMF-NOTES.md section 10l |
| **What changed** | `jmf-rules.template.txt` — clarified "dot-form" note to explain that `$` is required for inner classes and companion objects |
| **Why** | "Always use dot-form (com.example.Foo)" could be misread as "never use `$`", but `$` is required for inner classes (`Foo$Bar`) and companion objects (`Foo$`). The note means "use `.` as the package separator (not `/`)". |
| **How to verify** | Read the Notes section in `jmf-rules.template.txt` |
| **Status** | Fixed |

### OBS-10m: Universal wildcard missing safety warning

| Field | Value |
|-------|-------|
| **Classification** | Doc bug |
| **Source** | JMF-NOTES.md section 10m |
| **What changed** | `jmf-rules.template.txt` — added "DO NOT commit" warning to `*#*(*)` example |
| **Why** | `*#*(*)` suppresses JaCoCo for every method in the codebase, producing artificially inflated (up to 100%) coverage. Left enabled in CI, it would silently mask all regressions. |
| **How to verify** | Read the QUICK EXAMPLES section in `jmf-rules.template.txt` |
| **Status** | Fixed |

### OBS-10n: Scala var setter `_$eq` name mangling not explained

| Field | Value |
|-------|-------|
| **Classification** | Doc bug |
| **Source** | JMF-NOTES.md section 10n |
| **What changed** | `jmf-rules.template.txt` — added name-mangling explanation to setter example and name-mangling reference table |
| **Why** | The source-level `name_=` is compiled to `name_$eq` in bytecode. Without explaining this mangling, users might try `*_=(*)` (the source form) which would never match. |
| **How to verify** | Read the QUICK EXAMPLES and NAME MANGLING sections in `jmf-rules.template.txt` |
| **Status** | Fixed |

### OBS-11: Quick reference — JMF rule anatomy

| Field | Value |
|-------|-------|
| **Classification** | User guidance |
| **Source** | JMF-NOTES.md section 11 |
| **What changed** | `jmf-rules.template.txt` — added rule anatomy quick reference to HowTo section |
| **Why** | A concise reference showing the full rule structure helps users understand all components at a glance. |
| **How to verify** | Read the RULE ANATOMY section in `jmf-rules.template.txt` |
| **Status** | Fixed |

### OBS-12: Real bugs found in jmf-rules.txt via audit

| Field | Value |
|-------|-------|
| **Classification** | User guidance |
| **Source** | JMF-NOTES.md section 12 |
| **What changed** | `jmf-rules.template.txt` — added audit command to HowTo section |
| **Why** | The adopter found two real bugs in their own rules file using a simple grep audit command. The bugs were human-readable descriptors (`scala.Function1` instead of `Lscala/Function1;`). The audit command `grep -n '[a-z]\.[A-Z]' jmf-rules.txt | grep -v '^#'` detects this pattern. Current `jmf-rules.template.txt` passes this audit cleanly. |
| **How to verify** | `grep -n '[a-z]\.[A-Z]' jmf-rules.template.txt \| grep -v '^#'` should return no results |
| **Status** | Doc-only — template is clean; audit command added for users |

### OBS-13: Scala source name vs bytecode name

| Field | Value |
|-------|-------|
| **Classification** | User guidance |
| **Source** | JMF-NOTES.md section 13 |
| **What changed** | `jmf-rules.template.txt` — added Scala name-mangling reference table |
| **Why** | JMF operates on bytecodes, so globs must use compiled method names, not source names. The mangling table (e.g., `name_=` -> `name_$eq`, `++` -> `$plus$plus`) is essential reference material for Scala users. |
| **How to verify** | Read the NAME MANGLING section in `jmf-rules.template.txt` |
| **Status** | Fixed |

---

## Summary

### Counts by Classification

| Classification | Count |
|---------------|-------|
| Doc bug | 12 (OBS-10a through OBS-10n, excluding 10c which is combined with OBS-2) |
| User guidance | 7 (OBS-1, OBS-2, OBS-3, OBS-4, OBS-7, OBS-8, OBS-9, OBS-10j) |
| Missing feature | 1 (OBS-3 — unmatched rules diagnostic) |
| Global rule addition | 1 (OBS-5 — confirmed in Phase 2) |
| Code bug | 0 |

### New Tests Added

| Test Name | Reference |
|-----------|-----------|
| `section 1: human-readable descriptor does not match JVM descriptor` | T3.1 |
| `section 1: JVM descriptor matches correctly` | T3.1 |
| `section 1: human-readable String descriptor does not match` | T3.1 |
| `section 1: JVM String descriptor matches correctly` | T3.1 |
| `section 2: bare class name without * prefix does not match FQCN` | T3.2 |
| `section 2: * prefix matches FQCN correctly` | T3.2 |
| `section 2: bare class name matches only exact class name` | T3.2 |
| `section 3: rule that matches nothing loads without error` | T3.3 |
| `section 3: non-matching rule produces no error during matching` | T3.3 |
| `section 5: global rules cover $anonfun$ with synthetic flag` | T3.5 |
| `section 5: global rules cover $deserializeLambda$` | T3.5 |
| `section 5: global rules cover $extension methods` | T3.5 |
| `section 5: global rules cover andThen/compose` | T3.5 |
| `section 5: global rules cover default parameter accessors` | T3.5 |
| `section 7: synthetic flag restricts to ACC_SYNTHETIC methods only` | T3.7 |
| `section 9: file without version header loads rules normally` | T3.9 |
| `section 9: comment-style version header is silently ignored` | T3.9 |
| `section 9: non-comment version header causes parse error` | T3.9 |
| `section 10a: colon-prefix :synthetic is treated as part of descriptor` | T3.10a |
| `section 10a: space-separated synthetic flag works correctly` | T3.10a |
| `section 10a: colon-prefix :bridge is also treated as descriptor` | T3.10a |
| `section 10b: colon-prefixed :ret:V is part of descriptor` | T3.10b |
| `section 10b: space-separated ret:V works correctly` | T3.10b |
| `section 10e: omitted, (), and (*) all normalize to wildcard` | T3.10e |
| `section 10f: () matches methods WITH arguments due to normalization` | T3.10f |
| `section 10h: ret: without trailing semicolon fails for exact object types` | T3.10h |
| `section 10j: inline # comment after rule is ignored as unknown token` | T3.10j |
| `section 10j: dedicated comment line is cleanly ignored` | T3.10j |
| `colon-prefixed flags load without error but produce non-matching rules` | T3.10a/b |

### Files Changed

| File | Type of Change |
|------|---------------|
| `rewriter-core/src/test/scala/.../AdoptionObservationsSpec.scala` | New test file |
| `jmf-rules.template.txt` | HowTo rewrite (Phase 5) |
| `maven-plugin/src/main/resources/jmf-rules.template.txt` | Synced with root template |
| `adoption_feedback/OBSERVATIONS.md` | New report file |
| `adoption_feedback/TASK.md` | Task completion tracking |

### Open Items

| Item | Phase |
|------|-------|
| Unmatched rules diagnostic in verify mode | Future enhancement (OBS-3) |
| Code-level validation warning for missing `id:` | Future enhancement (OBS-10i) |

---

## Phase 4 — Deep Reverse Review

Independent review of all JMF core components. No new bugs found.

### DR-1: Rules.scala — parsing and matching

| Field | Value |
|-------|-------|
| **Classification** | Deep review finding |
| **What reviewed** | `parseLine()`, `normalizeDesc()`, `matches()`, `loadAll()`, `loadFromUrl()` |
| **Finding** | No bugs. Parsing logic correctly handles: whitespace splitting, `#` separator, descriptor normalization, flag/predicate parsing, include/exclude modes, URL loading with timeouts. Unknown tokens are silently ignored for forward-compatibility. |
| **Status** | No issues |

### DR-2: Glob.scala — glob-to-regex conversion

| Field | Value |
|-------|-------|
| **Classification** | Deep review finding |
| **What reviewed** | `Glob.toRegex()` |
| **Finding** | No bugs. Correctly converts `*` to `.*`, `?` to `.`, escapes all regex metacharacters including `$` (essential for Scala companion objects). Empty glob matches only empty string. |
| **Status** | No issues |

### DR-3: CoverageRewriter.scala — bytecode rewriting

| Field | Value |
|-------|-------|
| **Classification** | Deep review finding |
| **What reviewed** | `rewriteClassFile()`, ASM ClassVisitor/MethodVisitor chain |
| **Finding** | No bugs. Correctly uses `RuleResolver.resolve()` for include/exclude logic. Checks `alreadyAnnotated` to prevent double-annotation. Dry-run mode writes original bytes. |
| **Status** | No issues |

### DR-4: CoverageRewriterCli.scala — CLI argument handling

| Field | Value |
|-------|-------|
| **Classification** | Deep review finding |
| **What reviewed** | `parse()`, scopt option definitions, `checkConfig` |
| **Finding** | No bugs. Validates: `--in` exists and is a directory, `--out` required in non-verify mode, at least one rules source required. |
| **Status** | No issues |

### DR-5: VerifyScanner.scala — read-only scan mode

| Field | Value |
|-------|-------|
| **Classification** | Deep review finding |
| **What reviewed** | `scan()`, `ScanResult`, `printReport()` |
| **Finding** | No bugs. Performance-optimized with `ClassReader.SKIP_CODE | SKIP_DEBUG | SKIP_FRAMES`. Report groups by class and sorts alphabetically. |
| **Status** | No issues |

### DR-6: sbt plugin — JacocoFilterPlugin.scala

| Field | Value |
|-------|-------|
| **Classification** | Deep review finding |
| **What reviewed** | Classpath wiring, JMF rewrite task, agent attachment, report generation |
| **Finding** | No bugs. Complex but correct. Rewritten classes placed first on test classpath. Handles enabled/disabled states properly. |
| **Status** | No issues |

### DR-7: Maven plugin — RewriteMojo.java

| Field | Value |
|-------|-------|
| **Classification** | Deep review finding |
| **What reviewed** | `execute()`, `checkInputs()`, `assembleCmdLine()`, `buildCp()`, `launchSubprocess()` |
| **Finding** | No bugs. Proper subprocess management with output capture. Log routing correctly handles [info]/[warn]/[error] prefixes. Skips pom-packaged aggregator projects. |
| **Status** | No issues |

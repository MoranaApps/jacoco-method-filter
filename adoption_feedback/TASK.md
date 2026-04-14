# Adoption Feedback — Task List

Reference: [SPEC.md](SPEC.md) for full specification and rationale.

---

## Phase 1 — Configuration & Cleanup

### T1.1 — Update `.github/copilot-instructions.md`
- [x] Read `adoption_feedback/copilot-instructions.md` for structural patterns
- [x] Merge applicable improvements into `.github/copilot-instructions.md`:
  - [x] Add constraint words (Must/Must not/Prefer/Avoid) where missing
  - [x] Strengthen output discipline section
  - [x] Refine PR body management section
  - [x] Review and align coding guidelines section
- [x] Add new section: **JMF filtering decision rules** (see SPEC.md §G1), covering:
  - [x] Criteria for "filter this method" (compiler-generated, no branching, no logic)
  - [x] Criteria for "do NOT filter" (branching, validation, transformation, multi-call)
  - [x] Borderline cases and how to resolve them (single-call delegates, trivial negations)
  - [x] Verification requirement: `javap -p -verbose` before adding any rule
  - [x] Global rule override: use `+` include rules to rescue falsely-filtered methods
  - [x] Pointer to official project documentation for adopters
        _(note: copilot instructions reference requirement; actual README/DEVELOPER.md update is a future task)_
- [x] Preserve all Scala/sbt/JMF-specific content
- [x] Verify no Python-specific content leaked in

### T1.2 — Update `.github/copilot-review-rules.md`
- [x] Read `adoption_feedback/copilot-review-rules.md` for structural patterns
- [x] Merge applicable improvements into `.github/copilot-review-rules.md`:
  - [x] Add "Writing style" section if missing
  - [x] Strengthen commenting rules (what/why/how format)
  - [x] Refine double-check review mode (hidden side effects, safe defaults)
- [x] Preserve all JMF-specific high-risk areas and contract outputs

### T1.3 — Remove `.github/agents/` folder
- [x] Verify folder contents (5 agent files, nothing else)
- [x] Remove `.github/agents/` directory entirely

---

## Phase 2 — Global Rules Analysis

### T2.1 — Identify new global rules from adoption
- [x] Compare `adoption_feedback/jmf-rules.txt` globals against `jmf-rules.template.txt`
- [x] For each candidate global rule (see SPEC.md §G3):
  - [x] Verify the rule pattern is compiler-generated (not user logic)
  - [x] Confirm JVM descriptor format is correct
  - [x] Classify: safe-global vs needs-warning vs project-only
- [x] Document decision for each candidate
      _(decisions: $deserializeLambda$ + all $extension + gen-defaults = safe-global;
      andThen/compose = safe-global with caution note; apply = disabled;
      name/groups/optionalAttributes = active with domain-collision warning;
      productElementName/productElementNames = safe-global Scala 2.13+)_

### T2.2 — Add safety warning for global rules that may over-filter
- [x] Draft warning text for `jmf-rules.template.txt` HowTo section
- [x] Use `*#apply(*)` as primary example (adopter commented it out)
- [x] List other potentially dangerous globals: `*#name()`, `*#groups()`, `*#optionalAttributes()`
- [x] Add guidance: "check if global values do not filter out really implemented methods"
- [x] Show commented-out examples as illustration

### T2.3 — Update `jmf-rules.template.txt` with new global rules
- [x] Add validated new globals to GLOBALS RULES section
      _(added: $deserializeLambda$, 10x $extension rules, andThen/compose, gen-defaults,
      productElementName/productElementNames)_
- [x] Comment out `*#apply(*)` with warning, or move to examples
- [x] Add `*#copy$default$*(*)` note (subsumed by `gen-defaults`)
- [x] Ensure every rule has `id:` label
- [x] Sync `maven-plugin/src/main/resources/jmf-rules.template.txt` with root template
      _(sbt-plugin copy is a build artifact — auto-synced via IO.copyFile in build.sbt)_

---

## Phase 3 — Observation Review (from JMF-NOTES.md)

Each task follows the cycle: **review → test (red) → fix → test (green) → quality gates**.

### T3.0 — Start `adoption_feedback/OBSERVATIONS.md`
- [x] Create the file with header, classification legend, and column definitions
- [x] Add a row for each known observation (§1–§13 + §10a–§10n) with status "Open"
- [x] This file is updated after each T3.x task is completed

### T3.1 — §1: Descriptor format must be JVM internal, not human-readable
- [x] Review: confirm JMF uses raw JVM descriptors for matching
- [x] Test: write test with human-readable descriptor → expect no match
- [x] Test: write test with JVM descriptor → expect match
- [x] Fix: add validation/warning for human-readable descriptors if feasible
      _(result: doc-only — human-readable types are valid globs that just never match JVM descriptors; no reliable heuristic detection)_
- [x] Green: confirm tests pass
- [x] Quality gates: `sbt +test`
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.2 — §2: FQCN globs must start with `*` to match qualified class names
- [x] Review: confirm bare class name without `*` prefix fails to match FQCN
- [x] Test: write test with `ClassName#method()` → expect no match on FQCN
- [x] Test: write test with `*ClassName#method()` → expect match
- [x] Fix: add validation/warning for rules missing leading wildcard if feasible
      _(result: doc-only — bare class names are valid for classes in the default package)_
- [x] Green: confirm tests pass
- [x] Quality gates: `sbt +test`
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.3 — §3: Non-matching rules are silently ignored
- [x] Review: confirm JMF does not report/warn on unmatched rules
- [x] Test: write test loading a rule that matches nothing → verify no error
- [x] Fix: evaluate adding an "unmatched rules" diagnostic (log-level or report)
      _(result: doc-only — current behavior is intentional for forward-compatibility; unmatched-rule reporting is a future enhancement)_
- [x] Green: confirm tests pass
- [x] Quality gates: `sbt +test`
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.4 — §4: Diagnosing a missed-coverage method
- [x] Review: this is a workflow/documentation issue, not a code bug
- [x] Document: add diagnostic workflow to template HowTo or separate guide
- [x] No code fix required (documentation only)
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.5 — §5: Scala 2.12 compiler-generated methods that produce coverable bytecode
- [x] Review: confirm listed patterns (`$anonfun$*`, `$deserializeLambda$`, etc.) are correct
- [x] Test: verify existing global rules cover all listed patterns
- [x] Fix: add any missing patterns to globals
      _(result: all patterns already covered by globals added in Phase 2)_
- [x] Green: confirm tests pass
- [x] Quality gates: `sbt +test`
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.6 — §6: Verifying new rules end-to-end
- [x] Review: this is a workflow/documentation issue
- [x] Document: add end-to-end verification workflow to template HowTo
- [x] No code fix required (documentation only)
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.7 — §7: Avoid broad wildcards — prefer `synthetic`/`bridge` flags
- [x] Review: confirm `synthetic` flag restricts to ACC_SYNTHETIC methods
- [x] Test: write test with `synthetic` flag → matches only synthetic methods
- [x] Test: write test without `synthetic` flag → matches both synthetic and non-synthetic
- [x] Fix: update global lambda rule if it lacks the flag
      _(result: global rule already uses synthetic flag correctly)_
- [x] Green: confirm tests pass
- [x] Quality gates: `sbt +test`
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.8 — §8: Scala reflection in tests — case classes must be top-level
- [x] Review: this is a test-authoring guideline, not a JMF code issue
- [x] Document: add note to testing guidelines or copilot instructions
- [x] No code fix required (documentation/guidance only)
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.9 — §9: Rule file version header
- [x] Review: confirm missing/malformed `[jmf:1.0.0]` causes silent file skip
      _(result: observation is incorrect — JMF has no version header parsing; the `# [jmf:1.0.0]` is just a comment; non-comment version header causes parse error)_
- [x] Test: write test loading file without version header → verify behavior
- [x] Test: write test loading file with malformed version header → verify behavior
- [x] Fix: add error/warning for missing version header if currently silent
      _(result: doc-only — current behavior is correct; no silent skip occurs)_
- [x] Green: confirm tests pass
- [x] Quality gates: `sbt +test`
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.10 — §10: Misleading/incorrect hints in official JMF tutorial
This is the largest observation with 14 sub-points. Each sub-point is a documentation
fix in `jmf-rules.template.txt`.

#### T3.10a — Colon-prefix flag syntax is wrong
- [x] Review: confirm `:synthetic` does not work (space-separated does)
- [x] Test: write test with `:synthetic` → expect parse failure or no match
- [x] Fix: correct all Quick Examples from `*#*(*):synthetic` to `*#*(*) synthetic`
- [x] Green: confirm tests pass
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10b — `ret:` predicate syntax inconsistent
- [x] Review: confirm space-separated `ret:V` works, colon-prefixed `:ret:V` does not
- [x] Test: write test with both formats
- [x] Fix: standardize examples to space-separated format
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10c — FQCN `*` prefix requirement not warned
- [x] Fix: add explicit warning in HowTo section about `*` prefix
- [x] Documentation only (overlaps with T3.2)
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10d — Descriptor examples don't show JVM format
- [x] Fix: add JVM descriptor examples and type mapping table to HowTo
- [x] Documentation only (overlaps with T3.1)
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10e — Empty/short descriptor form equivalence
- [x] Review: confirm `()`, `(*)`, and omitted all normalize to `(*)*`
- [x] Test: write tests for each form
- [x] Fix: clarify in HowTo with explicit normalization table
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10f — `()` normalisation is misleading
- [x] Review: confirm `*#productElement()` matches `productElement(I)Ljava/lang/Object;`
- [x] Test: write test demonstrating `()` matches methods with args
- [x] Fix: add warning in HowTo about `()` matching all overloads
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10g — Quick Example comments use human-readable types
- [x] Fix: rewrite comments to use JVM descriptor format
- [x] Documentation only
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10h — `ret:` semicolon inconsistency
- [x] Review: confirm `ret:Lcom/example/*` (no semicolon) fails
- [x] Test: write test with and without trailing semicolon
- [x] Fix: correct examples, add note about mandatory semicolon
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10i — `id:` listed as optional but should be mandatory
- [x] Review: confirm omitting `id:` produces unreadable logs
- [x] Fix: change documentation to say `id:` is required
- [x] Consider: add validation warning when `id:` is missing
      _(result: doc-only; code validation is a future enhancement)_
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10j — `#` dual role ambiguity
- [x] Review: test if inline `# comment` after a rule causes problems
- [x] Test: write test with inline comment on rule line
- [x] Fix: document recommendation to use dedicated comment lines
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10k — CONSERVATIVE/STANDARD/AGGRESSIVE labels not applied
- [x] Fix: either apply labels to examples or remove the tiered approach
      _(result: removed tiering references; replaced with practical GLOBALS/PROJECT RULES organization)_
- [x] Documentation only
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10l — "dot-form" note contradicts `$` usage
- [x] Fix: clarify that `$` is required for inner classes/companions
- [x] Documentation only
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10m — Universal wildcard missing safety warning
- [x] Fix: add "DO NOT commit" warning to `*#*(*)` example
- [x] Documentation only
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10n — Scala var setter `_$eq` name mangling not explained
- [x] Fix: add explanation of source → bytecode name mangling
- [x] Documentation only
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.11 — §11: Quick reference — JMF rule anatomy
- [x] Review: verify the anatomy description is complete and accurate
- [x] Fix: integrate into template HowTo if not already present
- [x] Documentation only
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.12 — §12: Real bugs found in jmf-rules.txt via audit
- [x] Review: confirm the two bugs (human-readable descriptors) were already fixed
- [x] Document: add the `grep` audit command to developer guidelines
- [x] Verify: run the audit command against current `jmf-rules.template.txt`
      _(result: template is clean — no human-readable descriptors found)_
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.13 — §13: Scala source name vs bytecode name
- [x] Review: confirm name-mangling table is accurate
- [x] Fix: add name-mangling reference to template HowTo
- [x] Documentation only
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.14 — Finalize `adoption_feedback/OBSERVATIONS.md` (end of Phase 3)
- [x] Verify all observations have been recorded with complete fields
- [x] Add summary section:
  - [x] Count by classification (code bug / doc bug / missing feature / user guidance / config)
  - [x] List all new tests added (name + T3.x reference)
  - [x] List all files changed (file + type of change)
  - [x] List any open items remaining for Phase 4 (deep review)
- [x] Review for consistency: no gaps, no duplicate entries, statuses accurate

---

## Phase 4 — Deep Reverse Review

### T4.1 — Independent deep review of JMF solution
- [x] Review rule parsing (`Rules.load`, `Rules.matches`) for edge cases
- [x] Review bytecode rewriting for correctness
- [x] Review CLI argument handling
- [x] Review sbt plugin integration
- [x] Review Maven plugin integration
- [x] Document any new findings in OBSERVATIONS.md (append to existing file)
      _(result: no bugs found — 7 deep review findings all show "No issues")_

### T4.2 — Fix newly discovered issues
- [x] For each new finding: test (red) → fix → test (green) → quality gates
      _(result: no issues to fix — deep review found zero bugs)_
- [x] Update OBSERVATIONS.md: record what changed, why, how to verify, status

---

## Phase 5 — Template Documentation Rewrite

### T5.1 — Rewrite `jmf-rules.template.txt` HowTo section
- [x] Fix all 14 sub-points from §10 (T3.10a–T3.10n)
- [x] Add JVM descriptor type mapping table
- [x] Add FQCN wildcard prefix requirement
- [x] Add global rule safety warning with examples
- [x] Add diagnostic/verification workflow
- [x] Add name-mangling reference
- [x] Add rule anatomy quick reference
- [x] Ensure all examples use correct syntax (space-separated flags, JVM descriptors)
- [x] Remove or fix CONSERVATIVE/STANDARD/AGGRESSIVE tiering

---

## Phase 6 — Code Review Rounds

### T6.1 — Round 1: Full code review of all changes
- [x] Review all modified files for correctness
- [x] Check for regressions
- [x] Verify test coverage
- [x] Document findings
      _(result: zero findings)_

### T6.2 — Round 2: Second pass
- [x] Address Round 1 findings
- [x] Re-review affected areas
- [x] Document findings
      _(result: zero findings)_

### T6.3 — Round 3: Third pass
- [x] Address Round 2 findings
- [x] Re-review affected areas
- [x] Document findings
      _(result: zero findings)_

### T6.4 — Round 4: Fourth pass
- [x] Address Round 3 findings
- [x] Re-review affected areas
- [x] Document findings
      _(result: zero findings)_

### T6.5 — Round 5: Fifth pass (minimum final)
- [x] Address Round 4 findings
- [x] Re-review all changes
- [x] If findings exist: continue to Round 6+
- [x] If zero findings: mark review complete
      _(result: zero findings — review complete)_

---

## Completion Criteria

- [x] All Phase 1–5 tasks completed
- [x] All tests pass: `sbt +test`
- [x] Code review rounds completed with zero findings in final round
- [x] OBSERVATIONS.md is complete, finalized, and accurate
- [x] `jmf-rules.template.txt` HowTo is rewritten and accurate
- [x] `.github/copilot-instructions.md` updated (including JMF filtering decision rules)
- [x] `.github/copilot-review-rules.md` updated
- [x] `.github/agents/` removed

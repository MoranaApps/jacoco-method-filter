# Adoption Feedback — Task List

Reference: [SPEC.md](SPEC.md) for full specification and rationale.

---

## Phase 1 — Configuration & Cleanup

### T1.1 — Update `.github/copilot-instructions.md`
- [ ] Read `adoption_feedback/copilot-instructions.md` for structural patterns
- [ ] Merge applicable improvements into `.github/copilot-instructions.md`:
  - [ ] Add constraint words (Must/Must not/Prefer/Avoid) where missing
  - [ ] Strengthen output discipline section
  - [ ] Refine PR body management section
  - [ ] Review and align coding guidelines section
- [ ] Add new section: **JMF filtering decision rules** (see SPEC.md §G1), covering:
  - [ ] Criteria for "filter this method" (compiler-generated, no branching, no logic)
  - [ ] Criteria for "do NOT filter" (branching, validation, transformation, multi-call)
  - [ ] Borderline cases and how to resolve them (single-call delegates, trivial negations)
  - [ ] Verification requirement: `javap -p -verbose` before adding any rule
  - [ ] Global rule override: use `+` include rules to rescue falsely-filtered methods
  - [ ] Pointer to official project documentation for adopters
- [ ] Preserve all Scala/sbt/JMF-specific content
- [ ] Verify no Python-specific content leaked in

### T1.2 — Update `.github/copilot-review-rules.md`
- [ ] Read `adoption_feedback/copilot-review-rules.md` for structural patterns
- [ ] Merge applicable improvements into `.github/copilot-review-rules.md`:
  - [ ] Add "Writing style" section if missing
  - [ ] Strengthen commenting rules (what/why/how format)
  - [ ] Refine double-check review mode (hidden side effects, safe defaults)
- [ ] Preserve all JMF-specific high-risk areas and contract outputs

### T1.3 — Remove `.github/agents/` folder
- [ ] Verify folder contents (5 agent files, nothing else)
- [ ] Remove `.github/agents/` directory entirely

---

## Phase 2 — Global Rules Analysis

### T2.1 — Identify new global rules from adoption
- [ ] Compare `adoption_feedback/jmf-rules.txt` globals against `jmf-rules.template.txt`
- [ ] For each candidate global rule (see SPEC.md §G3):
  - [ ] Verify the rule pattern is compiler-generated (not user logic)
  - [ ] Confirm JVM descriptor format is correct
  - [ ] Classify: safe-global vs needs-warning vs project-only
- [ ] Document decision for each candidate

### T2.2 — Add safety warning for global rules that may over-filter
- [ ] Draft warning text for `jmf-rules.template.txt` HowTo section
- [ ] Use `*#apply(*)` as primary example (adopter commented it out)
- [ ] List other potentially dangerous globals: `*#name()`, `*#groups()`, `*#optionalAttributes()`
- [ ] Add guidance: "check if global values do not filter out really implemented methods"
- [ ] Show commented-out examples as illustration

### T2.3 — Update `jmf-rules.template.txt` with new global rules
- [ ] Add validated new globals to GLOBALS RULES section
- [ ] Comment out `*#apply(*)` with warning, or move to examples
- [ ] Add `*#copy$default$*(*)` note (subsumed by `gen-defaults`)
- [ ] Ensure every rule has `id:` label

---

## Phase 3 — Observation Review (from JMF-NOTES.md)

Each task follows the cycle: **review → test (red) → fix → test (green) → quality gates**.

### T3.0 — Start `adoption_feedback/OBSERVATIONS.md`
- [ ] Create the file with header, classification legend, and column definitions
- [ ] Add a row for each known observation (§1–§13 + §10a–§10n) with status "Open"
- [ ] This file is updated after each T3.x task is completed

### T3.1 — §1: Descriptor format must be JVM internal, not human-readable
- [ ] Review: confirm JMF uses raw JVM descriptors for matching
- [ ] Test: write test with human-readable descriptor → expect no match
- [ ] Test: write test with JVM descriptor → expect match
- [ ] Fix: add validation/warning for human-readable descriptors if feasible
- [ ] Green: confirm tests pass
- [ ] Quality gates: `sbt +test`
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.2 — §2: FQCN globs must start with `*` to match qualified class names
- [ ] Review: confirm bare class name without `*` prefix fails to match FQCN
- [ ] Test: write test with `ClassName#method()` → expect no match on FQCN
- [ ] Test: write test with `*ClassName#method()` → expect match
- [ ] Fix: add validation/warning for rules missing leading wildcard if feasible
- [ ] Green: confirm tests pass
- [ ] Quality gates: `sbt +test`
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.3 — §3: Non-matching rules are silently ignored
- [ ] Review: confirm JMF does not report/warn on unmatched rules
- [ ] Test: write test loading a rule that matches nothing → verify no error
- [ ] Fix: evaluate adding an "unmatched rules" diagnostic (log-level or report)
- [ ] Green: confirm tests pass
- [ ] Quality gates: `sbt +test`
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.4 — §4: Diagnosing a missed-coverage method
- [ ] Review: this is a workflow/documentation issue, not a code bug
- [ ] Document: add diagnostic workflow to template HowTo or separate guide
- [ ] No code fix required (documentation only)
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.5 — §5: Scala 2.12 compiler-generated methods that produce coverable bytecode
- [ ] Review: confirm listed patterns (`$anonfun$*`, `$deserializeLambda$`, etc.) are correct
- [ ] Test: verify existing global rules cover all listed patterns
- [ ] Fix: add any missing patterns to globals
- [ ] Green: confirm tests pass
- [ ] Quality gates: `sbt +test`
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.6 — §6: Verifying new rules end-to-end
- [ ] Review: this is a workflow/documentation issue
- [ ] Document: add end-to-end verification workflow to template HowTo
- [ ] No code fix required (documentation only)
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.7 — §7: Avoid broad wildcards — prefer `synthetic`/`bridge` flags
- [ ] Review: confirm `synthetic` flag restricts to ACC_SYNTHETIC methods
- [ ] Test: write test with `synthetic` flag → matches only synthetic methods
- [ ] Test: write test without `synthetic` flag → matches both synthetic and non-synthetic
- [ ] Fix: update global lambda rule if it lacks the flag
- [ ] Green: confirm tests pass
- [ ] Quality gates: `sbt +test`
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.8 — §8: Scala reflection in tests — case classes must be top-level
- [ ] Review: this is a test-authoring guideline, not a JMF code issue
- [ ] Document: add note to testing guidelines or copilot instructions
- [ ] No code fix required (documentation/guidance only)
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.9 — §9: Rule file version header
- [ ] Review: confirm missing/malformed `[jmf:1.0.0]` causes silent file skip
- [ ] Test: write test loading file without version header → verify behavior
- [ ] Test: write test loading file with malformed version header → verify behavior
- [ ] Fix: add error/warning for missing version header if currently silent
- [ ] Green: confirm tests pass
- [ ] Quality gates: `sbt +test`
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.10 — §10: Misleading/incorrect hints in official JMF tutorial
This is the largest observation with 14 sub-points. Each sub-point is a documentation
fix in `jmf-rules.template.txt`.

#### T3.10a — Colon-prefix flag syntax is wrong
- [ ] Review: confirm `:synthetic` does not work (space-separated does)
- [ ] Test: write test with `:synthetic` → expect parse failure or no match
- [ ] Fix: correct all Quick Examples from `*#*(*):synthetic` to `*#*(*) synthetic`
- [ ] Green: confirm tests pass
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10b — `ret:` predicate syntax inconsistent
- [ ] Review: confirm space-separated `ret:V` works, colon-prefixed `:ret:V` does not
- [ ] Test: write test with both formats
- [ ] Fix: standardize examples to space-separated format
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10c — FQCN `*` prefix requirement not warned
- [ ] Fix: add explicit warning in HowTo section about `*` prefix
- [ ] Documentation only (overlaps with T3.2)
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10d — Descriptor examples don't show JVM format
- [ ] Fix: add JVM descriptor examples and type mapping table to HowTo
- [ ] Documentation only (overlaps with T3.1)
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10e — Empty/short descriptor form equivalence
- [ ] Review: confirm `()`, `(*)`, and omitted all normalize to `(*)*`
- [ ] Test: write tests for each form
- [ ] Fix: clarify in HowTo with explicit normalization table
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10f — `()` normalisation is misleading
- [ ] Review: confirm `*#productElement()` matches `productElement(I)Ljava/lang/Object;`
- [ ] Test: write test demonstrating `()` matches methods with args
- [ ] Fix: add warning in HowTo about `()` matching all overloads
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10g — Quick Example comments use human-readable types
- [ ] Fix: rewrite comments to use JVM descriptor format
- [ ] Documentation only
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10h — `ret:` semicolon inconsistency
- [ ] Review: confirm `ret:Lcom/example/*` (no semicolon) fails
- [ ] Test: write test with and without trailing semicolon
- [ ] Fix: correct examples, add note about mandatory semicolon
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10i — `id:` listed as optional but should be mandatory
- [ ] Review: confirm omitting `id:` produces unreadable logs
- [ ] Fix: change documentation to say `id:` is required
- [ ] Consider: add validation warning when `id:` is missing
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10j — `#` dual role ambiguity
- [ ] Review: test if inline `# comment` after a rule causes problems
- [ ] Test: write test with inline comment on rule line
- [ ] Fix: document recommendation to use dedicated comment lines
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10k — CONSERVATIVE/STANDARD/AGGRESSIVE labels not applied
- [ ] Fix: either apply labels to examples or remove the tiered approach
- [ ] Documentation only
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10l — "dot-form" note contradicts `$` usage
- [ ] Fix: clarify that `$` is required for inner classes/companions
- [ ] Documentation only
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10m — Universal wildcard missing safety warning
- [ ] Fix: add "DO NOT commit" warning to `*#*(*)` example
- [ ] Documentation only
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

#### T3.10n — Scala var setter `_$eq` name mangling not explained
- [ ] Fix: add explanation of source → bytecode name mangling
- [ ] Documentation only
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.11 — §11: Quick reference — JMF rule anatomy
- [ ] Review: verify the anatomy description is complete and accurate
- [ ] Fix: integrate into template HowTo if not already present
- [ ] Documentation only
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.12 — §12: Real bugs found in jmf-rules.txt via audit
- [ ] Review: confirm the two bugs (human-readable descriptors) were already fixed
- [ ] Document: add the `grep` audit command to developer guidelines
- [ ] Verify: run the audit command against current `jmf-rules.template.txt`
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.13 — §13: Scala source name vs bytecode name
- [ ] Review: confirm name-mangling table is accurate
- [ ] Fix: add name-mangling reference to template HowTo
- [ ] Documentation only
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

### T3.14 — Finalize `adoption_feedback/OBSERVATIONS.md` (end of Phase 3)
- [ ] Verify all observations have been recorded with complete fields
- [ ] Add summary section:
  - [ ] Count by classification (code bug / doc bug / missing feature / user guidance / config)
  - [ ] List all new tests added (name + T3.x reference)
  - [ ] List all files changed (file + type of change)
  - [ ] List any open items remaining for Phase 4 (deep review)
- [ ] Review for consistency: no gaps, no duplicate entries, statuses accurate

---

## Phase 4 — Deep Reverse Review

### T4.1 — Independent deep review of JMF solution
- [ ] Review rule parsing (`Rules.load`, `Rules.matches`) for edge cases
- [ ] Review bytecode rewriting for correctness
- [ ] Review CLI argument handling
- [ ] Review sbt plugin integration
- [ ] Review Maven plugin integration
- [ ] Document any new findings in OBSERVATIONS.md (append to existing file)

### T4.2 — Fix newly discovered issues
- [ ] For each new finding: test (red) → fix → test (green) → quality gates
- [ ] Update OBSERVATIONS.md: record what changed, why, how to verify, status

---

## Phase 5 — Template Documentation Rewrite

### T5.1 — Rewrite `jmf-rules.template.txt` HowTo section
- [ ] Fix all 14 sub-points from §10 (T3.10a–T3.10n)
- [ ] Add JVM descriptor type mapping table
- [ ] Add FQCN wildcard prefix requirement
- [ ] Add global rule safety warning with examples
- [ ] Add diagnostic/verification workflow
- [ ] Add name-mangling reference
- [ ] Add rule anatomy quick reference
- [ ] Ensure all examples use correct syntax (space-separated flags, JVM descriptors)
- [ ] Remove or fix CONSERVATIVE/STANDARD/AGGRESSIVE tiering

---

## Phase 6 — Code Review Rounds

### T6.1 — Round 1: Full code review of all changes
- [ ] Review all modified files for correctness
- [ ] Check for regressions
- [ ] Verify test coverage
- [ ] Document findings

### T6.2 — Round 2: Second pass
- [ ] Address Round 1 findings
- [ ] Re-review affected areas
- [ ] Document findings

### T6.3 — Round 3: Third pass
- [ ] Address Round 2 findings
- [ ] Re-review affected areas
- [ ] Document findings

### T6.4 — Round 4: Fourth pass
- [ ] Address Round 3 findings
- [ ] Re-review affected areas
- [ ] Document findings

### T6.5 — Round 5: Fifth pass (minimum final)
- [ ] Address Round 4 findings
- [ ] Re-review all changes
- [ ] If findings exist: continue to Round 6+
- [ ] If zero findings: mark review complete

---

## Completion Criteria

- [ ] All Phase 1–5 tasks completed
- [ ] All tests pass: `sbt +test`
- [ ] Code review rounds completed with zero findings in final round
- [ ] OBSERVATIONS.md is complete, finalized, and accurate
- [ ] `jmf-rules.template.txt` HowTo is rewritten and accurate
- [ ] `.github/copilot-instructions.md` updated (including JMF filtering decision rules)
- [ ] `.github/copilot-review-rules.md` updated
- [ ] `.github/agents/` removed

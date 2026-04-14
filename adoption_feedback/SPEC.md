# Adoption Feedback — Specification

## Context

This spec captures findings from the first real-world adoption of `jacoco-method-filter`
(JMF) in the [balta](https://github.com/AbsaOSS/balta) project. The adopter produced:

- **jmf-rules.txt** — a production rules file with ~380 lines, including new global
  rule candidates and extensive project-specific rules.
- **JMF-NOTES.md** — 14 observations (numbered 1–13, with §10 containing 14 sub-points)
  documenting silent failures, misleading tutorial text, and missing warnings.

Both files live in `adoption_feedback/` and are the primary inputs for this work.

---

## Goals

### G1 — Update local copilot configuration files

Merge applicable improvements from the adoption project's copilot files
(`adoption_feedback/copilot-instructions.md`, `adoption_feedback/copilot-review-rules.md`)
into the local `.github/copilot-instructions.md` and `.github/copilot-review-rules.md`.

**Constraints:**
- Keep the local files tailored to this Scala/sbt project (do not import Python/pytest specifics).
- Preserve existing domain-specific sections (entry points, contract-sensitive outputs, commands).
- Import structural improvements: constraint words (Must/Must not/Prefer/Avoid), output
  discipline, PR body management, commenting rules, and double-check review mode refinements.

**Required addition — JMF filtering decision rules:**
The `.github/copilot-instructions.md` for this repo must contain a dedicated section
documenting the criteria for deciding whether a method belongs in JMF filtering or should
be covered by tests. This is crucial domain knowledge absent from the current file. The
section must address:

- **Filter if:** method is compiler-generated (case class boilerplate, lambda, default
  parameter accessor, value class extension, Iterator mixin forwarder, static companion
  forwarder, implicit wrapper constructor).
- **Do NOT filter if:** method contains any branching logic, validation, data transformation,
  error handling, or calls to more than one non-trivial dependency.
- **Borderline cases:** single-call delegates, trivial negations (`!hasNext`), identity
  accessors (`val` getters) — filter only when the method body is provably a single
  expression with no branching.
- **Verification requirement:** before adding a rule, run `javap -p -verbose` on the
  class to confirm the method body matches the claimed pattern. Never add a rule based
  on source code alone.
- **Global rule override:** if a global rule would filter a method that contains real
  logic, add a project-level `+` include rule to rescue it (e.g., `+*Config$#apply(*)`).

This section must also be reflected in the official project documentation (README or
dedicated doc page) so that adopters encounter it before writing their first rule.

### G2 — Remove `.github/agents/` folder

The agents folder (5 agent definition files) is no longer needed. Remove the entire
`.github/agents/` directory.

### G3 — Identify new global rules from adoption rules file

Compare `adoption_feedback/jmf-rules.txt` globals + "proposal to new globals" section against
the current `jmf-rules.template.txt` GLOBALS RULES section. Candidate new globals:

| Rule pattern | id | Rationale |
|---|---|---|
| `*#productElementName(*)` | `case-prod-element-name` | Case class boilerplate (Scala 2.13+) |
| `*#productElementNames()` | `case-prod-element-names` | Case class boilerplate (Scala 2.13+) |
| `*#$deserializeLambda$(*)` | `scala-deser-lambda` | Lambda serialization helper, compiler-generated |
| `*#hashCode$extension(*)` | `valclass-hashcode-ext` | Value class companion extension bridges |
| `*#equals$extension(*)` | `valclass-equals-ext` | Value class companion extension bridges |
| `*#toString$extension(*)` | `valclass-tostring-ext` | Value class companion extension bridges |
| `*#canEqual$extension(*)` | `valclass-canequal-ext` | Value class companion extension bridges |
| `*#productIterator$extension(*)` | `valclass-proditer-ext` | Value class companion extension bridges |
| `*#productElement$extension(*)` | `valclass-prodelem-ext` | Value class companion extension bridges |
| `*#productArity$extension(*)` | `valclass-prodarity-ext` | Value class companion extension bridges |
| `*#productPrefix$extension(*)` | `valclass-prodprefix-ext` | Value class companion extension bridges |
| `*#copy$extension(*)` | `valclass-copy-ext` | Value class companion extension bridges |
| `*#copy$default$*$extension(*)` | `valclass-copydef-ext` | Value class companion extension bridges |
| `*#andThen(*)` | `fn1-andthen` | Function1 trait mixin delegates |
| `*#compose(*)` | `fn1-compose` | Function1 trait mixin delegates |
| `*#*$default$*(*)` | `gen-defaults` | Default parameter accessors |

**Key finding:** The adopter commented out `*#apply(*)` (id:case-apply) because it filtered
a method that contained real application logic. The current template includes this as an
active global rule — this must be reviewed and documented as a warning.

### G4 — Update documentation: global rule safety warning

Add a warning to the template and/or HowTo section that users must verify global rules do
not filter out methods with real business logic. Use `*#apply(*)` as the primary example —
the adopter found that companion `apply` methods can contain validation/factory logic that
should remain covered.

**Examples of globals that may need project-level overrides:**
- `*#apply(*)` — commented out by adopter (real factory logic)
- `*#copy$default$*(*)` — subsumed by `gen-defaults` in adopter's file
- `*#name()` / `*#groups()` / `*#optionalAttributes()` — may collide with domain methods

### G5 — Review each observation from JMF-NOTES.md

Each of the 14 chapters (§1–§13, with §10 having sub-points 10a–10n) represents a real
problem found during adoption. Each must be:

1. **Reviewed for validity** — confirm the observation is correct against current JMF code.
2. **Test created** — write a test that reproduces the issue (red).
3. **Red-tested** — confirm the test fails as expected.
4. **Fixed** — implement the fix in JMF code or documentation.
5. **Tested** — confirm the test passes (green).
6. **Quality gates confirmed** — `sbt +test` passes.

### G6 — Create final observation report

Produce `adoption_feedback/OBSERVATIONS.md` as a living document. It is started at the
beginning of Phase 3 and updated incrementally after each observation is resolved. At the
end of Phase 3 it is finalized with a summary section.

The report must capture **every change made**, not only observation fixes, so a human
reviewer can understand the full scope of work without reading diffs. Each entry must
include:

- **Observation / change title**
- **Classification:** Code bug | Documentation bug | Missing feature | User guidance |
  Configuration cleanup | Global rule addition | Deep review finding
- **Source:** which chapter of JMF-NOTES.md triggered it, or "deep review", or "global
  rules analysis", or "copilot update"
- **What changed:** files and lines affected
- **Why the change was made:** root cause or rationale (not just "fixed bug X")
- **How to verify:** test name, manual step, or sbt command
- **Resolution status:** Open | Fixed | Doc-only | Won't fix (with reason)

The summary section at the end of Phase 3 provides:
- Counts by classification
- List of all new tests added
- List of all files changed
- Any remaining open items carried into Phase 5 (deep review)

### G7 — Deep reverse review of the solution

After addressing all adoption observations, perform an independent deep review of JMF to
identify additional issues not found during adoption. Document and fix them using the same
red-green cycle as G5.

### G8 — Improve `jmf-rules.template.txt` documentation

Rewrite the HowTo/comment section in `jmf-rules.template.txt` to be more readable and
accurate. Problems to address (derived from JMF-NOTES.md §10a–§10n):

- §10a: Colon-prefix flag syntax in Quick Examples is wrong — fix to space-separated
- §10b: `ret:` predicate syntax inconsistent — standardize to space-separated
- §10c: Missing warning about FQCN `*` prefix requirement
- §10d: Descriptor examples don't demonstrate JVM-format requirement
- §10e: Empty/short descriptor equivalence needs clarification
- §10f: `()` normalisation to `(*)*` is undocumented/misleading
- §10g: Quick Example comments use human-readable types (contradicts formal syntax)
- §10h: `ret:` object type semicolon inconsistency
- §10i: `id:` appears optional but should be mandatory
- §10j: `#` dual role (comment marker vs FQCN separator) is ambiguous
- §10k: CONSERVATIVE/STANDARD/AGGRESSIVE labels not applied to examples
- §10l: "Always use dot-form" contradicts necessary `$` usage
- §10m: `*#*(*)` universal wildcard missing safety warning
- §10n: Scala var setter `_$eq` name mangling not explained

### G9 — Code review rounds

Perform 5 rounds of code review on all changes. Continue beyond round 5 if issues are
still found. Stop only when a full review round produces zero findings.

---

## Out of Scope

- No code implementation in this phase (analysis only).
- No changes to build configuration or CI workflows.
- No Scala version upgrades or dependency changes.

---

## Inputs

| File | Purpose |
|---|---|
| `adoption_feedback/jmf-rules.txt` | Adopter's production rules file |
| `adoption_feedback/JMF-NOTES.md` | Adopter's 14 observations |
| `adoption_feedback/copilot-instructions.md` | Adopter's copilot instructions (source of ideas) |
| `adoption_feedback/copilot-review-rules.md` | Adopter's review rules (source of ideas) |
| `.github/copilot-instructions.md` | Local copilot instructions (to be updated) |
| `.github/copilot-review-rules.md` | Local review rules (to be updated) |
| `jmf-rules.template.txt` | Template rules file (to be improved) |
| `.github/agents/` | Agents folder (to be removed) |

## Outputs

| Deliverable | Description |
|---|---|
| Updated `.github/copilot-instructions.md` | Merged improvements from adoption |
| Updated `.github/copilot-review-rules.md` | Merged improvements from adoption |
| Removal of `.github/agents/` | Cleaned up obsolete agent definitions |
| Updated `jmf-rules.template.txt` | New globals + improved HowTo docs |
| Tests for each observation | Red-green tests confirming fixes |
| Code/doc fixes for each observation | Fixes for all validated observations |
| `adoption_feedback/OBSERVATIONS.md` | Living report: started in Phase 3, finalized at end of Phase 3, extended in Phase 5 |
| Deep review findings | Additional issues found via independent review, appended to OBSERVATIONS.md |

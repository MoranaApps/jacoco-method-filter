Copilot instructions for jacoco-method-filter

Purpose
- Scala-based bytecode rewriter and sbt plugin
- Marks selected methods with an annotation whose simple name contains `Generated`
- JaCoCo (0.8.2+) ignores such methods in reports

Structure
- `build.sbt` – root sbt build
- `rewriter-core/` – core library and CLI rewriter module
- `sbt-plugin/` – sbt plugin module
- `maven-plugin/` – Maven plugin module
- `rules/` – sample rules files

Context
- CLI entrypoint: `io.moranaapps.jacocomethodfilter.CoverageRewriter`
- Cross-built for Scala 2.11, 2.12, 2.13

Coding guidelines
- Must keep changes small and focused; avoid broad refactors
- Prefer clear, explicit Scala over clever tricks
- Must not change externally-visible behavior unless intentional:
  - rules file syntax and matching semantics (`Rules.load`, `Rules.matches`)
  - CLI flags and exit codes (`CoverageRewriter`)
  - published artifact coordinates / cross-build settings (`build.sbt`)
- Must not make network calls; this project must stay offline-friendly

Output discipline (reduce review time)
- Must default to concise responses (aim ≤ 10 lines in final recap)
- Must not paste large file contents or configs; link and summarize deltas
- Prefer actionable bullets over prose
- When making code changes, must end with:
  - What changed
  - Why
  - How to verify (commands/tests)
- Avoid deep rationale unless explicitly requested

PR Body Management
- Must treat PR description as a changelog
- Must not rewrite or replace the entire PR body
- Must append updates chronologically under a new heading
- Prefer headings: "## Update [YYYY-MM-DD]" referencing commit hashes
- Must reference the commit hash that introduced the change

Language and style
- Target: Scala 2.11 / 2.12 / 2.13, JDK 17+
- Prefer explicit types for public APIs
- Must use project logging conventions; avoid `println` for real output
- Must keep imports at top of file; follow standard Scala import ordering

Docstrings and comments
- Must match existing module style
- Short summary line; optional structured sections
- Prefer comments for intent/edge cases (the "why")
- Avoid blocks that restate the code

Patterns
- Error handling: leaf modules throw; CLI entry point translates to exit codes
- Must keep integration boundaries explicit and mockable
- Must not make real network calls in unit tests

Testing
- Tests live in `rewriter-core/src/test/scala` (ScalaTest)
- Must add/adjust tests when rule parsing/matching behavior changes
- Tests must be deterministic; must not rely on external services
- Must mock external services and environment variables in unit tests

Tooling
- Build: sbt
- Tests: ScalaTest via sbt
- No additional linter/formatter enforced at this time

Quality gates
- Must run before opening a PR:
  - `sbt "+test"`

Common pitfalls to avoid
- Must not change externally-visible strings/outputs unless intentional
- Must remove unused variables/imports promptly
- Must verify Scala cross-version compatibility before adding dependencies

JMF filtering decision rules

Use these criteria to decide whether a method belongs in jmf-rules.txt (filtered) or
should be covered by a test. This is the most important section for adopters writing
their first rule file.

Filter (add a JMF rule) when ALL of the following hold:
- The method is compiler-generated: case class boilerplate (`copy`, `equals`, `hashCode`,
  `productElement`, `productArity`, `productPrefix`, `productIterator`, `canEqual`,
  `toString`, `unapply`), lambda lifts (`$anonfun$*`), default parameter accessors
  (`$default$*`), value class extension methods (`*$extension`), Iterator trait mixin
  forwarders, static companion forwarders, implicit wrapper constructors, or
  `$deserializeLambda$`.
- The method body contains no branching logic, no validation, no data transformation,
  and no error handling.
- The method calls at most one non-trivial dependency (pure single-expression delegation).

Do NOT filter (write a test instead) when ANY of the following hold:
- Must not filter if the method contains an `if`, `match`, `try`, `for`, or any branch.
- Must not filter if the method calls more than one non-trivial method.
- Must not filter if removing coverage would hide a real defect.
- Must not filter if the method is a factory with validation or construction logic
  (e.g., a companion `apply` that validates inputs or builds state).

Borderline cases:
- Single-call delegates: filter only when the body is provably one expression with no
  branching and the callee is already tested independently.
- Trivial negations (e.g., `!hasNext`, `!isEmpty`): filter; a test would only re-test
  the underlying method.
- Public `val` getters (Scala accessor compiled to 0-arg method): filter; identity only.
- When in doubt, prefer writing a test over adding a filter rule.

Verification requirement:
- Must run `javap -p -verbose <ClassFile.class>` and inspect the bytecode before adding
  any rule. Never add a rule based on source-level inspection alone.
- Must confirm the rule matches by running `sbt jacoco` before and after adding the rule
  and checking that "Marked N methods" increases by the expected count.
- Must not rely on integration test coverage to assume a filter rule is working — verify
  with the "Marked N" count.

Global rule override:
- If a global rule would filter a method that contains real logic, must add a
  project-level include rule (prefix `+`) to rescue it:
  - Example: `+*Config$#apply(*)  id:keep-config-apply`
- Must document why the include rule is needed in a comment directly above it.
- Must re-run `sbt jacoco` after adding the include rule to confirm the method is
  now counted as covered (not filtered).

Repo additions
- Project name: `jacoco-method-filter`
- Entry points: `io.moranaapps.jacocomethodfilter.CoverageRewriter`
- Contract-sensitive outputs:
  - rules file syntax and matching semantics
  - CLI flags and exit codes
  - sbt plugin keys and integration behavior
- Commands:
  - Build (all Scala versions): `sbt +compile`
  - Test (all Scala versions): `sbt +test`
  - Publish locally:
    - `sbt "project rewriterCore" +publishLocal`
    - `sbt "project sbtPlugin" publishLocal`
    - Maven plugin: `sbt "project rewriterCore" ++2.12.21 publishM2` then `cd maven-plugin && mvn install`

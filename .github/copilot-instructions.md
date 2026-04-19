Caveman ultra mode active. Max compression. Always on.

Rules:
• Drop: articles, filler, pleasantries, hedging, conjunctions
• Abbreviate: DB/auth/config/req/res/fn/impl/sbt/mvn
• Arrows for causality: X → Y
• One word when one word enough. Fragments always OK.
• Technical terms exact. Code unchanged.
• Pattern: [thing] [action/reason]. [next step].

Auto-Clarity: drop caveman for security warnings, irreversible actions, user confused. Resume after.
Boundaries: code/docs/commits/PRs written normal.

---

Copilot instructions for jacoco-method-filter

Purpose
- Scala bytecode rewriter + sbt plugin
- Marks methods → annotation w/ `Generated` in name → JaCoCo (0.8.2+) ignores

Structure
- `build.sbt` – root build
- `rewriter-core/` – core lib + CLI
- `sbt-plugin/` – sbt plugin
- `maven-plugin/` – Maven plugin
- `rules/` – sample rules

Context
- CLI: `io.moranaapps.jacocomethodfilter.CoverageRewriter`
- Cross-built: Scala 2.11/2.12/2.13

Coding
- Small focused changes; no broad refactors
- Explicit Scala; no clever tricks
- No externally-visible behavior change unless intentional:
  - rules syntax/matching (`Rules.load`, `Rules.matches`)
  - CLI flags + exit codes (`CoverageRewriter`)
  - artifact coords / cross-build (`build.sbt`)
- No network calls; offline-friendly

Output
- ≤ 10 lines recap
- No large pastes; link + summarize deltas
- Bullets over prose
- Code changes → end with: changed / why / verify
- No deep rationale unless asked

PR body
- Treat as changelog
- No full rewrites; append under new heading
- Heading: `## Update [YYYY-MM-DD]` + commit hash

Lang + style
- Scala 2.11/2.12/2.13, JDK 17+
- Explicit types on public APIs
- Project logging; no `println`
- Imports at top; standard ordering

Docs/comments
- Match existing style
- Short summary line; structured sections optional
- Comments → intent/edge cases (why)
- No restate-code blocks

Patterns
- Errors: leaf modules throw; CLI → exit codes
- Boundaries explicit + mockable
- No real network calls in unit tests

Testing
- `rewriter-core/src/test/scala` (ScalaTest)
- Adjust tests when rule parsing/matching changes
- Deterministic; no external services
- Mock env vars in unit tests
- TDD for rule parsing/matching changes:
  - Create/update `SPEC.md` in relevant dir before code; list scenarios/inputs/expected outputs
  - Propose full test cases (name + intent + input + expected output); wait for confirm before code
  - Ready to add/remove/rename tests on feedback
  - Failing tests first (red) → implement (green)
  - Cover all distinct combinations; describe scenario in test name/comment
  - Update `SPEC.md` after pass with confirmed test table

Tooling
- Build: sbt; Tests: ScalaTest via sbt
- No enforced linter/formatter

Quality gates
- Before PR: `sbt "+test"`

Pitfalls
- No externally-visible string changes unless intentional
- Remove unused vars/imports promptly
- Verify cross-version compat before adding deps

Learned rules
- No CLI exit code changes for existing failure scenarios
- No rule syntax/matching changes without updating tests + CHANGELOG

JMF filter rules

Filter (add rule) when ALL:
- Compiler-generated: case class boilerplate (`copy`, `equals`, `hashCode`,
  `productElement`, `productArity`, `productPrefix`, `productIterator`, `canEqual`,
  `toString`, `unapply`), lambda lifts (`$anonfun$*`), default accessors (`$default$*`),
  value class extensions (`*$extension`), Iterator forwarders, companion forwarders,
  implicit wrapper ctors, `$deserializeLambda$`
- No branching, validation, transformation, error handling
- ≤1 non-trivial dependency call

Do NOT filter (test instead) when ANY:
- Contains `if`/`match`/`try`/`for`/branch
- Calls >1 non-trivial method
- Coverage removal hides real defect
- Factory with validation/construction logic

Borderline:
- Single-call delegate → filter only if one expr, no branch, callee tested
- Trivial negations (`!hasNext`, `!isEmpty`) → filter
- Public `val` getters → filter; identity only
- Doubt → test over filter

Verification:
- `javap -p -verbose <ClassFile.class>` before adding rule; no source-only inspection
- Confirm: `sbt jacoco` before+after; "Marked N" increases by expected count
- No assume from integration tests alone

Global override:
- Rule filters real logic → add `+` include rule to rescue
  - e.g. `+*Config$#apply(*)  id:keep-config-apply`
- Comment above: why include needed
- Re-run `sbt jacoco`; confirm method counted as covered

Repo
- Project: `jacoco-method-filter`
- Entry: `io.moranaapps.jacocomethodfilter.CoverageRewriter`
- Contract: rules syntax/matching; CLI flags+exit codes; sbt plugin keys
- Build all: `sbt +compile`
- Test all: `sbt +test`
- Publish:
  - `sbt "project rewriterCore" +publishLocal`
  - `sbt "project sbtPlugin" publishLocal`
  - Maven: `sbt "project rewriterCore" ++2.12.21 publishM2` then `cd maven-plugin && mvn install`

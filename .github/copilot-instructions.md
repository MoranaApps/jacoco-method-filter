Copilot instructions for jacoco-method-filter

Purpose
- Scala-based bytecode rewriter and sbt plugin
- Marks selected methods with an annotation whose simple name contains `Generated`
- JaCoCo (0.8.2+) ignores such methods in reports

Structure
- `build.sbt` – root sbt build
- `rewriter-core/` – core library and CLI rewriter module
- `sbt-plugin/` – sbt plugin module
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
- Avoid network calls; this project must stay offline-friendly

Output discipline (reduce review time)
- Default to concise responses (aim ≤ 10 lines in final recap)
- Do not restate large file contents; summarize deltas
- End code changes with: what changed, why, how to verify

PR Body Management
- Treat PR description as a changelog; append new changes at the end
- Must not rewrite/replace entire PR body; always append updates
- Keep original description at top; add updates chronologically below
- Use headings like `## Update [YYYY-MM-DD]` referencing commit hashes

Language and style
- Target: Scala 2.11 / 2.12 / 2.13, JDK 17+
- Prefer explicit types for public APIs
- Use project logging conventions; avoid `println` for real output
- Imports at top of file; follow standard Scala import ordering

Docstrings and comments
- Match existing module style
- Short summary line; optional structured sections
- Comment only for intent/edge cases (the "why")

Patterns
- Error handling: leaf modules throw; CLI entry point translates to exit codes
- Keep integration boundaries explicit and mockable
- Avoid real network calls in unit tests

Testing
- Tests live in `rewriter-core/src/test/scala` (ScalaTest)
- Must add/adjust tests when rule parsing/matching behavior changes
- Tests must be deterministic; must not rely on external services
- Mock external services and environment variables in unit tests

Tooling
- Build: sbt
- Tests: ScalaTest via sbt
- No additional linter/formatter enforced at this time

Quality gates
- Run before opening a PR:
  - `sbt "+test"`

Common pitfalls to avoid
- Avoid changing externally-visible strings/outputs unless intentional
- Remove unused variables/imports promptly
- Verify Scala cross-version compatibility before adding dependencies

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

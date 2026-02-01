Copilot instructions for jacoco-method-filter

Purpose
This repo is a Scala-based bytecode rewriter and sbt plugin that marks selected methods with an annotation whose
simple name contains `Generated`, so JaCoCo (0.8.2+) can ignore them in reports.

Context
- SBT build at repo root: `build.sbt`.
- Core library / CLI rewriter module: `rewriter-core/`.
	- CLI entrypoint: `io.moranaapps.jacocomethodfilter.CoverageRewriter`.
- sbt plugin module: `sbt-plugin/`.
- Rules/examples and integration docs:
	- `integration/` and `rules/`.

Coding guidelines
- Keep changes small and focused; avoid broad refactors.
- Prefer clear, explicit Scala over cleverness.
- Avoid changing externally-visible behavior unless intended:
	- rules file syntax and matching semantics (`Rules.load`, `Rules.matches`)
	- CLI flags and exit codes (`CoverageRewriter`)
	- published artifact coordinates / cross-build settings (`build.sbt`)
- Do not add network calls unless explicitly required; this project should stay offline-friendly.

Testing
- Tests live in `rewriter-core/src/test/scala` (ScalaTest) and are run via sbt.
- Add/adjust tests when rule parsing/matching behavior changes.
- Tests must be deterministic and not rely on external services.

Pre-commit Quality Gates
Before opening a PR (or claiming work is done), run:
- `sbt "+test"`

Common commands
- Build (all Scala versions): `sbt +compile`
- Test (all Scala versions): `sbt +test`
- Publish locally:
	- `sbt "project rewriterCore" +publishLocal`
	- `sbt "project sbtPlugin" publishLocal`

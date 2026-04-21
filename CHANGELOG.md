# Changelog

All notable user-facing changes to **jacoco-method-filter** are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project uses [Semantic Versioning](https://semver.org/).

## [2.1.0]

### Added

- **Unmatched rules report** in `--verify` mode — after scanning, prints an `UNMATCHED RULES`
  section listing every rule that matched zero methods in the scanned class directory.
  Covers both exclusion and include rules.
- **`--error-on-unmatched` CLI flag** — when combined with `--verify`, exits with code `1` if
  any rules are unmatched. Enables CI enforcement of rule hygiene.
- **`forward-compat` rule marker** — annotate a rule with `forward-compat` to exempt it from
  the unmatched-rule warning. Use for rules targeting classes present in production builds
  but absent from the current module's test classpath. Forward-compat rules are labelled
  `(forward-compat)` in the `--verify` active-rules listing for easy identification.
- Report formats (txt, json, csv) include unmatched rule information: txt/printReport add an
  `UNMATCHED RULES` block, JSON adds an `unmatchedRules` array, CSV adds `UNMATCHED_RULE` rows.

## [2.0.1] — 2026-02-14

### Fixed

- Release workflow now cross-publishes core library for **all three** Scala versions (2.11, 2.12, 2.13).
 Previously only the 2.12 artifact was published to Maven Central.

## [2.0.0] — 2025-02-14

### Added

- **Include rules** — rescue specific methods from broad exclusions (`+include` mode).
- **Global and local rules** — load shared team rules from a URL or path (`--global-rules` / `--local-rules`).
- **Verify mode** — preview which methods would be excluded without modifying classes (`--verify`).
- **Maven plugin goals** — `rewrite`, `report`, `verify`, and `init-rules`.
- **sbt `jmfInitRules` task** — bootstrap a default `jmf-rules.txt` with Scala-friendly templates.
- **sbt `jmfVerify` task** — on-demand scan showing rule impact.
- **Fat JAR packaging** — core library ships ASM shaded (`jmf.shaded.asm`) to avoid classpath conflicts.
- Integration test suite for JaCoCo compatibility (0.8.7 – 0.8.14).

### Changed

- Maven plugin parameters aligned: `globalRules` / `localRules` replace the legacy `rulesFile` parameter.
- `localRules` defaults to `${project.basedir}/jmf-rules.txt` (same behavior, consistent naming).
- Collection converters replaced with a cross-version compat shim (no more `JavaConverters`
 deprecation warnings on Scala 2.13).
- Core library cross-published for Scala 2.11, 2.12, and 2.13 (fat JAR with shaded ASM).
- Standardized output directories across sbt and Maven plugins.
- Updated ASM to **9.7.1** (supports JDK 24 class files; was 9.6 / JDK 22).
- Integration tests for sbt examples use a dedicated CI fixture instead of `sed`-based uncommenting.

### Removed

- `jmf.rulesFile` Maven property removed from `rewrite`, `verify`, and `init-rules` goals (use `jmf.localRules`).

## [1.1.0] — 2024-09-14

### Added

- Scala 2.11 cross-build support.

### Changed

- Updated ASM to 9.6, scopt to 3.7.1.

## [1.0.0] — 2024-08-24

### Added

- Versioned rules file format (`[jmf:1.0.0]` header).
- Maven integration support (CLI-based workflow).
- Quiet-exit mode for builds without a rules file.

### Changed

- Renamed from internal prototype to `jacoco-method-filter`.

## [0.1.7] — 2024-08-10

### Added

- Sample rules file and dev-run helper script.
- GitHub Actions CI setup.

## [0.1.0] — 2024-07-28

### Added

- Initial release: sbt plugin with bytecode rewriter for JaCoCo method-level coverage filtering.
- Rule syntax: `<class>#<method>(<descriptor>)` with glob patterns.
- `@CoverageGenerated` annotation injection.

[2.1.0]: https://github.com/MoranaApps/jacoco-method-filter/compare/v2.0.0...HEAD
[2.0.1]: https://github.com/MoranaApps/jacoco-method-filter/compare/v2.0.0...HEAD
[2.0.0]: https://github.com/MoranaApps/jacoco-method-filter/compare/v1.1.0...v2.0.0
[1.1.0]: https://github.com/MoranaApps/jacoco-method-filter/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/MoranaApps/jacoco-method-filter/compare/v0.1.7...v1.0.0
[0.1.7]: https://github.com/MoranaApps/jacoco-method-filter/compare/0.1.0...v0.1.7
[0.1.0]: https://github.com/MoranaApps/jacoco-method-filter/releases/tag/0.1.0

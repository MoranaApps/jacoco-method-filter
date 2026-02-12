# Integration Tests

Shell-based integration tests that verify the published sbt and Maven plugins
work end-to-end with the example projects.

## Prerequisites

- Java 8+ (CI uses 21)
- sbt (for sbt plugin tests)
- Maven 3.6+ (for Maven plugin tests)

## Quick Start

Run all tests (publishes plugins locally first):

```bash
./integration-tests/run-all.sh
```

Skip the local-publish step (if you already published):

```bash
./integration-tests/run-all.sh --skip-publish
```

Run a single test:

```bash
bash integration-tests/test-sbt-init-rules.sh
```

## Test Matrix

| Script | What it verifies |
|--------|------------------|
| `test-sbt-init-rules.sh` | `sbt jmfInitRules` creates `jmf-rules.txt` from scratch |
| `test-mvn-init-rules.sh` | `mvn jacoco-method-filter:init-rules` creates `jmf-rules.txt` |
| `test-sbt-verify.sh` | `sbt jmfVerify` shows methods that would be filtered (read-only scan) |
| `test-mvn-verify.sh` | `mvn jacoco-method-filter:verify` shows methods that would be filtered |
| `test-cli-verify.sh` | CLI `--verify` mode shows methods that would be filtered |
| `test-cli-verify-suggest.sh` | CLI `--verify --verify-suggest-includes` emits heuristic rescue-rule suggestions |
| `test-sbt-basic.sh` | `examples/sbt-basic` passes tests without filtering, then with filtering + report generation |
| `test-maven-basic.sh` | `examples/maven-basic` (Java) passes tests without and with `-Pcode-coverage` |
| `test-maven-scala.sh` | `examples/maven-scala` (Scala) passes tests without and with `-Pcode-coverage` |
| `test-jacoco-compat.sh` | End-to-end JaCoCo compatibility — generates coverage, rewrites classes, verifies filtering works across JaCoCo versions |

## How It Works

1. `run-all.sh` publishes `rewriter-core`, `sbt-plugin`, and `maven-plugin` to the local repository
2. Each `test-*.sh` script copies an example project to a temp directory
3. The test runs the build with and without coverage filtering
4. Assertions check that expected outputs (rules files, reports, filtered classes) exist
5. Temp directories are cleaned up automatically

### sbt fixture

The `examples/sbt-basic/` project ships with the plugin **commented out** so that
cloning the repo does not trigger dependency resolution. Rather than using fragile
`sed` edits to uncomment the plugin at test time, the sbt integration tests copy
a dedicated CI fixture (`fixtures/sbt-basic/`) that has the plugin already enabled,
then overlay the source and rules files from the example.

## CI Integration

The `integration` job in `.github/workflows/ci.yml` runs `./integration-tests/run-all.sh`,
which publishes both the sbt plugin and Maven plugin locally (no remote resolution) and then
exercises every `test-*.sh` script. This covers the full sbt plugin lifecycle end-to-end:

- `publishLocal` → `test-sbt-init-rules.sh` (creates rules file)
- `publishLocal` → `test-sbt-verify.sh` (read-only scan)
- `publishLocal` → `test-sbt-basic.sh` (compile → rewrite → test → report)

All tests are deterministic: they copy fixtures into temp directories, rely only on
locally-published artifacts, and clean up on exit.

Add to your CI workflow:

```yaml
- name: Integration tests
  run: ./integration-tests/run-all.sh
```

Or to skip the publish step when it's done in a prior step:

```yaml
- name: Publish locally
  run: |
    sbt "project rewriterCore" publishLocal
    sbt "project sbtPlugin" publishLocal
    cd maven-plugin && mvn -B install -DskipTests

- name: Integration tests
  run: ./integration-tests/run-all.sh --skip-publish
```

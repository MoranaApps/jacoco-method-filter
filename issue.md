## Summary

Lower the baseline versions the project builds and releases against so that
downstream consumers on slightly older toolchains are not forced to upgrade.

## Motivation

- **Scala 2.13.16** is currently the 2.13 cross-build target. Requiring the
  newest patch release is unnecessary — the codebase does not use any
  2.13.14+ specific features. Consumers pinned to an earlier 2.13 patch
  (e.g. 2.13.13) should be able to use the published artifacts.
- **Python 3.14** is used in the release workflows only to run GitHub
  Actions (`AbsaOSS/generate-release-notes`, `release-notes-presence-check`).
  Pinning to a bleeding-edge interpreter adds churn with no benefit; a widely
  available LTS-era version is more stable for CI.

## Proposed change

| Item | From | To |
| --- | --- | --- |
| `crossScalaVersions` (2.13 entry) | `2.13.16` | `2.13.13` |
| Release workflow Python | `3.14` | `3.11` |
| PR release-notes check Python | `3.13` | `3.11` |

## Acceptance criteria

- Core library cross-builds and publishes for Scala 2.11 / 2.12 / 2.13.13.
- Release draft and PR release-notes workflows run green on Python 3.11.
- No source changes required; behaviour is unchanged.

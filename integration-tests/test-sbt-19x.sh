#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Test: sbt 1.9.x compatibility (regression guard for issue #76)
#
# v2.4.0 of the plugin used the 3-arg `Command.process(String, State, onParseError)`
# overload, which only exists in sbt >= 1.10.0. On sbt 1.9.x, `jacocoReportAll`
# (and `jacocoCleanAll`) failed with:
#   java.lang.NoSuchMethodError: sbt.Command$.process(...)
#
# This test pins a project to sbt 1.9.9 and runs the full jacoco flow; the
# `jacocoReportAll` step is what invokes `Command.process`, so a regression to a
# newer-than-1.9 sbt API makes `run_cmd` fail here.
#
# Prerequisite: sbt plugin published locally.
# ---------------------------------------------------------------------------
source "$(dirname "$0")/helpers.sh"

TEST_NAME="sbt-19x-compat"
info "Running: $TEST_NAME"

# Self-contained fixture (own src + rules), pinned to sbt.version=1.9.9.
cp -R "$REPO_ROOT/integration-tests/fixtures/sbt-19x" "$WORK_DIR/project"
cd "$WORK_DIR/project"

# ── 1. Confirm the launcher really uses sbt 1.9.x ──────────────────────────
SBT_VERSION_OUT="$(sbt -Dsbt.supershell=false --no-colors sbtVersion 2>&1 | tail -n 5)"
echo "$SBT_VERSION_OUT"
echo "$SBT_VERSION_OUT" | grep -Eq '1\.9\.[0-9]+' \
  || fail "$TEST_NAME — expected sbt 1.9.x, got:\n$SBT_VERSION_OUT"

pass "$TEST_NAME — running on sbt 1.9.x"

# ── 2. Plain test (no filtering) ──────────────────────────────────────────
run_cmd "$TEST_NAME — sbt clean test (no filtering)" sbt clean test

pass "$TEST_NAME — tests pass without filtering"

# ── 3. Full jacoco flow (jacocoReportAll invokes Command.process) ─────────
# The critical step: it must NOT fail with NoSuchMethodError on sbt 1.9.x.
run_cmd "$TEST_NAME — sbt jacoco (with filtering on sbt 1.9.x)" sbt jacoco

REPORT_DIR="target/scala-2.12/jacoco-report"
assert_dir_not_empty "$REPORT_DIR" \
  "$TEST_NAME — JaCoCo report directory exists and is not empty"

assert_file_exists "$REPORT_DIR/index.html" \
  "$TEST_NAME — HTML report generated"

assert_file_exists "$REPORT_DIR/jacoco.xml" \
  "$TEST_NAME — XML report generated"

assert_file_exists "$REPORT_DIR/jacoco.csv" \
  "$TEST_NAME — CSV report generated"

pass "$TEST_NAME — coverage with filtering on sbt 1.9.x"

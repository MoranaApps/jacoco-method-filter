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

# ── 1. Plain test (no filtering) — also loads the plugin on sbt 1.9.x ──────
run_cmd "$TEST_NAME — sbt clean test (no filtering)" sbt clean test

# The sbt launcher honours project/build.properties; assert it really is 1.9.x
# so this test cannot silently start passing on a newer sbt.
assert_file_contains "$LAST_CMD_LOG" "welcome to sbt 1.9" \
  "$TEST_NAME — launcher is sbt 1.9.x"

pass "$TEST_NAME — tests pass without filtering on sbt 1.9.x"

# ── 2. Full jacoco flow (jacocoReportAll invokes Command.process) ─────────
# The critical step: it must NOT fail with NoSuchMethodError on sbt 1.9.x.
run_cmd "$TEST_NAME — sbt jacoco (with filtering on sbt 1.9.x)" sbt jacoco

assert_file_contains "$LAST_CMD_LOG" "welcome to sbt 1.9" \
  "$TEST_NAME — jacoco flow ran on sbt 1.9.x"

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

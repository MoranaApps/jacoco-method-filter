#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Test: examples/sbt-basic — tests pass WITHOUT filtering, then WITH filtering.
#
# Prerequisite: sbt plugin published locally.
# ---------------------------------------------------------------------------
source "$(dirname "$0")/helpers.sh"

TEST_NAME="sbt-basic-example"
info "Running: $TEST_NAME"

# Use the CI fixture (plugin already enabled) + overlay source/rules from the example.
cp -R "$REPO_ROOT/integration-tests/fixtures/sbt-basic" "$WORK_DIR/project"
cp -R "$REPO_ROOT/examples/sbt-basic/src" "$WORK_DIR/project/src"
cp    "$REPO_ROOT/examples/sbt-basic/jmf-rules.txt" "$WORK_DIR/project/"
cd "$WORK_DIR/project"

# ── 1. Without filtering (plain test) ──────────────────────────────────────
run_cmd "$TEST_NAME — sbt clean test (no filtering)" sbt clean test

pass "$TEST_NAME — tests pass without filtering"

# ── 2. With filtering (jacoco alias) ───────────────────────────────────────
run_cmd "$TEST_NAME — sbt jacoco (with filtering)" sbt jacoco

# Verify reports were generated (crossTarget = target/scala-<ver>)
REPORT_DIR="target/scala-2.12/jacoco-report"
assert_dir_not_empty "$REPORT_DIR" \
  "$TEST_NAME — JaCoCo report directory exists and is not empty"

assert_file_exists "$REPORT_DIR/index.html" \
  "$TEST_NAME — HTML report generated"

assert_file_exists "$REPORT_DIR/jacoco.xml" \
  "$TEST_NAME — XML report generated"

assert_file_exists "$REPORT_DIR/jacoco.csv" \
  "$TEST_NAME — CSV report generated"

pass "$TEST_NAME — coverage with filtering"

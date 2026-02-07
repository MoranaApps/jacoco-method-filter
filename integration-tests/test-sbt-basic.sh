#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Test: examples/sbt-basic — tests pass WITHOUT filtering, then WITH filtering.
#
# Prerequisite: sbt plugin published locally.
# ---------------------------------------------------------------------------
source "$(dirname "$0")/helpers.sh"

TEST_NAME="sbt-basic-example"
info "Running: $TEST_NAME"

cp -R "$REPO_ROOT/examples/sbt-basic" "$WORK_DIR/project"
cd "$WORK_DIR/project"

# ── 1. Without filtering (plain test) ──────────────────────────────────────
info "$TEST_NAME — running tests WITHOUT coverage filtering"
sbt clean test

pass "$TEST_NAME — tests pass without filtering"

# ── 2. With filtering (jacoco alias) ───────────────────────────────────────
info "$TEST_NAME — running tests WITH coverage filtering (sbt jacoco)"
sbt jacoco

# Verify reports were generated
assert_dir_not_empty "target/jacoco/report" \
  "$TEST_NAME — JaCoCo report directory exists and is not empty"

assert_file_exists "target/jacoco/report/index.html" \
  "$TEST_NAME — HTML report generated"

assert_file_exists "target/jacoco/report/jacoco.xml" \
  "$TEST_NAME — XML report generated"

assert_file_exists "target/jacoco/report/jacoco.csv" \
  "$TEST_NAME — CSV report generated"

pass "$TEST_NAME — coverage with filtering"

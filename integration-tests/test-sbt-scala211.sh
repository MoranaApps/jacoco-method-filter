#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Test: Scala 2.11 cross-build compatibility
#
# Verifies that the plugin works correctly with Scala 2.11 projects,
# ensuring no NoSuchMethodError occurs due to Scala version conflicts.
#
# Prerequisite: sbt plugin published locally.
# ---------------------------------------------------------------------------
source "$(dirname "$0")/helpers.sh"

TEST_NAME="sbt-scala211-crossbuild"
info "Running: $TEST_NAME"

# Use the Scala 2.11 fixture
cp -R "$REPO_ROOT/integration-tests/fixtures/sbt-scala211" "$WORK_DIR/project"
cd "$WORK_DIR/project"

# ── 1. Clean test to verify basic functionality ────────────────────────────
run_cmd "$TEST_NAME — sbt clean test (no filtering)" sbt clean test

pass "$TEST_NAME — tests pass without filtering"

# ── 2. With filtering (jacoco alias) ───────────────────────────────────────
# This is the critical test - it should NOT fail with NoSuchMethodError
run_cmd "$TEST_NAME — sbt jacoco (with filtering on Scala 2.11)" sbt jacoco

# Verify reports were generated
assert_dir_not_empty "target/jacoco-report" \
  "$TEST_NAME — JaCoCo report directory exists and is not empty"

assert_file_exists "target/jacoco-report/index.html" \
  "$TEST_NAME — HTML report generated"

assert_file_exists "target/jacoco-report/jacoco.xml" \
  "$TEST_NAME — XML report generated"

assert_file_exists "target/jacoco-report/jacoco.csv" \
  "$TEST_NAME — CSV report generated"

pass "$TEST_NAME — coverage with filtering on Scala 2.11"

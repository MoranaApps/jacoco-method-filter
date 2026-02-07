#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Test: examples/maven-scala (Scala) — tests pass WITHOUT and WITH filtering.
#
# Prerequisite: Maven plugin published locally (mvn install).
# ---------------------------------------------------------------------------
source "$(dirname "$0")/helpers.sh"

TEST_NAME="maven-scala-example"
info "Running: $TEST_NAME"

cp -R "$REPO_ROOT/examples/maven-scala" "$WORK_DIR/project"
cd "$WORK_DIR/project"

# ── 1. Without filtering (plain verify) ────────────────────────────────────
run_cmd "$TEST_NAME — mvn clean verify (no filtering)" mvn -B clean verify

pass "$TEST_NAME — tests pass without filtering"

# ── 2. With filtering (-Pcode-coverage) ────────────────────────────────────
run_cmd "$TEST_NAME — mvn clean verify -Pcode-coverage" mvn -B clean verify -Pcode-coverage

assert_dir_not_empty "target/classes-filtered" \
  "$TEST_NAME — filtered classes directory exists"

assert_file_exists "target/jacoco-report/index.html" \
  "$TEST_NAME — HTML report generated"

assert_file_exists "target/jacoco.xml" \
  "$TEST_NAME — XML report generated"

pass "$TEST_NAME — coverage with filtering"

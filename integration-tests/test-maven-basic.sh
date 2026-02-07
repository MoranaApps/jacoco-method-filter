#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Test: examples/maven-basic (Java) — tests pass WITHOUT and WITH filtering.
#
# Prerequisite: Maven plugin published locally (mvn install).
# ---------------------------------------------------------------------------
source "$(dirname "$0")/helpers.sh"

TEST_NAME="maven-basic-example"
info "Running: $TEST_NAME"

cp -R "$REPO_ROOT/examples/maven-basic" "$WORK_DIR/project"
cd "$WORK_DIR/project"

# ── 1. Without filtering (plain verify) ────────────────────────────────────
info "$TEST_NAME — running tests WITHOUT coverage filtering"
mvn -B clean verify

pass "$TEST_NAME — tests pass without filtering"

# ── 2. With filtering (-Pcode-coverage) ────────────────────────────────────
info "$TEST_NAME — running tests WITH coverage filtering (-Pcode-coverage)"
mvn -B clean verify -Pcode-coverage

assert_dir_not_empty "target/classes-filtered" \
  "$TEST_NAME — filtered classes directory exists"

assert_file_exists "target/jacoco-html/index.html" \
  "$TEST_NAME — HTML report generated"

assert_file_exists "target/jacoco.xml" \
  "$TEST_NAME — XML report generated"

pass "$TEST_NAME — coverage with filtering"

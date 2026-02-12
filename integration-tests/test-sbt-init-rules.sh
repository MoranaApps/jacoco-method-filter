#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Test: sbt jmfInitRules creates a rules file from scratch.
#
# Prerequisite: sbt plugin published locally.
# ---------------------------------------------------------------------------
source "$(dirname "$0")/helpers.sh"

TEST_NAME="sbt-init-rules"
info "Running: $TEST_NAME"

# Use the CI fixture (plugin already enabled) + overlay source from the example.
cp -R "$REPO_ROOT/integration-tests/fixtures/sbt-basic" "$WORK_DIR/project"
cp -R "$REPO_ROOT/examples/sbt-basic/src" "$WORK_DIR/project/src"
# Intentionally do NOT copy jmf-rules.txt — the test creates it from scratch.

cd "$WORK_DIR/project"

# Run jmfInitRules — should create jmf-rules.txt
run_cmd "$TEST_NAME — sbt jmfInitRules" sbt jmfInitRules

assert_file_exists "jmf-rules.txt" "$TEST_NAME — rules file created"
assert_file_contains "jmf-rules.txt" "jmf" "$TEST_NAME — rules file has content"

pass "$TEST_NAME"

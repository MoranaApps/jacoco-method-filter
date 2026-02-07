#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Test: sbt jmfInitRules creates a rules file from scratch.
#
# Prerequisite: sbt plugin published locally.
# ---------------------------------------------------------------------------
source "$(dirname "$0")/helpers.sh"

TEST_NAME="sbt-init-rules"
info "Running: $TEST_NAME"

# Copy the sbt-basic example into a temp dir and remove the existing rules file.
cp -R "$REPO_ROOT/examples/sbt-basic" "$WORK_DIR/project"
rm -f "$WORK_DIR/project/jmf-rules.txt"

cd "$WORK_DIR/project"

# Run jmfInitRules — should create jmf-rules.txt
sbt --no-colors jmfInitRules

assert_file_exists "jmf-rules.txt" "$TEST_NAME — rules file created"
assert_file_contains "jmf-rules.txt" "jmf" "$TEST_NAME — rules file has content"

pass "$TEST_NAME"

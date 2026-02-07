#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Test: sbt jmfVerify — shows methods that would be filtered without modifying.
#
# Prerequisite: sbt plugin published locally.
# ---------------------------------------------------------------------------
source "$(dirname "$0")/helpers.sh"

TEST_NAME="sbt-verify"
info "Running: $TEST_NAME"

cp -R "$REPO_ROOT/examples/sbt-basic" "$WORK_DIR/project"
cd "$WORK_DIR/project"

# Run jmfVerify task
info "$TEST_NAME — running sbt jmfVerify"
OUTPUT=$(sbt jmfVerify 2>&1)

# Check that output contains expected markers
echo "$OUTPUT" | grep -q "\[verify\]" || \
  fail "$TEST_NAME — output missing [verify] prefix"

echo "$OUTPUT" | grep -q "Active rules" || \
  fail "$TEST_NAME — output missing 'Active rules'"

echo "$OUTPUT" | grep -q "Verification complete" || \
  fail "$TEST_NAME — output missing 'Verification complete'"

# Check that it mentions scanning class files
echo "$OUTPUT" | grep -q "scanned [0-9]* class file" || \
  fail "$TEST_NAME — output missing scan summary"

# Check that it found some matched methods (sbt-basic has case classes that should match rules)
echo "$OUTPUT" | grep -q "found [0-9]* method" || \
  fail "$TEST_NAME — output missing method count"

# Verify that no output directory was created (read-only mode)
[[ ! -d "target/classes-filtered" ]] || \
  fail "$TEST_NAME — verify should not create output directory"

pass "$TEST_NAME"

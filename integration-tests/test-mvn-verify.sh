#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Test: mvn jacoco-method-filter:verify — shows methods that would be filtered.
#
# Prerequisite: Maven plugin published locally (mvn install).
# ---------------------------------------------------------------------------
source "$(dirname "$0")/helpers.sh"

TEST_NAME="mvn-verify"
info "Running: $TEST_NAME"

cp -R "$REPO_ROOT/examples/maven-basic" "$WORK_DIR/project"
cd "$WORK_DIR/project"

# Compile first to ensure classes exist
info "$TEST_NAME — compiling project"
mvn -B clean compile

# Run verify goal
info "$TEST_NAME — running mvn verify goal"
OUTPUT=$(mvn -B io.github.moranaapps:jacoco-method-filter-maven-plugin:verify 2>&1)

# Check that output contains expected markers
echo "$OUTPUT" | grep -q "JaCoCo Method Filter: Verify Rules Impact" || \
  fail "$TEST_NAME — output missing verify banner"

echo "$OUTPUT" | grep -q "Active rules" || \
  fail "$TEST_NAME — output missing 'Active rules'"

echo "$OUTPUT" | grep -q "Verification complete" || \
  fail "$TEST_NAME — output missing 'Verification complete'"

# Check that it mentions scanning class files
echo "$OUTPUT" | grep -q "scanned [0-9]* class file" || \
  fail "$TEST_NAME — output missing scan summary"

# Verify that no output directory was created (read-only mode)
[[ ! -d "target/classes-filtered" ]] || \
  fail "$TEST_NAME — verify should not create output directory"

pass "$TEST_NAME"

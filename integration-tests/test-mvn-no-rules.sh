#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Test: Maven plugin with no rules file - pass-through behavior (issue #69).
#
# Covers:
#   1. rewrite + report (via -Pcode-coverage) with no jmf-rules.txt -> BUILD SUCCESS,
#      target/classes-filtered populated, a normal JaCoCo report is generated.
#   2. verify goal with no rules -> BUILD SUCCESS.
#   3. -Djmf.requireRules=true with no rules -> BUILD FAILURE (legacy strict behavior).
#
# Prerequisite: Maven plugin published locally (mvn install).
# ---------------------------------------------------------------------------
source "$(dirname "$0")/helpers.sh"

TEST_NAME="mvn-no-rules"
info "Running: $TEST_NAME"

cp -R "$REPO_ROOT/examples/maven-basic" "$WORK_DIR/project"
cd "$WORK_DIR/project"

# The whole point of this test: no rules file present.
rm -f jmf-rules.txt
[[ ! -f jmf-rules.txt ]] || fail "$TEST_NAME — jmf-rules.txt should have been removed"

# ── 1. Coverage profile with no rules -> pass-through ──────────────────────
LOG1="$WORK_DIR/cov.log"
info "$TEST_NAME — mvn clean verify -Pcode-coverage (no rules)"
mvn -B clean verify -Pcode-coverage > "$LOG1" 2>&1 || { cat "$LOG1"; \
  fail "$TEST_NAME — coverage build must succeed with no rules file"; }
cat "$LOG1"

grep -q "BUILD SUCCESS" "$LOG1" || fail "$TEST_NAME — expected BUILD SUCCESS"
grep -q "classes pass through unfiltered" "$LOG1" || \
  fail "$TEST_NAME — rewrite must log the pass-through notice"

assert_dir_not_empty "target/classes-filtered" \
  "$TEST_NAME — filtered classes directory populated (pass-through copy)"
assert_file_exists "target/jacoco-report/index.html" \
  "$TEST_NAME — HTML report generated"
assert_file_exists "target/jacoco.xml" \
  "$TEST_NAME — XML report generated"

pass "$TEST_NAME — coverage profile succeeds with no rules file"

# ── 2. verify goal with no rules -> BUILD SUCCESS ─────────────────────────
LOG2="$WORK_DIR/verify.log"
info "$TEST_NAME — verify goal with no rules"
mvn -B io.github.moranaapps:jacoco-method-filter-maven-plugin:verify > "$LOG2" 2>&1 || { cat "$LOG2"; \
  fail "$TEST_NAME — verify goal must succeed with no rules file"; }
cat "$LOG2"
grep -q "BUILD SUCCESS" "$LOG2" || fail "$TEST_NAME — verify goal: expected BUILD SUCCESS"
pass "$TEST_NAME — verify goal succeeds with no rules file"

# ── 3. jmf.requireRules=true -> BUILD FAILURE ─────────────────────────────
LOG3="$WORK_DIR/require.log"
info "$TEST_NAME — rewrite with -Djmf.requireRules=true (expect failure)"
strict_status=0
mvn -B clean process-test-classes -Pcode-coverage -Djmf.requireRules=true > "$LOG3" 2>&1 || strict_status=$?
cat "$LOG3"
[[ $strict_status -ne 0 ]] || \
  fail "$TEST_NAME — jmf.requireRules=true must fail the build when no rules exist"
grep -q "Rules configuration missing" "$LOG3" || \
  fail "$TEST_NAME — expected 'Rules configuration missing' in the failure output"
pass "$TEST_NAME — jmf.requireRules=true fails the build ($strict_status)"

pass "$TEST_NAME"

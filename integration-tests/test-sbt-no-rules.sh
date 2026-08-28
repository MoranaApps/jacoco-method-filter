#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Test: sbt plugin with no rules file - pass-through behavior (issue #69).
#
# Covers:
#   1. sbt jacoco (jacocoOn; clean; test; jacocoReportAll; jacocoOff) with no
#      jmf-rules.txt -> success, report generated, "classes pass through unfiltered".
#   2. jmfRequireRules := true with no rules -> jmfRewrite fails.
#
# Prerequisite: sbt plugin published locally.
# ---------------------------------------------------------------------------
source "$(dirname "$0")/helpers.sh"

TEST_NAME="sbt-no-rules"
info "Running: $TEST_NAME"

# CI fixture (plugin enabled) + example source, but deliberately NO jmf-rules.txt.
cp -R "$REPO_ROOT/integration-tests/fixtures/sbt-basic" "$WORK_DIR/project"
cp -R "$REPO_ROOT/examples/sbt-basic/src"               "$WORK_DIR/project/src"
cd "$WORK_DIR/project"
[[ ! -f jmf-rules.txt ]] || fail "$TEST_NAME — fixture unexpectedly ships a jmf-rules.txt"

# ── 1. Coverage run with no rules -> pass-through ─────────────────────────
LOG1="$WORK_DIR/jacoco.log"
info "$TEST_NAME — sbt jacoco (no rules)"
sbt jacoco > "$LOG1" 2>&1 || { cat "$LOG1"; fail "$TEST_NAME — 'sbt jacoco' must succeed with no rules file"; }
cat "$LOG1"

grep -q "classes pass through unfiltered" "$LOG1" || \
  fail "$TEST_NAME — jmfRewrite must log the pass-through notice"

REPORT_DIR="target/scala-2.12/jacoco-report"
assert_file_exists "$REPORT_DIR/index.html" "$TEST_NAME — HTML report generated"
assert_file_exists "$REPORT_DIR/jacoco.xml" "$TEST_NAME — XML report generated"
pass "$TEST_NAME — coverage run succeeds with no rules file"

# ── 2. jmfRequireRules := true -> jmfRewrite fails ────────────────────────
LOG2="$WORK_DIR/require.log"
info "$TEST_NAME — jmfRewrite with jmfRequireRules := true (expect failure)"
require_status=0
sbt "set every jacocoPluginEnabled := true" "set every jmfRequireRules := true" jmfRewrite \
  > "$LOG2" 2>&1 || require_status=$?
cat "$LOG2"
[[ $require_status -ne 0 ]] || \
  fail "$TEST_NAME — jmfRequireRules := true must fail jmfRewrite when no rules exist"
grep -q "no rules configured" "$LOG2" || \
  fail "$TEST_NAME — expected 'no rules configured' in the failure output"
pass "$TEST_NAME — jmfRequireRules := true fails the build ($require_status)"

pass "$TEST_NAME"

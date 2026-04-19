#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Test: sbt jmfVerify with jmfReportFile + jmfReportFormat settings.
#
# Verifies that the sbt plugin writes the filtered-methods report to a file
# in all three supported formats (txt, json, csv) and that each file's
# content is structurally correct.
#
# Rules file used here deliberately exercises all three rule kinds so every
# section of each report format is exercised:
#   - Global rules  (*#copy(*) etc.)   → EXCLUDED via scala-* rule IDs
#   - Local rule    (class-specific)   → EXCLUDED via local-calc-add rule ID
#   - Include rule  (+… rescue)        → RESCUED via rescue-calc-tostring rule ID
#
# Prerequisite: sbt plugin published locally.
# ---------------------------------------------------------------------------
source "$(dirname "$0")/helpers.sh"

TEST_NAME="sbt-report-file"
info "Running: $TEST_NAME"

cp -R "$REPO_ROOT/integration-tests/fixtures/sbt-basic" "$WORK_DIR/project"
cp -R "$REPO_ROOT/examples/sbt-basic/src"               "$WORK_DIR/project/src"

# Custom rules file: global + local + include — written directly into work dir
# so assertions can check all three rule kinds appear in report output.
cat > "$WORK_DIR/project/jmf-rules.txt" << 'RULES'
# [jmf:1.0.0]

# ── Global rules (match any class) ───────────────────────────────────────────
*#copy(*)                               id:scala-copy
*#copy$default$*(*)                     id:scala-copy-default
*#productArity(*)                       id:scala-product-arity
*#productElement(*)                     id:scala-product-element
*#productPrefix(*)                      id:scala-product-prefix
*#productIterator(*)                    id:scala-product-iterator
*#equals(*)                             id:scala-equals
*#hashCode(*)                           id:scala-hashcode
*#toString(*)                           id:scala-tostring
*#canEqual(*)                           id:scala-canequal

# ── Local rule (class-specific: exclude Calculator#add) ──────────────────────
example.Calculator#add(*)               id:local-calc-add

# ── Include rule (rescue Calculator#toString from global exclusion) ───────────
+example.Calculator#toString(*)         id:rescue-calc-tostring
RULES

cd "$WORK_DIR/project"

# ── 1. txt format ──────────────────────────────────────────────────────────
REPORT_TXT="$WORK_DIR/jmf-report.txt"

info "$TEST_NAME — running jmfVerify with txt report"
LOG="$WORK_DIR/jmfVerify-txt.log"
if ! sbt \
  "set jmfReportFile := Some(file(\"$REPORT_TXT\"))" \
  "set jmfReportFormat := \"txt\"" \
  jmfVerify > "$LOG" 2>&1; then
  cat "$LOG"
  fail "$TEST_NAME — sbt jmfVerify (txt) failed"
fi

assert_file_exists "$REPORT_TXT" \
  "$TEST_NAME — txt report file created"

# EXCLUDED section — Calculator boilerplate via global rules + local rule
assert_file_contains "$REPORT_TXT" "EXCLUDED" \
  "$TEST_NAME — txt report contains EXCLUDED section"

assert_file_contains "$REPORT_TXT" "example.Calculator" \
  "$TEST_NAME — txt report mentions Calculator"

# Global rule ID visible in EXCLUDED section
assert_file_contains "$REPORT_TXT" "scala-copy" \
  "$TEST_NAME — txt report contains scala-copy rule ID (global rule)"

# Local rule ID visible in EXCLUDED section (class-specific rule)
assert_file_contains "$REPORT_TXT" "local-calc-add" \
  "$TEST_NAME — txt report contains local-calc-add rule ID (local rule)"

# RESCUED section — toString rescued by include rule
assert_file_contains "$REPORT_TXT" "RESCUED" \
  "$TEST_NAME — txt report contains RESCUED section"

assert_file_contains "$REPORT_TXT" "rescue-calc-tostring" \
  "$TEST_NAME — txt report contains rescue-calc-tostring rule ID (include rule)"

assert_file_contains "$REPORT_TXT" "Summary:" \
  "$TEST_NAME — txt report contains Summary line"

# No [verify] stdout prefix in file
if grep -q "\[verify\]" "$REPORT_TXT"; then
  fail "$TEST_NAME — txt report must not contain [verify] prefix"
fi

pass "$TEST_NAME — txt report file content correct"

# ── 2. json format ─────────────────────────────────────────────────────────
REPORT_JSON="$WORK_DIR/jmf-report.json"

info "$TEST_NAME — running jmfVerify with json report"
LOG="$WORK_DIR/jmfVerify-json.log"
if ! sbt \
  "set jmfReportFile := Some(file(\"$REPORT_JSON\"))" \
  "set jmfReportFormat := \"json\"" \
  jmfVerify > "$LOG" 2>&1; then
  cat "$LOG"
  fail "$TEST_NAME — sbt jmfVerify (json) failed"
fi

assert_file_exists "$REPORT_JSON" \
  "$TEST_NAME — json report file created"

assert_file_contains "$REPORT_JSON" '"classesScanned"' \
  "$TEST_NAME — json report has classesScanned key"

assert_file_contains "$REPORT_JSON" '"excluded"' \
  "$TEST_NAME — json report has excluded key"

assert_file_contains "$REPORT_JSON" '"rescued"' \
  "$TEST_NAME — json report has rescued key"

assert_file_contains "$REPORT_JSON" '"example.Calculator"' \
  "$TEST_NAME — json report mentions Calculator"

# File must start with { (well-formed JSON object)
head -1 "$REPORT_JSON" | grep -q "^{" || \
  fail "$TEST_NAME — json report does not start with {"

# excluded array non-empty: global rule visible
assert_file_contains "$REPORT_JSON" '"scala-copy"' \
  "$TEST_NAME — json excluded entries contain scala-copy (global rule)"

# excluded array: local rule visible
assert_file_contains "$REPORT_JSON" '"local-calc-add"' \
  "$TEST_NAME — json excluded entries contain local-calc-add (local rule)"

# rescued array non-empty: include rule visible
assert_file_contains "$REPORT_JSON" '"rescue-calc-tostring"' \
  "$TEST_NAME — json rescued entries contain rescue-calc-tostring (include rule)"

# rescued entries have both exclusionRuleIds and inclusionRuleIds keys
assert_file_contains "$REPORT_JSON" '"exclusionRuleIds"' \
  "$TEST_NAME — json rescued entries have exclusionRuleIds key"

assert_file_contains "$REPORT_JSON" '"inclusionRuleIds"' \
  "$TEST_NAME — json rescued entries have inclusionRuleIds key"

pass "$TEST_NAME — json report file content correct"

# ── 3. csv format ──────────────────────────────────────────────────────────
REPORT_CSV="$WORK_DIR/jmf-report.csv"

info "$TEST_NAME — running jmfVerify with csv report"
LOG="$WORK_DIR/jmfVerify-csv.log"
if ! sbt \
  "set jmfReportFile := Some(file(\"$REPORT_CSV\"))" \
  "set jmfReportFormat := \"csv\"" \
  jmfVerify > "$LOG" 2>&1; then
  cat "$LOG"
  fail "$TEST_NAME — sbt jmfVerify (csv) failed"
fi

assert_file_exists "$REPORT_CSV" \
  "$TEST_NAME — csv report file created"

# Header row must be exact
HEADER="outcome,class,method,descriptor,exclusionRuleIds,inclusionRuleIds"
assert_file_contains "$REPORT_CSV" "$HEADER" \
  "$TEST_NAME — csv report has correct header"

assert_file_contains "$REPORT_CSV" "EXCLUDED" \
  "$TEST_NAME — csv report contains EXCLUDED rows"

assert_file_contains "$REPORT_CSV" "example.Calculator" \
  "$TEST_NAME — csv report mentions Calculator"

# Global rule ID in EXCLUDED rows
assert_file_contains "$REPORT_CSV" "scala-copy" \
  "$TEST_NAME — csv EXCLUDED rows contain scala-copy (global rule)"

# Local rule ID in EXCLUDED rows
assert_file_contains "$REPORT_CSV" "local-calc-add" \
  "$TEST_NAME — csv EXCLUDED rows contain local-calc-add (local rule)"

# RESCUED row present — include rule in action
assert_file_contains "$REPORT_CSV" "RESCUED" \
  "$TEST_NAME — csv report contains RESCUED row"

assert_file_contains "$REPORT_CSV" "rescue-calc-tostring" \
  "$TEST_NAME — csv RESCUED row contains rescue-calc-tostring (include rule)"

# RESCUED row must also show the exclusion rule that was overridden
assert_file_contains "$REPORT_CSV" "scala-tostring" \
  "$TEST_NAME — csv RESCUED row shows exclusionRuleId scala-tostring"

# Every data row (non-header) must have 6 comma-separated fields
INVALID_ROWS=$(tail -n +2 "$REPORT_CSV" | awk -F',' 'NF != 6 { print NR+1": "$0 }')
if [[ -n "$INVALID_ROWS" ]]; then
  echo "Rows with wrong field count:"
  echo "$INVALID_ROWS"
  fail "$TEST_NAME — csv report has rows with wrong number of fields"
fi

pass "$TEST_NAME — csv report file content correct"

# ── 4. No report file written when jmfReportFile is None (default) ─────────
# Run jmfVerify without any jmfReportFile setting (default = None).
# The sbt output must NOT contain "[info] Report written to:" because no
# report file path is configured.
info "$TEST_NAME — verifying no report file written when jmfReportFile = None"
DEFAULT_LOG="$WORK_DIR/jmfVerify-default.log"
sbt jmfVerify > "$DEFAULT_LOG" 2>&1 || true
if grep -q "Report written to" "$DEFAULT_LOG"; then
  fail "$TEST_NAME — report file must not be written when jmfReportFile = None"
fi

pass "$TEST_NAME — no report file written by default"

pass "$TEST_NAME"

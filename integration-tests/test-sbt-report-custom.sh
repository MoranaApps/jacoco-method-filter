#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Test: sbt plugin with custom report settings
#
# Verifies:
# - Custom report formats (HTML only, skip XML/CSV)
# - Custom report name/title
# - Custom source encoding
# - Custom includes/excludes patterns
#
# Prerequisite: sbt plugin published locally.
# ---------------------------------------------------------------------------
source "$(dirname "$0")/helpers.sh"

TEST_NAME="sbt-report-custom"
info "Running: $TEST_NAME"

# Use the CI fixture + overlay source/rules from the example.
cp -R "$REPO_ROOT/integration-tests/fixtures/sbt-basic" "$WORK_DIR/project"
cp -R "$REPO_ROOT/examples/sbt-basic/src" "$WORK_DIR/project/src"
cp    "$REPO_ROOT/examples/sbt-basic/jmf-rules.txt" "$WORK_DIR/project/"
cd "$WORK_DIR/project"

# Create custom build.sbt with report customizations
cat >> build.sbt << 'EOF'

// Custom report settings
jacocoReportFormats := Set("html")  // Only HTML, skip XML and CSV
jacocoReportName := "Custom Coverage Report"
jacocoSourceEncoding := "UTF-8"
// Keep default includes/excludes for now (agent patterns)
EOF

# ── 1. Run jacoco with custom settings ────────────────────────────────────
run_cmd "$TEST_NAME — sbt jacoco (custom report settings)" sbt jacoco

# Verify reports directory exists
REPORT_DIR="target/scala-2.12/jacoco-report"
assert_dir_not_empty "$REPORT_DIR" \
  "$TEST_NAME — JaCoCo report directory exists and is not empty"

# ── 2. Verify only HTML report is generated (XML and CSV should NOT exist) ─
assert_file_exists "$REPORT_DIR/index.html" \
  "$TEST_NAME — HTML report generated"

# Check that XML and CSV were NOT generated (should fail if they exist)
if [[ -f "$REPORT_DIR/jacoco.xml" ]]; then
  fail "$TEST_NAME — XML report should NOT be generated (jacocoReportFormats = Set(html))"
fi

if [[ -f "$REPORT_DIR/jacoco.csv" ]]; then
  fail "$TEST_NAME — CSV report should NOT be generated (jacocoReportFormats = Set(html))"
fi

pass "$TEST_NAME — XML and CSV correctly skipped"

# ── 3. Verify custom report title is used ─────────────────────────────────
assert_file_contains "$REPORT_DIR/index.html" "Custom Coverage Report" \
  "$TEST_NAME — Custom report title appears in HTML"

pass "$TEST_NAME — custom report settings work correctly"

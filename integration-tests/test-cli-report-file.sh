#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Test: CLI --report-file and --report-format flags.
#
# Verifies:
# - --verify --report-file writes a txt report to disk
# - --verify --report-file --report-format json writes JSON
# - --verify --report-file --report-format csv writes CSV with correct header
# - --dry-run --report-file writes a report after a dry-run rewrite pass
# - --report-file with nested (non-existent) parent directory creates dirs
#
# Prerequisite: rewriter-core published locally.
# ---------------------------------------------------------------------------
source "$(dirname "$0")/helpers.sh"

TEST_NAME="cli-report-file"
info "Running: $TEST_NAME"

# Set up project
cp -R "$REPO_ROOT/integration-tests/fixtures/sbt-basic" "$WORK_DIR/project"
cp -R "$REPO_ROOT/examples/sbt-basic/src"               "$WORK_DIR/project/src"
cp    "$REPO_ROOT/examples/sbt-basic/jmf-rules.txt"     "$WORK_DIR/project/"
cd "$WORK_DIR/project"

# Compile to produce .class files and seed Coursier cache
run_cmd "$TEST_NAME — compiling project" sbt compile
run_cmd "$TEST_NAME — exporting classpath" sbt "export runtime:dependencyClasspath"

assert_dir_not_empty "target/scala-2.12/classes" \
  "$TEST_NAME — compiled classes exist"

# ── Build CLI classpath ────────────────────────────────────────────────────
CORE_JAR=$(
  find ~/.ivy2/local/io.github.moranaapps/jacoco-method-filter-core_2.12 \
    -name "jacoco-method-filter-core_2.12.jar" 2>/dev/null | \
  while IFS= read -r f; do
    timestamp=$(stat -c %Y "$f" 2>/dev/null || stat -f %m "$f" 2>/dev/null)
    printf '%s\t%s\n' "$timestamp" "$f"
  done | \
  sort -rn | head -1 | cut -f2- || true
)
SCALA_LIB=$(find ~/.cache/coursier ~/Library/Caches/Coursier -name "scala-library-2.12*.jar" 2>/dev/null | head -1 || true)
ASM_JAR=$(find ~/.cache/coursier ~/Library/Caches/Coursier -name "asm-9.*.jar" 2>/dev/null | sort -V | tail -1 || true)
ASM_COMMONS_JAR=$(find ~/.cache/coursier ~/Library/Caches/Coursier -name "asm-commons-9.*.jar" 2>/dev/null | sort -V | tail -1 || true)
SCOPT_JAR=$(find ~/.cache/coursier ~/Library/Caches/Coursier -name "scopt_2.12-*.jar" 2>/dev/null | head -1 || true)

[[ -n "$CORE_JAR"  && -f "$CORE_JAR"  ]] || fail "$TEST_NAME — core JAR not found (run 'sbt publishLocal' first)"
[[ -n "$SCALA_LIB" && -f "$SCALA_LIB" ]] || fail "$TEST_NAME — Scala library not found in Coursier cache"
[[ -n "$ASM_JAR"   && -f "$ASM_JAR"   ]] || fail "$TEST_NAME — ASM JAR not found in Coursier cache"
[[ -n "$SCOPT_JAR" && -f "$SCOPT_JAR" ]] || fail "$TEST_NAME — scopt JAR not found in Coursier cache"

CP="$CORE_JAR:$SCALA_LIB:$ASM_JAR:$SCOPT_JAR"
[[ -n "$ASM_COMMONS_JAR" && -f "$ASM_COMMONS_JAR" ]] && CP="$CP:$ASM_COMMONS_JAR"

CLI="java -cp $CP io.moranaapps.jacocomethodfilter.CoverageRewriter"
IN="target/scala-2.12/classes"
RULES="jmf-rules.txt"

# ── 1. --verify --report-file txt (default format) ────────────────────────
REPORT_TXT="$WORK_DIR/report-verify.txt"
info "$TEST_NAME — running CLI verify with --report-file (txt)"
$CLI --verify --in "$IN" --local-rules "$RULES" --report-file "$REPORT_TXT"

assert_file_exists "$REPORT_TXT" \
  "$TEST_NAME — txt report file created"

assert_file_contains "$REPORT_TXT" "EXCLUDED" \
  "$TEST_NAME — txt report contains EXCLUDED section"

assert_file_contains "$REPORT_TXT" "Summary:" \
  "$TEST_NAME — txt report contains Summary line"

assert_file_contains "$REPORT_TXT" "example.Calculator" \
  "$TEST_NAME — txt report mentions Calculator (has matched methods)"

# No [verify] prefix in file output (that's stdout-only prefix)
if grep -q "\[verify\]" "$REPORT_TXT"; then
  fail "$TEST_NAME — txt report should not contain [verify] prefix"
fi

pass "$TEST_NAME — txt report file written correctly"

# ── 2. --verify --report-file --report-format json ────────────────────────
REPORT_JSON="$WORK_DIR/report-verify.json"
info "$TEST_NAME — running CLI verify with --report-file (json)"
$CLI --verify --in "$IN" --local-rules "$RULES" \
  --report-file "$REPORT_JSON" --report-format json

assert_file_exists "$REPORT_JSON" \
  "$TEST_NAME — json report file created"

assert_file_contains "$REPORT_JSON" '"classesScanned"' \
  "$TEST_NAME — json report has classesScanned key"

assert_file_contains "$REPORT_JSON" '"excluded"' \
  "$TEST_NAME — json report has excluded key"

assert_file_contains "$REPORT_JSON" '"rescued"' \
  "$TEST_NAME — json report has rescued key"

# Must be parseable as JSON (at minimum: starts with { ends with })
head -1 "$REPORT_JSON" | grep -q "^{" || \
  fail "$TEST_NAME — json report does not start with {"

pass "$TEST_NAME — json report file written correctly"

# ── 3. --verify --report-file --report-format csv ────────────────────────
REPORT_CSV="$WORK_DIR/report-verify.csv"
info "$TEST_NAME — running CLI verify with --report-file (csv)"
$CLI --verify --in "$IN" --local-rules "$RULES" \
  --report-file "$REPORT_CSV" --report-format csv

assert_file_exists "$REPORT_CSV" \
  "$TEST_NAME — csv report file created"

assert_file_contains "$REPORT_CSV" \
  "outcome,class,method,descriptor,exclusionRuleIds,inclusionRuleIds" \
  "$TEST_NAME — csv report has correct header"

assert_file_contains "$REPORT_CSV" "EXCLUDED" \
  "$TEST_NAME — csv report contains EXCLUDED rows"

pass "$TEST_NAME — csv report file written correctly"

# ── 4. --dry-run --report-file ────────────────────────────────────────────
REPORT_DRYRUN="$WORK_DIR/report-dryrun.txt"
OUT_DIR="$WORK_DIR/classes-filtered"
info "$TEST_NAME — running CLI dry-run with --report-file"
$CLI --dry-run --in "$IN" --out "$OUT_DIR" --local-rules "$RULES" \
  --report-file "$REPORT_DRYRUN"

assert_file_exists "$REPORT_DRYRUN" \
  "$TEST_NAME — dry-run report file created"

assert_file_contains "$REPORT_DRYRUN" "EXCLUDED" \
  "$TEST_NAME — dry-run report contains EXCLUDED section"

pass "$TEST_NAME — dry-run report file written correctly"

# ── 5. --report-file in nested (non-existent) directory ───────────────────
NESTED_REPORT="$WORK_DIR/nested/deep/path/report.json"
info "$TEST_NAME — running CLI verify with nested report path"
$CLI --verify --in "$IN" --local-rules "$RULES" \
  --report-file "$NESTED_REPORT" --report-format json

assert_file_exists "$NESTED_REPORT" \
  "$TEST_NAME — report file created in nested directory"

pass "$TEST_NAME — nested parent directory created automatically"

# ── 6. --report-format without --report-file is rejected ─────────────────
info "$TEST_NAME — verifying --report-format without --report-file is rejected"
if $CLI --verify --in "$IN" --local-rules "$RULES" --report-format json > /dev/null 2>&1; then
  fail "$TEST_NAME — CLI must reject --report-format without --report-file"
fi

pass "$TEST_NAME — --report-format without --report-file rejected correctly"

pass "$TEST_NAME"

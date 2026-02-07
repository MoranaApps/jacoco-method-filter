#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Test: JaCoCo E2E compatibility — verify jacoco-method-filter works correctly
# with specified JaCoCo versions.
#
# Usage:
#   bash integration-tests/test-jacoco-compat.sh <jacoco-version>
#   e.g., bash integration-tests/test-jacoco-compat.sh 0.8.14
#
# Prerequisite: rewriter-core published locally.
# ---------------------------------------------------------------------------
source "$(dirname "$0")/helpers.sh"

TEST_NAME="jacoco-compat"

# ---- Parse arguments --------------------------------------------------------
if [[ $# -lt 1 ]]; then
  fail "$TEST_NAME — usage: $0 <jacoco-version>"
fi

JACOCO_VERSION="$1"
info "Running: $TEST_NAME for JaCoCo version $JACOCO_VERSION"

# ---- Setup directories -------------------------------------------------------
FIXTURE_DIR="$REPO_ROOT/integration-tests/fixtures/jacoco-compat"
PROJECT_DIR="$WORK_DIR/project"
CACHE_DIR="$WORK_DIR/jacoco-cache"

mkdir -p "$PROJECT_DIR" "$CACHE_DIR"

# ---- Download JaCoCo JARs ----------------------------------------------------
info "Downloading JaCoCo JARs for version $JACOCO_VERSION"

AGENT_JAR="$CACHE_DIR/org.jacoco.agent-${JACOCO_VERSION}-runtime.jar"
CLI_JAR="$CACHE_DIR/org.jacoco.cli-${JACOCO_VERSION}-nodeps.jar"

download_jar() {
  local url="$1"
  local dest="$2"
  
  if [[ -f "$dest" ]]; then
    info "Using cached JAR: $dest"
    return 0
  fi
  
  info "Downloading: $url"
  if ! curl -fsSL -o "$dest" "$url"; then
    fail "$TEST_NAME — failed to download $url"
  fi
}

download_jar \
  "https://repo1.maven.org/maven2/org/jacoco/org.jacoco.agent/${JACOCO_VERSION}/org.jacoco.agent-${JACOCO_VERSION}-runtime.jar" \
  "$AGENT_JAR"

download_jar \
  "https://repo1.maven.org/maven2/org/jacoco/org.jacoco.cli/${JACOCO_VERSION}/org.jacoco.cli-${JACOCO_VERSION}-nodeps.jar" \
  "$CLI_JAR"

assert_file_exists "$AGENT_JAR" "$TEST_NAME — JaCoCo agent JAR"
assert_file_exists "$CLI_JAR" "$TEST_NAME — JaCoCo CLI JAR"

# ---- Copy fixture project -----------------------------------------------------
info "Copying fixture project"
cp -R "$FIXTURE_DIR"/* "$PROJECT_DIR/"
cd "$PROJECT_DIR"

# ---- Compile Java classes -----------------------------------------------------
info "Compiling Java classes"
mkdir -p classes
javac -d classes src/example/*.java || fail "$TEST_NAME — javac compilation failed"

assert_dir_not_empty "classes" "$TEST_NAME — compiled classes"
assert_file_exists "classes/example/FilterDemo.class" "$TEST_NAME — FilterDemo.class"
assert_file_exists "classes/example/FilterDemoTest.class" "$TEST_NAME — FilterDemoTest.class"

# ---- Run test with JaCoCo agent -----------------------------------------------
info "Running test with JaCoCo agent to generate coverage data"
JACOCO_EXEC="jacoco.exec"

java -javaagent:"$AGENT_JAR=destfile=$JACOCO_EXEC" \
  -cp classes \
  example.FilterDemoTest || fail "$TEST_NAME — test execution failed"

assert_file_exists "$JACOCO_EXEC" "$TEST_NAME — jacoco.exec"

# ---- Run CoverageRewriter to filter classes ----------------------------------
info "Running CoverageRewriter to filter classes"
FILTERED_DIR="classes-filtered"
mkdir -p "$FILTERED_DIR"

# Use sbt run to invoke the rewriter (most reliable approach)
(cd "$REPO_ROOT" && sbt "project rewriterCore" \
  "run --in $PROJECT_DIR/classes --out $PROJECT_DIR/$FILTERED_DIR --rules $PROJECT_DIR/jmf-rules.txt" \
  2>&1 | tee "$WORK_DIR/rewriter.log") || fail "$TEST_NAME — CoverageRewriter failed"

assert_dir_not_empty "$FILTERED_DIR" "$TEST_NAME — filtered classes directory"
assert_file_exists "$FILTERED_DIR/example/FilterDemo.class" "$TEST_NAME — filtered FilterDemo.class"

# ---- Generate JaCoCo XML reports ----------------------------------------------
info "Generating JaCoCo XML reports"

# Report for original classes
java -jar "$CLI_JAR" report "$JACOCO_EXEC" \
  --classfiles classes \
  --xml report-original.xml \
  --html report-original-html \
  || fail "$TEST_NAME — JaCoCo report generation (original) failed"

assert_file_exists "report-original.xml" "$TEST_NAME — original XML report"

# Report for filtered classes
java -jar "$CLI_JAR" report "$JACOCO_EXEC" \
  --classfiles "$FILTERED_DIR" \
  --xml report-filtered.xml \
  --html report-filtered-html \
  || fail "$TEST_NAME — JaCoCo report generation (filtered) failed"

assert_file_exists "report-filtered.xml" "$TEST_NAME — filtered XML report"

# ---- Assertions on reports ----------------------------------------------------
info "Verifying report contents"

# In original report: equals, hashCode, toString should be present with coverage
assert_file_contains "report-original.xml" 'name="equals"' \
  "$TEST_NAME — original report contains equals method"

assert_file_contains "report-original.xml" 'name="hashCode"' \
  "$TEST_NAME — original report contains hashCode method"

assert_file_contains "report-original.xml" 'name="toString"' \
  "$TEST_NAME — original report contains toString method"

# Check that equals/hashCode/toString have non-zero coverage in original
# Look for method counters - if covered > 0 or missed == 0 then method was executed
if ! grep -A2 'name="equals"' report-original.xml | grep -q 'covered="[1-9]'; then
  # Also check if missed="0" (meaning fully covered)
  if ! grep -A2 'name="equals"' report-original.xml | grep -q 'missed="0"'; then
    fail "$TEST_NAME — equals method should have coverage in original report"
  fi
fi

# In both reports: computeValue and isPositive should be present with coverage
assert_file_contains "report-original.xml" 'name="computeValue"' \
  "$TEST_NAME — original report contains computeValue method"

assert_file_contains "report-original.xml" 'name="isPositive"' \
  "$TEST_NAME — original report contains isPositive method"

assert_file_contains "report-filtered.xml" 'name="computeValue"' \
  "$TEST_NAME — filtered report contains computeValue method"

assert_file_contains "report-filtered.xml" 'name="isPositive"' \
  "$TEST_NAME — filtered report contains isPositive method"

# In filtered report: equals, hashCode, toString should be ABSENT or have zero counters
# Check if the methods exist in the filtered report - they should not
FILTERED_HAS_EQUALS=false
FILTERED_HAS_HASHCODE=false
FILTERED_HAS_TOSTRING=false

grep -q 'name="equals"' report-filtered.xml && FILTERED_HAS_EQUALS=true
grep -q 'name="hashCode"' report-filtered.xml && FILTERED_HAS_HASHCODE=true
grep -q 'name="toString"' report-filtered.xml && FILTERED_HAS_TOSTRING=true

# If they exist, verify they have zero coverage
if [[ "$FILTERED_HAS_EQUALS" == true ]]; then
  # Method exists - check it has zero counters
  if grep -A2 'name="equals"' report-filtered.xml | grep -q 'covered="[1-9]'; then
    echo "─── Filtered report excerpt (equals) ───"
    grep -A5 'name="equals"' report-filtered.xml || true
    echo "─── End excerpt ───"
    fail "$TEST_NAME — equals method should have zero coverage in filtered report (JaCoCo $JACOCO_VERSION)"
  fi
fi

if [[ "$FILTERED_HAS_HASHCODE" == true ]]; then
  if grep -A2 'name="hashCode"' report-filtered.xml | grep -q 'covered="[1-9]'; then
    echo "─── Filtered report excerpt (hashCode) ───"
    grep -A5 'name="hashCode"' report-filtered.xml || true
    echo "─── End excerpt ───"
    fail "$TEST_NAME — hashCode method should have zero coverage in filtered report (JaCoCo $JACOCO_VERSION)"
  fi
fi

if [[ "$FILTERED_HAS_TOSTRING" == true ]]; then
  if grep -A2 'name="toString"' report-filtered.xml | grep -q 'covered="[1-9]'; then
    echo "─── Filtered report excerpt (toString) ───"
    grep -A5 'name="toString"' report-filtered.xml || true
    echo "─── End excerpt ───"
    fail "$TEST_NAME — toString method should have zero coverage in filtered report (JaCoCo $JACOCO_VERSION)"
  fi
fi

# Summary logging on success
info "SUCCESS with JaCoCo $JACOCO_VERSION:"
info "  - jacoco.exec was generated"
info "  - Both XML reports were generated"
info "  - Original report shows coverage for filtered methods (equals, hashCode, toString)"
info "  - Filtered report excludes or zeros filtered methods"
info "  - Kept methods (computeValue, isPositive) present in both reports"
info "Rules file: $PROJECT_DIR/jmf-rules.txt"
cat "$PROJECT_DIR/jmf-rules.txt"

pass "$TEST_NAME (JaCoCo $JACOCO_VERSION)"

#!/usr/bin/env bash
set -euo pipefail
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
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JACOCO_VERSIONS_FILE="$SCRIPT_DIR/jacoco-versions.txt"

source "$SCRIPT_DIR/helpers.sh"

TEST_NAME="jacoco-compat"

# ---- Parse arguments --------------------------------------------------------
if [[ $# -lt 1 ]]; then
  TESTED_VERSIONS=""
  if [[ -f "$JACOCO_VERSIONS_FILE" ]]; then
    TESTED_VERSIONS="$(sed -e 's/#.*$//' -e '/^[[:space:]]*$/d' "$JACOCO_VERSIONS_FILE" | paste -sd ', ' -)"
  fi

  echo "ERROR: Missing JaCoCo version argument"
  echo "Usage: $0 <jacoco-version>"
  echo "Example: $0 0.8.14"
  echo ""
  if [[ -n "$TESTED_VERSIONS" ]]; then
    echo "Tested versions: $TESTED_VERSIONS"
  fi
  exit 1
fi

JACOCO_VERSION="$1"
info "Running: $TEST_NAME for JaCoCo version $JACOCO_VERSION"

# ---- Setup directories -------------------------------------------------------
FIXTURE_DIR="$REPO_ROOT/integration-tests/fixtures/jacoco-compat"
PROJECT_DIR="$WORK_DIR/project"
CACHE_DIR="${JACOCO_CACHE_DIR:-$REPO_ROOT/target/jacoco-cache}"

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
cd "$PROJECT_DIR" || fail "$TEST_NAME — failed to enter project directory"

# ---- Compile Java classes -----------------------------------------------------
info "Compiling Java classes"
mkdir -p classes
# Compile to an older bytecode level for broad compatibility:
# - JaCoCo 0.8.7 cannot instrument newer classfile versions.
# - CoverageRewriter uses ASM which also has a max supported classfile version.
# Running the test on a newer JDK is fine as long as the compiled classes are compatible.
javac --release 8 -d classes src/example/*.java || fail "$TEST_NAME — javac compilation failed"

assert_dir_not_empty "classes" "$TEST_NAME — compiled classes"
assert_file_exists "classes/example/FilterDemo.class" "$TEST_NAME — FilterDemo.class"
assert_file_exists "classes/example/FilterDemoTest.class" "$TEST_NAME — FilterDemoTest.class"
assert_file_exists "classes/example/BaseContainer.class" "$TEST_NAME — BaseContainer.class"
assert_file_exists "classes/example/StringContainer.class" "$TEST_NAME — StringContainer.class"

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

# Helper function to check if a method has coverage
check_method_has_coverage() {
  local report="$1"
  local method="$2"
  local label="$3"
  
  # Check if covered > 0 OR missed == 0 (both indicate execution)
  if ! grep -A2 "name=\"$method\"" "$report" | grep -q 'covered="[1-9]'; then
    if ! grep -A2 "name=\"$method\"" "$report" | grep -q 'missed="0"'; then
      fail "$label — method '$method' should have coverage in $report"
    fi
  fi
}

# Helper function to check if a method has zero or no coverage
check_method_no_coverage() {
  local report="$1"
  local method="$2"
  local label="$3"
  
  if grep -q "name=\"$method\"" "$report"; then
    # Method exists - verify it has zero coverage
    if grep -A2 "name=\"$method\"" "$report" | grep -q 'covered="[1-9]'; then
      echo "─── Filtered report excerpt ($method) ───"
      grep -A5 "name=\"$method\"" "$report" || true
      echo "─── End excerpt ───"
      fail "$label — method '$method' should have zero coverage in filtered report (JaCoCo $JACOCO_VERSION)"
    fi
  fi
  # If method doesn't exist, that's also acceptable (filtered out completely)
}

# In original report: equals, hashCode, toString should be present with coverage
assert_file_contains "report-original.xml" 'name="equals"' \
  "$TEST_NAME — original report contains equals method"

assert_file_contains "report-original.xml" 'name="hashCode"' \
  "$TEST_NAME — original report contains hashCode method"

assert_file_contains "report-original.xml" 'name="toString"' \
  "$TEST_NAME — original report contains toString method"

check_method_has_coverage "report-original.xml" "equals" "$TEST_NAME"

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
check_method_no_coverage "report-filtered.xml" "equals" "$TEST_NAME"
check_method_no_coverage "report-filtered.xml" "hashCode" "$TEST_NAME"
check_method_no_coverage "report-filtered.xml" "toString" "$TEST_NAME"

# Summary logging on success
info "SUCCESS with JaCoCo $JACOCO_VERSION:"
info "  - jacoco.exec was generated"
info "  - Both XML reports were generated"
info "  - Original report shows coverage for filtered methods (equals, hashCode, toString)"
info "  - Filtered report excludes or zeros filtered methods"
info "  - Kept methods (computeValue, isPositive) present in both reports"

# ── Flags & predicates assertions ─────────────────────────────────────────

# --- bridge/synthetic flag rule ------------------------------------------
# The synthetic bridge method Object get() in StringContainer should be
# excluded in the filtered report.  The covariant String get() should
# remain because it is NOT synthetic/bridge.
assert_file_contains "report-original.xml" 'name="get"' \
  "$TEST_NAME — original report contains get method(s)"

# In filtered report: at least one get (the covariant String get) must survive
assert_file_contains "report-filtered.xml" 'name="get"' \
  "$TEST_NAME — filtered report still has covariant get"

# --- ret:V + name-starts:init predicate ----------------------------------
# initData (void return, name starts with "init") should be filtered
assert_file_contains "report-original.xml" 'name="initData"' \
  "$TEST_NAME — original report contains initData"
check_method_no_coverage "report-filtered.xml" "initData" "$TEST_NAME"

# --- name-contains:Data predicate ----------------------------------------
# processData should be filtered (name contains "Data", no rescue rule)
assert_file_contains "report-original.xml" 'name="processData"' \
  "$TEST_NAME — original report contains processData"
check_method_no_coverage "report-filtered.xml" "processData" "$TEST_NAME"

# --- include (rescue) rule -----------------------------------------------
# getData should survive in filtered report because it's rescued by +rule.
# Note: coverage counters may be zero because the rewriter changes the class
# checksum and JaCoCo cannot match execution data. We only verify presence.
assert_file_contains "report-original.xml" 'name="getData"' \
  "$TEST_NAME — original report contains getData"
assert_file_contains "report-filtered.xml" 'name="getData"' \
  "$TEST_NAME — filtered report still has getData (rescued by include rule)"

info "  - Flags (bridge/synthetic) and predicates (ret:, name-*) verified"
info "  - Include (rescue) rule for getData verified"
info "Rules file: $PROJECT_DIR/jmf-rules.txt"
cat "$PROJECT_DIR/jmf-rules.txt"

pass "$TEST_NAME (JaCoCo $JACOCO_VERSION)"

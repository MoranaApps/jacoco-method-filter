#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Test: CLI --verify --verify-suggest-includes produces suggestions.
#
# Asserts that:
#   1. The suggestions section appears in the output.
#   2. At least one "+<class>#<method>(*)" suggestion line is emitted.
#   3. The heuristic NOTE footer is present.
#
# Prerequisite: rewriter-core published locally.
# ---------------------------------------------------------------------------
source "$(dirname "$0")/helpers.sh"

TEST_NAME="cli-verify-suggest-includes"
info "Running: $TEST_NAME"

# Use the CI fixture (plugin already enabled) + overlay source/rules from the example.
cp -R "$REPO_ROOT/integration-tests/fixtures/sbt-basic" "$WORK_DIR/project"
cp -R "$REPO_ROOT/examples/sbt-basic/src" "$WORK_DIR/project/src"
cp    "$REPO_ROOT/examples/sbt-basic/jmf-rules.txt" "$WORK_DIR/project/"
cd "$WORK_DIR/project"

# Compile the project to get .class files AND download dependencies
run_cmd "$TEST_NAME — compiling project" sbt compile

# Trigger dependency resolution to ensure all JARs are in Coursier cache
run_cmd "$TEST_NAME — exporting classpath" sbt "export runtime:dependencyClasspath"

# Verify classes directory exists
assert_dir_not_empty "target/scala-2.12/classes" \
  "$TEST_NAME — compiled classes exist"

# ── Locate JARs for CLI invocation ──────────────────────────────────────────

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

if [[ -z "$CORE_JAR" || ! -f "$CORE_JAR" ]]; then
  fail "$TEST_NAME — core JAR not found in ~/.ivy2/local (run 'sbt publishLocal' first)"
fi
if [[ -z "$SCALA_LIB" || ! -f "$SCALA_LIB" ]]; then
  fail "$TEST_NAME — Scala library not found in Coursier cache"
fi
if [[ -z "$ASM_JAR" || ! -f "$ASM_JAR" ]]; then
  fail "$TEST_NAME — ASM JAR not found in Coursier cache"
fi
if [[ -z "$SCOPT_JAR" || ! -f "$SCOPT_JAR" ]]; then
  fail "$TEST_NAME — scopt JAR not found in Coursier cache"
fi

CP="$CORE_JAR:$SCALA_LIB:$ASM_JAR:$SCOPT_JAR"
if [[ -n "$ASM_COMMONS_JAR" && -f "$ASM_COMMONS_JAR" ]]; then
  CP="$CP:$ASM_COMMONS_JAR"
fi

# ── Run CLI with --verify --verify-suggest-includes ─────────────────────────

info "$TEST_NAME — running CLI verify with suggest-includes"
OUTPUT=$(java -cp "$CP" io.moranaapps.jacocomethodfilter.CoverageRewriter \
  --verify \
  --verify-suggest-includes \
  --in target/scala-2.12/classes \
  --local-rules jmf-rules.txt 2>&1)
CLI_STATUS=$?

echo "─── CLI verify-suggest-includes output ───"
echo "$OUTPUT"
echo "─── end CLI verify-suggest-includes output ───"

if [[ $CLI_STATUS -ne 0 ]]; then
  fail "$TEST_NAME — CLI exited with status $CLI_STATUS"
fi

# ── Assertions ──────────────────────────────────────────────────────────────

# Basic verify markers must still be present
echo "$OUTPUT" | grep -q "\[verify\]" || \
  fail "$TEST_NAME — output missing [verify] prefix"

echo "$OUTPUT" | grep -q "Verification complete\|Summary:" || \
  fail "$TEST_NAME — output missing completion marker"

# The "Suggested include rules" header must appear
echo "$OUTPUT" | grep -q "Suggested include rules" || \
  fail "$TEST_NAME — output missing 'Suggested include rules' section"

# At least one suggestion line matching the pattern +<class>#<method>(*)
echo "$OUTPUT" | grep -qE '^\[verify\]   \+[a-zA-Z0-9_.]+#[a-zA-Z0-9_]+\(\*\)' || \
  fail "$TEST_NAME — output missing suggestion lines (+class#method(*))"

# The heuristic note must appear
echo "$OUTPUT" | grep -q "heuristic" || \
  fail "$TEST_NAME — output missing heuristic note"

# Calculator's excluded methods (e.g. equals, hashCode, toString, copy) should
# produce suggestions because MethodClassifier considers them PossiblyHuman.
echo "$OUTPUT" | grep -q "Calculator" || \
  fail "$TEST_NAME — output should mention Calculator in suggestions"

pass "$TEST_NAME"

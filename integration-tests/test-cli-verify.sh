#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Test: CLI --verify mode — read-only scan shows methods that would be filtered.
#
# Prerequisite: rewriter-core published locally.
# ---------------------------------------------------------------------------
source "$(dirname "$0")/helpers.sh"

TEST_NAME="cli-verify"
info "Running: $TEST_NAME"

# Copy sbt-basic example to get compiled classes and rules
cp -R "$REPO_ROOT/examples/sbt-basic" "$WORK_DIR/project"
cd "$WORK_DIR/project"

# Compile the project to get .class files AND download dependencies
run_cmd "$TEST_NAME — compiling project" sbt compile

# Trigger dependency resolution to ensure all JARs are in Coursier cache
run_cmd "$TEST_NAME — exporting classpath" sbt "export runtime:dependencyClasspath"

# Verify classes directory exists
assert_dir_not_empty "target/scala-2.13/classes" \
  "$TEST_NAME — compiled classes exist"

# Get the classpath for the CLI
# Use Coursier cache (modern sbt default) + macOS location fallback
CORE_JAR=$(find ~/.ivy2/local/io.github.moranaapps/jacoco-method-filter-core_2.13/1.2.0/jars -name "jacoco-method-filter-core_2.13.jar" 2>/dev/null | head -1 || true)
SCALA_LIB=$(find ~/.cache/coursier ~/Library/Caches/Coursier -name "scala-library-2.13*.jar" 2>/dev/null | head -1 || true)
ASM_JAR=$(find ~/.cache/coursier ~/Library/Caches/Coursier -name "asm-9.6.jar" 2>/dev/null | head -1 || true)
ASM_COMMONS_JAR=$(find ~/.cache/coursier ~/Library/Caches/Coursier -name "asm-commons-9.6.jar" 2>/dev/null | head -1 || true)
SCOPT_JAR=$(find ~/.cache/coursier ~/Library/Caches/Coursier -name "scopt_2.13-*.jar" 2>/dev/null | head -1 || true)

info "CORE_JAR=$CORE_JAR"
info "SCALA_LIB=$SCALA_LIB"
info "ASM_JAR=$ASM_JAR"
info "ASM_COMMONS_JAR=$ASM_COMMONS_JAR"
info "SCOPT_JAR=$SCOPT_JAR"

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

CP="$CORE_JAR:$SCALA_LIB:$ASM_JAR:${ASM_COMMONS_JAR:-}:$SCOPT_JAR"

# Run CLI verify mode
info "$TEST_NAME — running CLI verify"
OUTPUT=$(java -cp "$CP" io.moranaapps.jacocomethodfilter.CoverageRewriter \
  --verify \
  --in target/scala-2.13/classes \
  --rules jmf-rules.txt 2>&1) || true

echo "─── CLI verify output ───"
echo "$OUTPUT"
echo "─── end CLI verify output ───"

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

# Calculator (case class) should appear — it has boilerplate methods matching rules
echo "$OUTPUT" | grep -q "example.Calculator" || \
  fail "$TEST_NAME — output should mention Calculator (has matched methods)"

# StringFormatter (plain class) should NOT appear — none of its methods match rules
if echo "$OUTPUT" | grep -q "StringFormatter"; then
  fail "$TEST_NAME — output should NOT mention StringFormatter (no methods match rules)"
fi

# Verify that no output directory was created (read-only mode)
[[ ! -d "target/classes-filtered" ]] || \
  fail "$TEST_NAME — verify should not create output directory"

pass "$TEST_NAME"

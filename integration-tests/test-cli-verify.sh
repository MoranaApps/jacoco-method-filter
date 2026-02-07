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
info "$TEST_NAME — compiling project (downloads dependencies)"
sbt compile

# Trigger dependency resolution to ensure all JARs are in Coursier cache
sbt "export runtime:dependencyClasspath" > /dev/null

# Verify classes directory exists
assert_dir_not_empty "target/scala-2.13/classes" \
  "$TEST_NAME — compiled classes exist"

# Get the classpath for the CLI
# Use Coursier cache (modern sbt default)
CORE_JAR=$(find ~/.ivy2/local/io.github.moranaapps/jacoco-method-filter-core_2.13/1.2.0/jars -name "jacoco-method-filter-core_2.13.jar" 2>/dev/null | head -1)
SCALA_LIB=$(find ~/.cache/coursier -name "scala-library-2.13*.jar" 2>/dev/null | head -1)
ASM_JAR=$(find ~/.cache/coursier -name "asm-9.6.jar" 2>/dev/null | head -1)
ASM_COMMONS_JAR=$(find ~/.cache/coursier -name "asm-commons-9.6.jar" 2>/dev/null | head -1)
SCOPT_JAR=$(find ~/.cache/coursier -name "scopt_2.13-*.jar" 2>/dev/null | head -1)

if [[ ! -f "$CORE_JAR" ]]; then
  fail "$TEST_NAME — core JAR not found in ~/.ivy2/local (run 'sbt publishLocal' first)"
fi
if [[ ! -f "$SCALA_LIB" ]]; then
  fail "$TEST_NAME — Scala library not found in Coursier cache"
fi
if [[ ! -f "$ASM_JAR" ]]; then
  fail "$TEST_NAME — ASM JAR not found in Coursier cache"
fi
if [[ ! -f "$SCOPT_JAR" ]]; then
  fail "$TEST_NAME — scopt JAR not found in Coursier cache"
fi

CP="$CORE_JAR:$SCALA_LIB:$ASM_JAR:$ASM_COMMONS_JAR:$SCOPT_JAR"

# Run CLI verify mode
info "$TEST_NAME — running CLI verify"
OUTPUT=$(java -cp "$CP" io.moranaapps.jacocomethodfilter.CoverageRewriter \
  --verify \
  --in target/scala-2.13/classes \
  --rules jmf-rules.txt 2>&1)

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

# Verify that no output directory was created (read-only mode)
[[ ! -d "target/classes-filtered" ]] || \
  fail "$TEST_NAME — verify should not create output directory"

pass "$TEST_NAME"

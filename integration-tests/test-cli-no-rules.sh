#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Test: CLI with no rules source - pass-through behavior (issue #69).
#
# Covers:
#   1. rewrite with neither --global-rules nor --local-rules -> exit 0,
#      pass-through copy of every class, "Loaded 0 rule(s)" / "marked 0 method(s)".
#   2. --local-rules pointing at a missing file -> exit 0 + [warn], still pass-through.
#   3. --require-rules with no rules source -> non-zero exit + [error], no output dir.
#   4. verify with no rules -> exit 0, "found 0 method(s) matched".
#
# Prerequisite: rewriter-core published locally (run 'sbt publishLocal' first).
# ---------------------------------------------------------------------------
source "$(dirname "$0")/helpers.sh"

TEST_NAME="cli-no-rules"
info "Running: $TEST_NAME"

# CI fixture (plugin enabled) + example source, but deliberately NO jmf-rules.txt.
cp -R "$REPO_ROOT/integration-tests/fixtures/sbt-basic" "$WORK_DIR/project"
cp -R "$REPO_ROOT/examples/sbt-basic/src"               "$WORK_DIR/project/src"
cd "$WORK_DIR/project"

run_cmd "$TEST_NAME — compiling project" sbt compile
run_cmd "$TEST_NAME — exporting classpath" sbt "export runtime:dependencyClasspath"

assert_dir_not_empty "target/scala-2.12/classes" \
  "$TEST_NAME — compiled classes exist"

# ── Locate JARs (same pattern as test-cli-strict.sh) ───────────────────────
CORE_JAR=$(
  find ~/.ivy2/local/io.github.moranaapps/jacoco-method-filter-core_2.12 \
    -name "jacoco-method-filter-core_2.12.jar" 2>/dev/null | \
  while IFS= read -r f; do
    timestamp=$(stat -c %Y "$f" 2>/dev/null || stat -f %m "$f" 2>/dev/null)
    printf '%s\t%s\n' "$timestamp" "$f"
  done | sort -rn | head -1 | cut -f2- || true
)

SCALA_LIB=$(find ~/.cache/coursier ~/Library/Caches/Coursier -name "scala-library-2.12*.jar" 2>/dev/null | head -1 || true)
ASM_JAR=$(find ~/.cache/coursier ~/Library/Caches/Coursier -name "asm-9.*.jar" 2>/dev/null | sort -V | tail -1 || true)
ASM_COMMONS_JAR=$(find ~/.cache/coursier ~/Library/Caches/Coursier -name "asm-commons-9.*.jar" 2>/dev/null | sort -V | tail -1 || true)
SCOPT_JAR=$(find ~/.cache/coursier ~/Library/Caches/Coursier -name "scopt_2.12-*.jar" 2>/dev/null | head -1 || true)

[[ -n "$CORE_JAR" && -f "$CORE_JAR" ]]   || fail "$TEST_NAME — core JAR not found in ~/.ivy2/local (run 'sbt publishLocal' first)"
[[ -n "$SCALA_LIB" && -f "$SCALA_LIB" ]] || fail "$TEST_NAME — Scala library not found in Coursier cache"
[[ -n "$ASM_JAR" && -f "$ASM_JAR" ]]     || fail "$TEST_NAME — ASM JAR not found in Coursier cache"
[[ -n "$SCOPT_JAR" && -f "$SCOPT_JAR" ]] || fail "$TEST_NAME — scopt JAR not found in Coursier cache"

CP="$CORE_JAR:$SCALA_LIB:$ASM_JAR:$SCOPT_JAR"
if [[ -n "$ASM_COMMONS_JAR" && -f "$ASM_COMMONS_JAR" ]]; then
  CP="$CP:$ASM_COMMONS_JAR"
fi

MAIN="io.moranaapps.jacocomethodfilter.CoverageRewriter"
CLASSES_IN="target/scala-2.12/classes"
IN_COUNT=$(find "$CLASSES_IN" -name "*.class" | wc -l | tr -d ' ')
info "$TEST_NAME — input has $IN_COUNT .class file(s)"
[[ "$IN_COUNT" -gt 0 ]] || fail "$TEST_NAME — no input .class files"

# ═══════════════════════════════════════════════════════════════════════════
# Test 1 — rewrite with no rules sources -> pass-through copy
# ═══════════════════════════════════════════════════════════════════════════
info "$TEST_NAME — test 1: rewrite with no rules sources"
OUT1_DIR="$WORK_DIR/out-norules"
EXIT1=0
OUT1=$(java -cp "$CP" "$MAIN" --in "$CLASSES_IN" --out "$OUT1_DIR" 2>&1) || EXIT1=$?
echo "─── output (exit $EXIT1) ───"; echo "$OUT1"; echo "─── end ───"

[[ $EXIT1 -eq 0 ]] || fail "$TEST_NAME — test 1: expected exit 0, got $EXIT1"
echo "$OUT1" | grep -q "Loaded 0 rule(s)"   || fail "$TEST_NAME — test 1: expected 'Loaded 0 rule(s)'"
echo "$OUT1" | grep -q "marked 0 method(s)" || fail "$TEST_NAME — test 1: expected 'marked 0 method(s)'"

OUT1_COUNT=$(find "$OUT1_DIR" -name "*.class" | wc -l | tr -d ' ')
[[ "$OUT1_COUNT" == "$IN_COUNT" ]] || \
  fail "$TEST_NAME — test 1: output has $OUT1_COUNT classes, expected $IN_COUNT"
pass "$TEST_NAME — test 1: pass-through copy of all $IN_COUNT classes"

# ═══════════════════════════════════════════════════════════════════════════
# Test 2 — missing --local-rules file -> warn + pass-through
# ═══════════════════════════════════════════════════════════════════════════
info "$TEST_NAME — test 2: --local-rules pointing at a missing file"
OUT2_DIR="$WORK_DIR/out-missing-rules"
EXIT2=0
OUT2=$(java -cp "$CP" "$MAIN" --in "$CLASSES_IN" --out "$OUT2_DIR" \
  --local-rules "$WORK_DIR/does-not-exist.txt" 2>&1) || EXIT2=$?
echo "─── output (exit $EXIT2) ───"; echo "$OUT2"; echo "─── end ───"

[[ $EXIT2 -eq 0 ]] || fail "$TEST_NAME — test 2: expected exit 0, got $EXIT2"
echo "$OUT2" | grep -q "local rules file not found" || \
  fail "$TEST_NAME — test 2: expected '[warn] local rules file not found'"
OUT2_COUNT=$(find "$OUT2_DIR" -name "*.class" | wc -l | tr -d ' ')
[[ "$OUT2_COUNT" == "$IN_COUNT" ]] || \
  fail "$TEST_NAME — test 2: output has $OUT2_COUNT classes, expected $IN_COUNT"
pass "$TEST_NAME — test 2: missing local rules file warns and passes through"

# ═══════════════════════════════════════════════════════════════════════════
# Test 3 — --require-rules with no rules -> non-zero exit, no output dir
# ═══════════════════════════════════════════════════════════════════════════
info "$TEST_NAME — test 3: --require-rules with no rules sources"
OUT3_DIR="$WORK_DIR/out-require"
EXIT3=0
OUT3=$(java -cp "$CP" "$MAIN" --in "$CLASSES_IN" --out "$OUT3_DIR" --require-rules 2>&1) || EXIT3=$?
echo "─── output (exit $EXIT3) ───"; echo "$OUT3"; echo "─── end ───"

[[ $EXIT3 -ne 0 ]] || fail "$TEST_NAME — test 3: --require-rules must exit non-zero with no rules"
echo "$OUT3" | grep -q "\[error\]"     || fail "$TEST_NAME — test 3: expected [error] message"
echo "$OUT3" | grep -q "require-rules" || fail "$TEST_NAME — test 3: expected mention of require-rules"
[[ ! -d "$OUT3_DIR" ]] || fail "$TEST_NAME — test 3: output dir must not be created on early abort"
pass "$TEST_NAME — test 3: --require-rules exits non-zero ($EXIT3)"

# ═══════════════════════════════════════════════════════════════════════════
# Test 4 — verify with no rules -> exit 0, 0 methods matched
# ═══════════════════════════════════════════════════════════════════════════
info "$TEST_NAME — test 4: verify with no rules sources"
EXIT4=0
OUT4=$(java -cp "$CP" "$MAIN" --verify --in "$CLASSES_IN" 2>&1) || EXIT4=$?
echo "─── output (exit $EXIT4) ───"; echo "$OUT4"; echo "─── end ───"

[[ $EXIT4 -eq 0 ]] || fail "$TEST_NAME — test 4: expected exit 0, got $EXIT4"
echo "$OUT4" | grep -q "found 0 method(s) matched by rules" || \
  fail "$TEST_NAME — test 4: expected 'found 0 method(s) matched by rules'"
pass "$TEST_NAME — test 4: verify with no rules reports 0 matched"

pass "$TEST_NAME"

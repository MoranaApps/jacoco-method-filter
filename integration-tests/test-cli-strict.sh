#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Test: --strict flag — exits non-zero when any rules have no id: label.
#
# Covers:
#   1. Rules without id: produce [warn] output at load time (no --strict).
#   2. Rules with id: produce no [warn] output.
#   3. --strict exits non-zero when any rules lack id: label.
#   4. --strict exits zero when all rules have id: labels.
#   5. --strict works in rewrite mode (not only --verify).
#   6. Warning message includes source file path and line number.
#
# Prerequisite: rewriter-core published locally (run 'sbt publishLocal' first).
# ---------------------------------------------------------------------------
source "$(dirname "$0")/helpers.sh"

TEST_NAME="cli-strict"
info "Running: $TEST_NAME"

# ── Set up project ─────────────────────────────────────────────────────────
cp -R "$REPO_ROOT/integration-tests/fixtures/sbt-basic" "$WORK_DIR/project"
cp -R "$REPO_ROOT/examples/sbt-basic/src"               "$WORK_DIR/project/src"
cd "$WORK_DIR/project"

run_cmd "$TEST_NAME — compiling project" sbt compile
run_cmd "$TEST_NAME — exporting classpath" sbt "export runtime:dependencyClasspath"

assert_dir_not_empty "target/scala-2.12/classes" \
  "$TEST_NAME — compiled classes exist"

# ── Locate JARs ────────────────────────────────────────────────────────────
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
SCOPT_JAR=$(find ~/.cache/coursier ~/Library/Caches/Coursier -name "scopt_2.12-*.jar"        2>/dev/null | head -1 || true)

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

# ── Helper: run CLI verify with a given rules file (does not fail on non-zero) ──
run_verify() {
  local rules_file="$1"; shift
  java -cp "$CP" io.moranaapps.jacocomethodfilter.CoverageRewriter \
    --verify \
    --in target/scala-2.12/classes \
    --local-rules "$rules_file" "$@" 2>&1 || true
}

# ═══════════════════════════════════════════════════════════════════════════
# Test 1 — Unlabelled rule emits [warn] at load time (without --strict)
# ═══════════════════════════════════════════════════════════════════════════
info "$TEST_NAME — test 1: unlabelled rule emits [warn] at load time"

cat > rules-no-id.txt <<'EOF'
# Rule without id: label — should trigger a [warn]
*Calculator#copy(*)
EOF

OUTPUT1=$(run_verify rules-no-id.txt)
echo "─── output ───"
echo "$OUTPUT1"
echo "─── end output ───"

echo "$OUTPUT1" | grep -q "\[warn\]" || \
  fail "$TEST_NAME — test 1: expected [warn] in output for unlabelled rule"

echo "$OUTPUT1" | grep -q "no id: label" || \
  fail "$TEST_NAME — test 1: expected 'no id: label' in [warn] message"

echo "$OUTPUT1" | grep -q "rules-no-id.txt" || \
  fail "$TEST_NAME — test 1: expected source file name in [warn] message"

pass "$TEST_NAME — test 1: [warn] emitted for unlabelled rule"

# ═══════════════════════════════════════════════════════════════════════════
# Test 2 — Labelled rules produce no [warn]
# ═══════════════════════════════════════════════════════════════════════════
info "$TEST_NAME — test 2: labelled rules produce no [warn]"

cat > rules-with-ids.txt <<'EOF'
*Calculator#copy(*) id:calc-copy
*Calculator#equals(*) id:calc-equals
EOF

OUTPUT2=$(run_verify rules-with-ids.txt)
echo "─── output ───"
echo "$OUTPUT2"
echo "─── end output ───"

if echo "$OUTPUT2" | grep -q "\[warn\]"; then
  fail "$TEST_NAME — test 2: unexpected [warn] in output when all rules have id: labels"
fi

pass "$TEST_NAME — test 2: no [warn] when all rules are labelled"

# ═══════════════════════════════════════════════════════════════════════════
# Test 3 — --strict exits non-zero when any rules lack id: label
# ═══════════════════════════════════════════════════════════════════════════
info "$TEST_NAME — test 3: --strict exits non-zero when unlabelled rules present"

EXIT_CODE3=0
OUTPUT3=$(java -cp "$CP" io.moranaapps.jacocomethodfilter.CoverageRewriter \
  --verify \
  --in target/scala-2.12/classes \
  --local-rules rules-no-id.txt \
  --strict 2>&1) || EXIT_CODE3=$?

echo "─── output ───"
echo "$OUTPUT3"
echo "─── end output (exit $EXIT_CODE3) ───"

if [[ $EXIT_CODE3 -eq 0 ]]; then
  fail "$TEST_NAME — test 3: --strict must exit non-zero when unlabelled rules exist (got 0)"
fi

echo "$OUTPUT3" | grep -q "\[error\]" || \
  fail "$TEST_NAME — test 3: expected [error] message when --strict aborts"

echo "$OUTPUT3" | grep -q "no id: label" || \
  fail "$TEST_NAME — test 3: expected 'no id: label' in [error] message"

pass "$TEST_NAME — test 3: --strict exits non-zero ($EXIT_CODE3)"

# ═══════════════════════════════════════════════════════════════════════════
# Test 4 — --strict exits zero when all rules have id: labels
# ═══════════════════════════════════════════════════════════════════════════
info "$TEST_NAME — test 4: --strict exits zero when all rules labelled"

EXIT_CODE4=0
OUTPUT4=$(java -cp "$CP" io.moranaapps.jacocomethodfilter.CoverageRewriter \
  --verify \
  --in target/scala-2.12/classes \
  --local-rules rules-with-ids.txt \
  --strict 2>&1) || EXIT_CODE4=$?

echo "─── output ───"
echo "$OUTPUT4"
echo "─── end output (exit $EXIT_CODE4) ───"

if [[ $EXIT_CODE4 -ne 0 ]]; then
  fail "$TEST_NAME — test 4: --strict must exit zero when all rules have id: labels (got $EXIT_CODE4)"
fi

if echo "$OUTPUT4" | grep -q "\[error\]"; then
  fail "$TEST_NAME — test 4: unexpected [error] when all rules are labelled"
fi

pass "$TEST_NAME — test 4: --strict exits zero when all rules labelled"

# ═══════════════════════════════════════════════════════════════════════════
# Test 5 — --strict works in rewrite mode (not only --verify)
# ═══════════════════════════════════════════════════════════════════════════
info "$TEST_NAME — test 5: --strict works in rewrite mode"

STRICT_OUT_DIR="$WORK_DIR/out-strict"
EXIT_CODE5=0
OUTPUT5=$(java -cp "$CP" io.moranaapps.jacocomethodfilter.CoverageRewriter \
  --in target/scala-2.12/classes \
  --out "$STRICT_OUT_DIR" \
  --local-rules rules-no-id.txt \
  --strict 2>&1) || EXIT_CODE5=$?

echo "─── output ───"
echo "$OUTPUT5"
echo "─── end output (exit $EXIT_CODE5) ───"

if [[ $EXIT_CODE5 -eq 0 ]]; then
  fail "$TEST_NAME — test 5: --strict in rewrite mode must exit non-zero when unlabelled rules exist"
fi

if [[ -d "$STRICT_OUT_DIR" ]]; then
  fail "$TEST_NAME — test 5: output directory must NOT be created when --strict aborts early"
fi

pass "$TEST_NAME — test 5: --strict exits non-zero in rewrite mode ($EXIT_CODE5)"

# ═══════════════════════════════════════════════════════════════════════════
# Test 6 — Warning includes source file path and line number
# ═══════════════════════════════════════════════════════════════════════════
info "$TEST_NAME — test 6: [warn] includes file path and line number"

cat > rules-mixed.txt <<'EOF'
# line 1: comment (skipped)
*Calculator#copy(*) id:calc-copy
*Calculator#hashCode(*)
EOF

OUTPUT6=$(run_verify rules-mixed.txt)
echo "─── output ───"
echo "$OUTPUT6"
echo "─── end output ───"

echo "$OUTPUT6" | grep -q "\[warn\]" || \
  fail "$TEST_NAME — test 6: expected [warn] for unlabelled rule on line 3"

echo "$OUTPUT6" | grep "\[warn\]" | grep -q "line 3" || \
  fail "$TEST_NAME — test 6: expected 'line 3' in [warn] message"

echo "$OUTPUT6" | grep "\[warn\]" | grep -q "rules-mixed.txt" || \
  fail "$TEST_NAME — test 6: expected file name in [warn] message"

pass "$TEST_NAME — test 6: [warn] contains file path and line number"

# ═══════════════════════════════════════════════════════════════════════════
# Test 7 — --strict with mixed labelled/unlabelled: reports count
# ═══════════════════════════════════════════════════════════════════════════
info "$TEST_NAME — test 7: --strict reports count of unlabelled rules"

EXIT_CODE7=0
OUTPUT7=$(java -cp "$CP" io.moranaapps.jacocomethodfilter.CoverageRewriter \
  --verify \
  --in target/scala-2.12/classes \
  --local-rules rules-mixed.txt \
  --strict 2>&1) || EXIT_CODE7=$?

echo "─── output ───"
echo "$OUTPUT7"
echo "─── end output (exit $EXIT_CODE7) ───"

if [[ $EXIT_CODE7 -eq 0 ]]; then
  fail "$TEST_NAME — test 7: --strict must exit non-zero for mixed rules"
fi

echo "$OUTPUT7" | grep -q "1 rule(s) have no id:" || \
  fail "$TEST_NAME — test 7: expected count '1 rule(s)' in [error] message"

pass "$TEST_NAME — test 7: --strict reports correct count of unlabelled rules"

# ═══════════════════════════════════════════════════════════════════════════
# Test 8 — Combined --strict AND --error-on-unmatched: strict check runs first
# ═══════════════════════════════════════════════════════════════════════════
info "$TEST_NAME — test 8: --strict takes precedence when combined with --error-on-unmatched"

# Both conditions true: unlabelled rule (no id) + unmatched rule (class does not exist)
cat > rules-unlabelled-unmatched.txt <<'EOF'
*com.example.DoesNotExist#copy(*)
EOF

EXIT_CODE8=0
OUTPUT8=$(java -cp "$CP" io.moranaapps.jacocomethodfilter.CoverageRewriter \
  --verify \
  --in target/scala-2.12/classes \
  --local-rules rules-unlabelled-unmatched.txt \
  --strict \
  --error-on-unmatched 2>&1) || EXIT_CODE8=$?

echo "─── output ───"
echo "$OUTPUT8"
echo "─── end output (exit $EXIT_CODE8) ───"

if [[ $EXIT_CODE8 -eq 0 ]]; then
  fail "$TEST_NAME — test 8: must exit non-zero when both --strict and --error-on-unmatched conditions fail"
fi

echo "$OUTPUT8" | grep -q "no id: label" || \
  fail "$TEST_NAME — test 8: expected 'no id: label' abort message (--strict check runs first)"

pass "$TEST_NAME — test 8: combined --strict and --error-on-unmatched exits non-zero"

pass "$TEST_NAME"

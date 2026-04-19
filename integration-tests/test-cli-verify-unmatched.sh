#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Test: CLI --verify UNMATCHED RULES report and --error-on-unmatched flag.
#
# Covers:
#   1. Rules that match zero methods appear in "UNMATCHED RULES" section.
#   2. Rules that do match are NOT listed as unmatched.
#   3. forward-compat rules are excluded from the unmatched list.
#   4. --error-on-unmatched exits non-zero when unmatched rules exist.
#   5. --error-on-unmatched exits zero when all rules matched.
#
# Prerequisite: rewriter-core published locally (run 'sbt publishLocal' first).
# ---------------------------------------------------------------------------
source "$(dirname "$0")/helpers.sh"

TEST_NAME="cli-verify-unmatched"
info "Running: $TEST_NAME"

# ── Set up project ─────────────────────────────────────────────────────────
cp -R "$REPO_ROOT/integration-tests/fixtures/sbt-basic" "$WORK_DIR/project"
cp -R "$REPO_ROOT/examples/sbt-basic/src"               "$WORK_DIR/project/src"
cd "$WORK_DIR/project"

# Compile to produce .class files and warm up the Coursier cache.
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

# ── Helper: run CLI verify with a given rules file ─────────────────────────
run_verify() {
  local rules_file="$1"; shift
  java -cp "$CP" io.moranaapps.jacocomethodfilter.CoverageRewriter \
    --verify \
    --in target/scala-2.12/classes \
    --local-rules "$rules_file" "$@" 2>&1 || true
}

# ═══════════════════════════════════════════════════════════════════════════
# Test 1 — Rule with no matching class appears in UNMATCHED RULES section
# ═══════════════════════════════════════════════════════════════════════════
info "$TEST_NAME — test 1: unmatched rule appears in UNMATCHED RULES section"

cat > rules-unmatched.txt <<'EOF'
# A rule that will never match (class does not exist in the build)
*com.example.DoesNotExist#copy(*) id:ghost-copy
# A rule that DOES match (Calculator is a case class with boilerplate)
*Calculator#copy(*) id:calc-copy
EOF

OUTPUT=$(run_verify rules-unmatched.txt)
echo "─── output ───"
echo "$OUTPUT"
echo "─── end output ───"

echo "$OUTPUT" | grep -q "UNMATCHED RULES" || \
  fail "$TEST_NAME — test 1: output missing 'UNMATCHED RULES' section"

echo "$OUTPUT" | grep -q "ghost-copy" || \
  fail "$TEST_NAME — test 1: unmatched rule id 'ghost-copy' not listed"

# The matched rule must NOT appear in UNMATCHED RULES
# (it appears in the EXCLUDED section instead)
UNMATCHED_SECTION=$(echo "$OUTPUT" | awk '/UNMATCHED RULES/,/Summary:/' | head -20)
if echo "$UNMATCHED_SECTION" | grep -q "calc-copy"; then
  fail "$TEST_NAME — test 1: matched rule 'calc-copy' must not be in UNMATCHED RULES"
fi

pass "$TEST_NAME — test 1: unmatched rule correctly reported"

# ═══════════════════════════════════════════════════════════════════════════
# Test 2 — All rules matched → no UNMATCHED RULES section
# ═══════════════════════════════════════════════════════════════════════════
info "$TEST_NAME — test 2: all rules matched → no UNMATCHED RULES section"

cat > rules-all-matched.txt <<'EOF'
*Calculator#copy(*) id:calc-copy
*Calculator#equals(*) id:calc-equals
EOF

OUTPUT2=$(run_verify rules-all-matched.txt)
echo "─── output ───"
echo "$OUTPUT2"
echo "─── end output ───"

if echo "$OUTPUT2" | grep -q "UNMATCHED RULES"; then
  fail "$TEST_NAME — test 2: UNMATCHED RULES section should not appear when all rules matched"
fi

pass "$TEST_NAME — test 2: no UNMATCHED RULES when all rules matched"

# ═══════════════════════════════════════════════════════════════════════════
# Test 3 — forward-compat rules are excluded from UNMATCHED report
# ═══════════════════════════════════════════════════════════════════════════
info "$TEST_NAME — test 3: forward-compat rules excluded from UNMATCHED report"

cat > rules-fwd-compat.txt <<'EOF'
# forward-compat: targets a class present in production but not this test build
*com.example.FutureService#copy(*) forward-compat id:future-copy
# Plain unmatched: should be reported
*com.example.AlsoMissing#process(*) id:plain-missing
EOF

OUTPUT3=$(run_verify rules-fwd-compat.txt)
echo "─── output ───"
echo "$OUTPUT3"
echo "─── end output ───"

echo "$OUTPUT3" | grep -q "UNMATCHED RULES" || \
  fail "$TEST_NAME — test 3: UNMATCHED RULES section must appear (plain-missing rule)"

echo "$OUTPUT3" | grep -q "plain-missing" || \
  fail "$TEST_NAME — test 3: 'plain-missing' rule must appear in UNMATCHED RULES"

# forward-compat rules must show "(forward-compat)" in the active rules listing
echo "$OUTPUT3" | grep -q "(forward-compat)" || \
  fail "$TEST_NAME — test 3: active rules listing must indicate (forward-compat) for future-copy rule"

# forward-compat rule must NOT appear in the UNMATCHED RULES section.
# NOTE: its id DOES appear in the "Active rules" listing above — scope the check
# to only the UNMATCHED RULES section to avoid a false positive.
UNMATCHED_SECTION3=$(echo "$OUTPUT3" | awk '/UNMATCHED RULES/,0')
if echo "$UNMATCHED_SECTION3" | grep -q "future-copy"; then
  fail "$TEST_NAME — test 3: forward-compat rule 'future-copy' must NOT appear in UNMATCHED RULES"
fi

pass "$TEST_NAME — test 3: forward-compat rule correctly exempted"

# ═══════════════════════════════════════════════════════════════════════════
# Test 4 — --error-on-unmatched exits non-zero when unmatched rules exist
# ═══════════════════════════════════════════════════════════════════════════
info "$TEST_NAME — test 4: --error-on-unmatched exits non-zero on unmatched rules"

EXIT_CODE4=0
OUTPUT4=$(java -cp "$CP" io.moranaapps.jacocomethodfilter.CoverageRewriter \
  --verify \
  --in target/scala-2.12/classes \
  --local-rules rules-unmatched.txt \
  --error-on-unmatched 2>&1) || EXIT_CODE4=$?

echo "─── output ───"
echo "$OUTPUT4"
echo "─── end output ───"

if [[ $EXIT_CODE4 -eq 0 ]]; then
  fail "$TEST_NAME — test 4: --error-on-unmatched must exit non-zero when unmatched rules exist (got 0)"
fi

pass "$TEST_NAME — test 4: --error-on-unmatched exits non-zero ($EXIT_CODE4)"

# ═══════════════════════════════════════════════════════════════════════════
# Test 5 — --error-on-unmatched exits zero when all rules matched
# ═══════════════════════════════════════════════════════════════════════════
info "$TEST_NAME — test 5: --error-on-unmatched exits zero when all rules matched"

OUTPUT5=$(java -cp "$CP" io.moranaapps.jacocomethodfilter.CoverageRewriter \
  --verify \
  --in target/scala-2.12/classes \
  --local-rules rules-all-matched.txt \
  --error-on-unmatched 2>&1)
EXIT_CODE5=$?
echo "─── output ───"
echo "$OUTPUT5"
echo "─── end output ───"

if [[ $EXIT_CODE5 -ne 0 ]]; then
  fail "$TEST_NAME — test 5: --error-on-unmatched must exit zero when all rules matched (got $EXIT_CODE5)"
fi

pass "$TEST_NAME — test 5: --error-on-unmatched exits zero when all rules matched"

# ═══════════════════════════════════════════════════════════════════════════
# Test 6 — --error-on-unmatched rejected without --verify (CLI validation)
# ═══════════════════════════════════════════════════════════════════════════
info "$TEST_NAME — test 6: --error-on-unmatched without --verify is rejected"

EXIT_CODE6=0
OUTPUT6=$(java -cp "$CP" io.moranaapps.jacocomethodfilter.CoverageRewriter \
  --in target/scala-2.12/classes \
  --out /tmp/jmf-out-$$ \
  --local-rules rules-all-matched.txt \
  --error-on-unmatched 2>&1) || EXIT_CODE6=$?

echo "─── output ───"
echo "$OUTPUT6"
echo "─── end output ───"

if [[ $EXIT_CODE6 -eq 0 ]]; then
  fail "$TEST_NAME — test 6: CLI must reject --error-on-unmatched without --verify"
fi

pass "$TEST_NAME — test 6: --error-on-unmatched correctly rejected without --verify"

pass "$TEST_NAME"

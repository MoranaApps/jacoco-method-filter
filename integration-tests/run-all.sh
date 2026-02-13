#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# run-all.sh — Integration test runner.
#
# Publishes plugins locally, then runs every test-*.sh script in this
# directory. Prints a summary at the end.
#
# Usage:
#   ./integration-tests/run-all.sh              # run all tests
#   ./integration-tests/run-all.sh --skip-publish  # skip local publish step
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colours
if [[ -t 1 ]]; then
  RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; BOLD='\033[1m'; NC='\033[0m'
else
  RED=''; GREEN=''; YELLOW=''; BOLD=''; NC=''
fi

SKIP_PUBLISH=false
for arg in "$@"; do
  case "$arg" in
    --skip-publish) SKIP_PUBLISH=true ;;
  esac
done

# ── 0. Check required tools ────────────────────────────────────────────────
HAS_SBT=false; HAS_MVN=false
command -v sbt >/dev/null 2>&1 && HAS_SBT=true
command -v mvn >/dev/null 2>&1 && HAS_MVN=true

echo -e "${BOLD}═══ Environment ═══${NC}"
echo -e "  sbt: $([[ $HAS_SBT == true ]] && echo -e "${GREEN}found${NC}" || echo -e "${YELLOW}not found${NC}")"
echo -e "  mvn: $([[ $HAS_MVN == true ]] && echo -e "${GREEN}found${NC}" || echo -e "${YELLOW}not found${NC}")"
echo ""

if [[ "$HAS_SBT" == false && "$HAS_MVN" == false ]]; then
  echo -e "${RED}ERROR${NC}: Neither sbt nor mvn found on PATH. At least one is required."
  exit 1
fi

# ── 1. Publish plugins locally ─────────────────────────────────────────────
if [[ "$SKIP_PUBLISH" == false ]]; then
  echo -e "${BOLD}═══ Publishing plugins locally ═══${NC}"

  if [[ "$HAS_SBT" == true ]]; then
    echo -e "${YELLOW}Publishing rewriter-core (all Scala versions) to Ivy local…${NC}"
    (cd "$REPO_ROOT" && sbt "project rewriterCore" +publishLocal)

    # Also publish to Maven local (~/.m2/repository) so the Maven plugin can resolve it.
    # Remove any previous non-SNAPSHOT artifacts first – sbt publishM2 refuses to overwrite.
    rm -rf ~/.m2/repository/io/github/moranaapps/jacoco-method-filter-core_2.12 2>/dev/null || true
    echo -e "${YELLOW}Publishing rewriter-core (Scala 2.12) to Maven local…${NC}"
    (cd "$REPO_ROOT" && sbt "project rewriterCore" ++2.12.21 publishM2)

    echo -e "${YELLOW}Publishing sbt plugin…${NC}"
    (cd "$REPO_ROOT" && sbt "project sbtPlugin" publishLocal)
  else
    echo -e "${YELLOW}Skipping sbt publish (sbt not found).${NC}"
  fi

  # Maven plugin: only if mvn is available and maven-plugin/pom.xml exists
  if [[ "$HAS_MVN" == false ]]; then
    echo -e "${YELLOW}Skipping Maven plugin publish (mvn not on PATH).${NC}"
  elif [[ ! -f "$REPO_ROOT/maven-plugin/pom.xml" ]]; then
    echo -e "${YELLOW}Skipping Maven plugin publish (maven-plugin/pom.xml not found).${NC}"
  else
    # Purge any cached "not found" markers and stale artifacts for clean resolution
    find ~/.m2/repository/io/github/moranaapps -name "*.lastUpdated" -delete 2>/dev/null || true
    rm -rf ~/.m2/repository/io/github/moranaapps/jacoco-method-filter-maven-plugin 2>/dev/null || true

    echo -e "${YELLOW}Publishing Maven plugin locally…${NC}"
    (cd "$REPO_ROOT/maven-plugin" && mvn -B install -DskipTests)
  fi

  echo ""
fi

# ── 2. Discover and run tests ──────────────────────────────────────────────
echo -e "${BOLD}═══ Running integration tests ═══${NC}"

TOTAL=0
PASSED=0
FAILED=0
SKIPPED=0
FAILURES=()
SKIPPED_LIST=()

for test_script in "$SCRIPT_DIR"/test-*.sh; do
  test_name="$(basename "$test_script" .sh)"
  TOTAL=$((TOTAL + 1))

  # Skip tests whose tool is not available
  if [[ "$test_name" == test-sbt-* && "$HAS_SBT" == false ]]; then
    SKIPPED=$((SKIPPED + 1))
    SKIPPED_LIST+=("$test_name (sbt not on PATH)")
    continue
  fi
  if [[ "$test_name" == test-maven-* || "$test_name" == test-mvn-* ]] && [[ "$HAS_MVN" == false ]]; then
    SKIPPED=$((SKIPPED + 1))
    SKIPPED_LIST+=("$test_name (mvn not on PATH)")
    continue
  fi

  echo ""
  echo -e "${BOLD}─── $test_name ───${NC}"

  # For jacoco-compat test, pass JaCoCo version as argument
  # Test both versions to match CI matrix behavior
  if [[ "$test_name" == "test-jacoco-compat" ]]; then
    for version in 0.8.7 0.8.14; do
      echo -e "${YELLOW}Testing with JaCoCo $version${NC}"
      if bash "$test_script" "$version"; then
        PASSED=$((PASSED + 1))
      else
        FAILED=$((FAILED + 1))
        FAILURES+=("$test_name (JaCoCo $version)")
      fi
      TOTAL=$((TOTAL + 1))
    done
  else
    if bash "$test_script"; then
      PASSED=$((PASSED + 1))
    else
      FAILED=$((FAILED + 1))
      FAILURES+=("$test_name")
    fi
  fi
done

# ── 3. Summary ─────────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}═══ Summary ($TOTAL tests) ═══${NC}"
echo -e "  ${GREEN}Passed${NC}:  $PASSED"
echo -e "  ${RED}Failed${NC}:  $FAILED"
echo -e "  ${YELLOW}Skipped${NC}: $SKIPPED"

if [[ ${#SKIPPED_LIST[@]} -gt 0 ]]; then
  echo ""
  echo -e "  ${YELLOW}Skipped tests:${NC}"
  for s in "${SKIPPED_LIST[@]}"; do
    echo -e "    - $s"
  done
fi

if [[ ${#FAILURES[@]} -gt 0 ]]; then
  echo ""
  echo -e "  ${RED}Failed tests:${NC}"
  for f in "${FAILURES[@]}"; do
    echo -e "    - $f"
  done
  echo ""
  echo -e "${RED}RESULT: FAILURE${NC}"
  exit 1
fi

echo ""
if [[ $SKIPPED -gt 0 ]]; then
  echo -e "${GREEN}RESULT: OK${NC} ($PASSED passed, $SKIPPED skipped)"
else
  echo -e "${GREEN}RESULT: OK${NC} ($PASSED passed)"
fi

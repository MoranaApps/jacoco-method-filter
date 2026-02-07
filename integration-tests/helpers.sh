#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Integration test helpers — sourced by every test script.
# ---------------------------------------------------------------------------
set -euo pipefail

# Colours (disabled when not a terminal)
if [[ -t 1 ]]; then
  RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; NC='\033[0m'
else
  RED=''; GREEN=''; YELLOW=''; NC=''
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Create a temporary work directory that is cleaned up on exit.
WORK_DIR="$(mktemp -d)"
cleanup() { rm -rf "$WORK_DIR"; }
trap cleanup EXIT

pass() { echo -e "${GREEN}PASS${NC}: $1"; }
fail() { echo -e "${RED}FAIL${NC}: $1"; exit 1; }
info() { echo -e "${YELLOW}INFO${NC}: $1"; }

# ---------------------------------------------------------------------------
# run_cmd <label> <cmd> [args...]
# Runs a command, captures stdout+stderr, and always prints the output so it
# is visible before the temp directory is cleaned up.
# ---------------------------------------------------------------------------
run_cmd() {
  local label="$1"; shift
  local log="$WORK_DIR/${label// /_}.log"
  info "$label"
  local rc=0
  "$@" > "$log" 2>&1 || rc=$?
  echo "─── output: $label ───"
  cat "$log"
  echo "─── end output (exit code $rc) ───"
  if [[ $rc -ne 0 ]]; then
    fail "$label"
  fi
}

# ---------------------------------------------------------------------------
# assert_file_exists <path> <label>
# ---------------------------------------------------------------------------
assert_file_exists() {
  local path="$1" label="${2:-$1}"
  [[ -f "$path" ]] || fail "$label — file not found: $path"
}

# ---------------------------------------------------------------------------
# assert_dir_not_empty <path> <label>
# ---------------------------------------------------------------------------
assert_dir_not_empty() {
  local path="$1" label="${2:-$1}"
  [[ -d "$path" ]] || fail "$label — directory not found: $path"
  local count
  count=$(find "$path" -type f | wc -l | tr -d ' ')
  [[ "$count" -gt 0 ]] || fail "$label — directory is empty: $path"
}

# ---------------------------------------------------------------------------
# assert_file_contains <path> <pattern> <label>
# ---------------------------------------------------------------------------
assert_file_contains() {
  local path="$1" pattern="$2" label="${3:-$1 contains $2}"
  grep -q "$pattern" "$path" || fail "$label — pattern '$pattern' not found in $path"
}

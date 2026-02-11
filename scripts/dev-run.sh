#!/usr/bin/env bash
set -euo pipefail
RULES=${1:-scripts/coverage-rules.sample.txt}
IN_DIR=${2:-target/scala-2.13/classes}
OUT_DIR=${3:-target/scala-2.13/classes-filtered}

echo "Using rules: $RULES"
echo "Input classes: $IN_DIR"
echo "Output classes: $OUT_DIR"

sbt "project rewriterCore" "run --in $IN_DIR --out $OUT_DIR --local-rules $RULES --dry-run"

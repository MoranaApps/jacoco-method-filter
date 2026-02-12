#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Enable the jacoco-method-filter sbt plugin in this example project.
#
# By default, the plugin dependency and build configuration are commented out
# so that cloning the repo does not trigger remote resolution.  This script
# uncomments them so you can run `sbt jacoco` or `sbt jmfVerify`.
#
# Usage:
#   cd examples/sbt-basic
#   ./enable-plugin.sh
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ── plugins.sbt ────────────────────────────────────────────────────────────
PLUGINS="$SCRIPT_DIR/project/plugins.sbt"
if grep -q '^// addSbtPlugin' "$PLUGINS"; then
  sed -i.bak 's|^// addSbtPlugin|addSbtPlugin|' "$PLUGINS"
  rm -f "$PLUGINS.bak"
  echo "✓ Enabled plugin dependency in project/plugins.sbt"
else
  echo "· Plugin dependency already enabled in project/plugins.sbt"
fi

# ── build.sbt ──────────────────────────────────────────────────────────────
BUILD="$SCRIPT_DIR/build.sbt"
if grep -q '^  // \.enablePlugins' "$BUILD"; then
  sed -i.bak 's|^  // \.enablePlugins|  .enablePlugins|' "$BUILD"
  rm -f "$BUILD.bak"
  echo "✓ Enabled .enablePlugins(JacocoFilterPlugin) in build.sbt"
else
  echo "· .enablePlugins already enabled in build.sbt"
fi

if grep -q '^// addCommandAlias' "$BUILD"; then
  sed -i.bak 's|^// addCommandAlias|addCommandAlias|' "$BUILD"
  rm -f "$BUILD.bak"
  echo "✓ Enabled command aliases in build.sbt"
else
  echo "· Command aliases already enabled in build.sbt"
fi

echo ""
echo "Done. You can now run:"
echo "  sbt jacoco          # full coverage cycle"
echo "  sbt jmfVerify       # dry-run: show what would be filtered"
echo "  sbt jmfInitRules    # generate a rules template"

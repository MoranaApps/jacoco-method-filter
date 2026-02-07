#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Test: mvn jacoco-method-filter:init-rules creates a rules file from scratch.
#
# Prerequisite: Maven plugin published locally (mvn install).
# ---------------------------------------------------------------------------
source "$(dirname "$0")/helpers.sh"

TEST_NAME="mvn-init-rules"
info "Running: $TEST_NAME"

# Copy the maven-basic example into a temp dir and remove the existing rules file.
cp -R "$REPO_ROOT/examples/maven-basic" "$WORK_DIR/project"
rm -f "$WORK_DIR/project/jmf-rules.txt"

cd "$WORK_DIR/project"

# Run init-rules — should create jmf-rules.txt
# Use fully-qualified syntax because the groupId is not in Maven's default
# plugin groups (org.apache.maven.plugins, org.codehaus.mojo).
mvn -B io.github.moranaapps:jacoco-method-filter-maven-plugin:init-rules

assert_file_exists "jmf-rules.txt" "$TEST_NAME — rules file created"
assert_file_contains "jmf-rules.txt" "jmf" "$TEST_NAME — rules file has content"

pass "$TEST_NAME"

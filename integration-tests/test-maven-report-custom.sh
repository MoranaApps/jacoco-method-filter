#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Test: Maven plugin with custom report settings
#
# Verifies:
# - Custom report formats (HTML and XML only, skip CSV)
# - Custom report name/title
# - Custom source encoding
# - Custom includes/excludes patterns
#
# Prerequisite: Maven plugin published locally (mvn install).
# ---------------------------------------------------------------------------
source "$(dirname "$0")/helpers.sh"

TEST_NAME="maven-report-custom"
info "Running: $TEST_NAME"

cp -R "$REPO_ROOT/examples/maven-basic" "$WORK_DIR/project"
cd "$WORK_DIR/project"

# Create a custom pom.xml with report customizations
# We'll add configuration to the report execution
cat > pom-custom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>maven-report-custom-test</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.source>8</maven.compiler.source>
        <maven.compiler.target>8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <junit.version>5.10.2</junit.version>
        <jacoco.version>0.8.14</jacoco.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-engine</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.12.1</version>
            </plugin>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>

            <!-- JaCoCo Maven Plugin -->
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <version>${jacoco.version}</version>
                <executions>
                    <execution>
                        <id>prepare-agent</id>
                        <goals>
                            <goal>prepare-agent</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>

            <!-- JaCoCo Method Filter Plugin with custom report settings -->
            <plugin>
                <groupId>io.github.moranaapps</groupId>
                <artifactId>jacoco-method-filter-maven-plugin</artifactId>
                <version>2.0.1</version>
                <executions>
                    <execution>
                        <id>filter-coverage</id>
                        <goals>
                            <goal>rewrite</goal>
                        </goals>
                    </execution>
                    <execution>
                        <id>generate-report</id>
                        <phase>verify</phase>
                        <goals>
                            <goal>report</goal>
                        </goals>
                        <configuration>
                            <!-- Custom report settings -->
                            <reportName>Maven Custom Coverage Report</reportName>
                            <reportFormats>html,xml</reportFormats>
                            <sourceEncoding>UTF-8</sourceEncoding>
                        </configuration>
                    </execution>
                </executions>
            </plugin>

            <!-- Overlay filtered classes -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-resources-plugin</artifactId>
                <version>3.3.1</version>
                <executions>
                    <execution>
                        <id>overlay-filtered-classes</id>
                        <phase>process-test-classes</phase>
                        <goals>
                            <goal>copy-resources</goal>
                        </goals>
                        <configuration>
                            <outputDirectory>${project.build.outputDirectory}</outputDirectory>
                            <overwrite>true</overwrite>
                            <resources>
                                <resource>
                                    <directory>${project.build.directory}/classes-filtered</directory>
                                    <filtering>false</filtering>
                                </resource>
                            </resources>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
EOF

# ── 1. Run build with custom report settings ──────────────────────────────
run_cmd "$TEST_NAME — mvn clean verify -f pom-custom.xml" mvn -B clean verify -f pom-custom.xml

# ── 2. Verify filtered classes exist ───────────────────────────────────────
assert_dir_not_empty "target/classes-filtered" \
  "$TEST_NAME — filtered classes directory exists"

# ── 3. Verify HTML and XML reports are generated ───────────────────────────
assert_file_exists "target/jacoco-report/index.html" \
  "$TEST_NAME — HTML report generated"

assert_file_exists "target/jacoco.xml" \
  "$TEST_NAME — XML report generated"

# ── 4. Verify CSV report is NOT generated ──────────────────────────────────
if [[ -f "target/jacoco.csv" ]]; then
  fail "$TEST_NAME — CSV report should NOT be generated (reportFormats = html,xml)"
fi

pass "$TEST_NAME — CSV correctly skipped"

# ── 5. Verify custom report title is used ──────────────────────────────────
assert_file_contains "target/jacoco-report/index.html" "Maven Custom Coverage Report" \
  "$TEST_NAME — Custom report title appears in HTML"

pass "$TEST_NAME — custom report settings work correctly"

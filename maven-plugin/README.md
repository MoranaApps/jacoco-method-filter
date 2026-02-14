# JaCoCo Method Filter Maven Plugin

Maven plugin for filtering JaCoCo coverage by annotating methods based on configurable rules.

## Table of Contents

- [Requirements](#requirements)
- [Implementation Details](#implementation-details)
- [Installation](#installation)
- [Available Goals](#available-goals)
- [Goal Details](#goal-details)
  - [`jacoco-method-filter:rewrite`](#jacoco-method-filterrewrite)
  - [`jacoco-method-filter:verify`](#jacoco-method-filterverify)
  - [`jacoco-method-filter:report`](#jacoco-method-filterreport)
  - [`jacoco-method-filter:init-rules`](#jacoco-method-filterinit-rules)

## Requirements

- Java 8 or higher
- Maven 3.6 or higher
- `jacoco-method-filter-core_2.12:2.0.0` dependency (automatically included)
- JaCoCo CLI 0.8.14 (automatically included)

## Implementation Details

The plugin executes the CoverageRewriter CLI tool by:

1. Building the runtime classpath from Maven dependencies
2. Locating the Java executable from the current JVM
3. Invoking the tool as a subprocess with appropriate arguments
4. Capturing and logging output with proper categorization

The report goal uses the JaCoCo CLI to generate HTML and XML reports using the filtered classes.

## Installation

Add to your `pom.xml` (recommended: inside a coverage profile):

```xml
<profiles>
  <profile>
    <id>code-coverage</id>
    <build>
      <plugins>
        <!-- JaCoCo Maven Plugin (for agent) -->
        <plugin>
          <groupId>org.jacoco</groupId>
          <artifactId>jacoco-maven-plugin</artifactId>
          <version>0.8.14</version>
          <executions>
            <execution>
              <goals>
                <goal>prepare-agent</goal>
              </goals>
            </execution>
          </executions>
        </plugin>

        <!-- JaCoCo Method Filter Plugin -->
        <plugin>
          <groupId>io.github.moranaapps</groupId>
          <artifactId>jacoco-method-filter-maven-plugin</artifactId>
          <version>2.0.0</version>
          <executions>
            <execution>
              <goals>
                <goal>rewrite</goal>
                <goal>report</goal>
              </goals>
            </execution>
          </executions>
        </plugin>

        <!-- Overlay filtered classes before tests -->
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
  </profile>
</profiles>
```

Run coverage:

```bash
mvn jacoco-method-filter:init-rules   # create default rules file (one-time)
mvn clean verify -Pcode-coverage      # run tests with coverage filtering
```

## Available Goals

| Goal | Description |
|------|-------------|
| `init-rules` | Creates a default `jmf-rules.txt` file in the project root with sensible defaults |
| `rewrite` | Rewrites compiled classes, applying the `@CoverageGenerated` annotation to matched methods |
| `verify` | Scans compiled classes and reports which methods would be matched by the configured rules (read-only) |
| `report` | Generates JaCoCo HTML/XML reports from the filtered classes and execution data |

## Goal Details

### `jacoco-method-filter:rewrite`

Rewrites compiled class files to add `@CoverageGenerated` annotations to methods matching the configured rules.

**Default Phase:** `process-test-classes`

**Parameters:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `jmf.globalRules` | `String` | — | Global rules source (path or URL). Can be combined with `localRules`. |
| `jmf.localRules` | `File` | `${project.basedir}/jmf-rules.txt` | Local rules file. Can be combined with `globalRules`. |
| `jmf.inputDirectory` | `File` | `${project.build.outputDirectory}` | Input classes directory. |
| `jmf.outputDirectory` | `File` | `${project.build.directory}/classes-filtered` | Output classes directory. |
| `jmf.dryRun` | `boolean` | `false` | Dry run mode — no files modified. |
| `jmf.skip` | `boolean` | `false` | Skip execution. |

> **Note:** `globalRules` and `localRules` can be used together; global rules are loaded first,
> then local rules are appended. By default, `localRules` points to `jmf-rules.txt` in the
> project root.

**Example (simple — uses default `jmf-rules.txt`):**

```xml
<plugin>
    <groupId>io.github.moranaapps</groupId>
    <artifactId>jacoco-method-filter-maven-plugin</artifactId>
    <version>2.0.0</version>
    <executions>
        <execution>
            <goals>
                <goal>rewrite</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

**Example (global + local rules):**

```xml
<plugin>
    <groupId>io.github.moranaapps</groupId>
    <artifactId>jacoco-method-filter-maven-plugin</artifactId>
    <version>2.0.0</version>
    <configuration>
        <globalRules>https://example.com/team-rules.txt</globalRules>
        <localRules>${project.basedir}/jmf-rules.txt</localRules>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>rewrite</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### `jacoco-method-filter:verify`

Scans compiled classes and reports which methods would be matched by the configured rules without modifying any files.

**Parameters:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `jmf.globalRules` | `String` | — | Global rules source (path or URL). Can be combined with `localRules`. |
| `jmf.localRules` | `File` | `${project.basedir}/jmf-rules.txt` | Local rules file. Can be combined with `globalRules`. |
| `jmf.inputDirectory` | `File` | `${project.build.outputDirectory}` | Input classes directory. |
| `jmf.skip` | `boolean` | `false` | Skip execution. |

**Example:**

```bash
mvn compile jacoco-method-filter:verify
```

### `jacoco-method-filter:report`

Generates JaCoCo coverage reports (HTML, XML, CSV) using filtered classes.

**Default Phase:** `verify`

**Parameters:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `jmf.jacocoExecFile` | `File` | `${project.build.directory}/jacoco.exec` | JaCoCo execution data file |
| `jmf.classesDirectory` | `File` | `${project.build.directory}/classes-filtered` | Filtered classes directory for report generation |
| `jmf.sourceDirectories` | `File[]` | Derived from `project.getCompileSourceRoots()`, falls back to `src/main/java` | Source directories for report generation |
| `jmf.reportDirectory` | `File` | `${project.build.directory}/jacoco-report` | HTML report output directory |
| `jmf.xmlOutputFile` | `File` | `${project.build.directory}/jacoco.xml` | XML report output file |
| `jmf.csvOutputFile` | `File` | `${project.build.directory}/jacoco.csv` | CSV report output file |
| `jmf.reportName` | `String` | `${project.name}` | Title used in the HTML report header |
| `jmf.reportFormats` | `String` | `"html,xml,csv"` | Comma-separated list of report formats to generate (valid: `html`, `xml`, `csv`) |
| `jmf.sourceEncoding` | `String` | `"UTF-8"` | Source file encoding for report generation |
| `jmf.jacocoIncludes` | `String` | `"**"` | *(Not used in report goal)* Colon-separated include patterns for JaCoCo agent. Use this with the `jacoco-maven-plugin` configuration instead. |
| `jmf.jacocoExcludes` | `String` | `""` | *(Not used in report goal)* Colon-separated exclude patterns for JaCoCo agent. Use this with the `jacoco-maven-plugin` configuration instead. |
| `jmf.skip` | `boolean` | `false` | Skip execution |
| `jmf.skipIfExecMissing` | `boolean` | `true` | Skip if exec file missing |

**Example (basic):**

```xml
<execution>
    <id>report</id>
    <phase>verify</phase>
    <goals>
        <goal>report</goal>
    </goals>
</execution>
```

**Example (customized report generation):**

```xml
<plugin>
    <groupId>io.github.moranaapps</groupId>
    <artifactId>jacoco-method-filter-maven-plugin</artifactId>
    <version>2.0.0</version>
    <executions>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals>
                <goal>report</goal>
            </goals>
            <configuration>
                <!-- Custom report title -->
                <reportName>My Project Coverage</reportName>
                
                <!-- Generate only HTML and XML (skip CSV) -->
                <reportFormats>html,xml</reportFormats>
                
                <!-- Custom source encoding -->
                <sourceEncoding>UTF-8</sourceEncoding>
                
                <!-- Custom output locations -->
                <reportDirectory>${project.build.directory}/coverage-report</reportDirectory>
                <xmlOutputFile>${project.build.directory}/coverage.xml</xmlOutputFile>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**Note on includes/excludes:** The `jacocoIncludes` and `jacocoExcludes` parameters exist for consistency with the sbt
 plugin but are not used by the report goal. To filter which classes are instrumented, configure the
  `jacoco-maven-plugin` instead:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.14</version>
    <configuration>
        <includes>
            <include>com/example/core/**</include>
            <include>com/example/api/**</include>
        </includes>
        <excludes>
            <exclude>com/example/generated/**</exclude>
        </excludes>
    </configuration>
    <!-- ... executions ... -->
</plugin>
```

### `jacoco-method-filter:init-rules`

Creates a `jmf-rules.txt` file from template (manual invocation only).

**Parameters:**

- `jmf.localRules` - Target rules file path (default: `${project.basedir}/jmf-rules.txt`)
- `jmf.overwrite` - Overwrite existing file (default: `false`)

**Example:**

```bash
mvn jacoco-method-filter:init-rules
```

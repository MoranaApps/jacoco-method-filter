# JaCoCo Method Filter Maven Plugin

Maven plugin for filtering JaCoCo coverage by annotating methods based on configurable rules.

## Goals

### `jacoco-method-filter:rewrite`

Rewrites compiled class files to add `@CoverageGenerated` annotations to methods matching the configured rules.

**Default Phase:** `process-test-classes`

**Parameters:**
- `jmf.rulesFile` - Rules file path (default: `${project.basedir}/jmf-rules.txt`)
- `jmf.inputDirectory` - Input classes directory (default: `${project.build.outputDirectory}`)
- `jmf.outputDirectory` - Output classes directory (default: `${project.build.directory}/classes-filtered`)
- `jmf.dryRun` - Dry run mode, no files modified (default: `false`)
- `jmf.skip` - Skip execution (default: `false`)

**Example:**
```xml
<plugin>
    <groupId>io.github.moranaapps</groupId>
    <artifactId>jacoco-method-filter-maven-plugin</artifactId>
    <version>1.2.0</version>
    <executions>
        <execution>
            <goals>
                <goal>rewrite</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### `jacoco-method-filter:report`

Generates JaCoCo HTML and XML reports using filtered classes.

**Parameters:**
- `jmf.jacocoExecFile` - JaCoCo exec file (default: `${project.build.directory}/jacoco.exec`)
- `jmf.classesDirectory` - Classes directory for report (default: `${project.build.directory}/classes-filtered`)
- `jmf.sourceDirectories` - Source directories (default: `src/main/java`)
- `jmf.reportDirectory` - HTML report output (default: `${project.build.directory}/jacoco-html`)
- `jmf.xmlOutputFile` - XML report output (default: `${project.build.directory}/jacoco.xml`)
- `jmf.skip` - Skip execution (default: `false`)
- `jmf.skipIfExecMissing` - Skip if exec file missing (default: `true`)

**Example:**
```xml
<execution>
    <id>report</id>
    <phase>verify</phase>
    <goals>
        <goal>report</goal>
    </goals>
</execution>
```

### `jacoco-method-filter:init-rules`

Creates a `jmf-rules.txt` file from template (manual invocation only).

**Parameters:**
- `jmf.rulesFile` - Target rules file (default: `${project.basedir}/jmf-rules.txt`)
- `jmf.overwrite` - Overwrite existing file (default: `false`)

**Example:**
```bash
mvn jacoco-method-filter:init-rules
```

## Complete Example

```xml
<build>
    <plugins>
        <plugin>
            <groupId>io.github.moranaapps</groupId>
            <artifactId>jacoco-method-filter-maven-plugin</artifactId>
            <version>1.2.0</version>
            <executions>
                <execution>
                    <id>rewrite-classes</id>
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
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

## Requirements

- Java 8 or higher
- Maven 3.6 or higher
- `jacoco-method-filter-core_2.12:1.2.0` dependency (automatically included)
- JaCoCo CLI 0.8.12 (automatically included)

## Implementation Details

The plugin executes the CoverageRewriter CLI tool by:
1. Building the runtime classpath from Maven dependencies
2. Locating the Java executable from the current JVM
3. Invoking the tool as a subprocess with appropriate arguments
4. Capturing and logging output with proper categorization

The report goal uses the JaCoCo CLI to generate HTML and XML reports using the filtered classes.

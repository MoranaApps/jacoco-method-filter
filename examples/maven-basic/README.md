# Maven Basic Example

Minimal working example demonstrating **jacoco-method-filter-maven-plugin** integration.

## What This Demonstrates

This project shows how to:
- Configure the plugin in a standalone Maven project
- Filter boilerplate methods (getters/setters/equals/hashCode/toString) from coverage reports
- Run tests with filtered coverage analysis
- Generate HTML and XML coverage reports

## Project Structure

```
maven-basic/
├── pom.xml                      # Maven configuration with plugin setup
├── jmf-rules.txt                # Method filtering rules
├── src/main/java/example/       # Sample application code
└── src/test/java/example/       # JUnit 5 tests
```

## Prerequisites

- Java 8 or later
- Maven 3.6+
- jacoco-method-filter plugin installed (see main project README for installation)

## Running Coverage Analysis

Execute tests with coverage by activating the `code-coverage` profile:

```bash
mvn clean verify -Pcode-coverage
```

This runs:
1. `maven-compiler-plugin` - Compiles source and test code
2. `jacoco-maven-plugin` - Instruments code and attaches JaCoCo agent
3. `jacoco-method-filter-maven-plugin:rewrite` - Annotates filtered methods as @Generated
4. `maven-surefire-plugin` - Runs tests using filtered classes
5. `jacoco-method-filter-maven-plugin:report` - Generates coverage reports

**Note**: Without `-Pcode-coverage`, tests run normally without coverage instrumentation.

## Viewing Results

After running `mvn clean verify -Pcode-coverage`, open the coverage report:

```bash
# HTML report
open target/jacoco-html/index.html

# XML report for CI/CD tools
cat target/jacoco.xml
```

**Filtered classes location**: `target/classes-filtered/`

## Configuration Details

### Plugin Execution Flow

1. **prepare-agent** (jacoco-maven-plugin)
   - Attaches JaCoCo agent to JVM
   - Creates `target/jacoco.exec` during test execution

2. **rewrite** (jacoco-method-filter-maven-plugin) - Phase: `process-test-classes`
   - Reads `jmf-rules.txt`
   - Scans compiled classes in `target/classes`
   - Writes filtered classes to `target/classes-filtered/`
   - Methods matching rules get `@Generated` annotation

3. **test** (maven-surefire-plugin)
   - Configured with `additionalClasspathElements` pointing to `target/classes-filtered/`
   - Tests run against filtered classes
   - JaCoCo ignores @Generated methods

4. **report** (jacoco-method-filter-maven-plugin) - Phase: `verify`
   - Generates HTML report in `target/jacoco-html/`
   - Generates XML report at `target/jacoco.xml`

### Rules File

The `jmf-rules.txt` file uses glob patterns to match methods:

```
example.*#get*(*)       # All getters in example package
example.*#set*(*) ret:V # All setters returning void
example.*#equals(*)     # equals methods
example.*#hashCode(*)   # hashCode methods
example.*#toString(*)   # toString methods
```

Run `mvn jacoco-method-filter:init-rules` to generate a comprehensive template.

## Expected Coverage Results

With filtering enabled:
- **Business logic methods**: Fully covered and reported
- **Getters/setters**: Excluded from coverage metrics
- **equals/hashCode/toString**: Excluded from coverage metrics

Without filtering (standard JaCoCo):
- Coverage percentages diluted by boilerplate code
- Reports show trivial methods as "uncovered"

## Customization

### Change Rules File Location

```xml
<plugin>
    <groupId>io.github.moranaapps</groupId>
    <artifactId>jacoco-method-filter-maven-plugin</artifactId>
    <configuration>
        <rulesFile>${project.basedir}/custom-rules.txt</rulesFile>
    </configuration>
</plugin>
```

### Change Output Directory

```xml
<configuration>
    <outputDirectory>${project.build.directory}/my-filtered-classes</outputDirectory>
</configuration>
```

### Dry Run Mode

Preview what would be filtered without writing files:

```bash
mvn jacoco-method-filter:rewrite -Djmf.dryRun=true
```

## Troubleshooting

**Issue**: Tests run but no coverage report generated

**Solution**: Ensure you activate the profile and run the `verify` phase:
```bash
mvn clean verify -Pcode-coverage  # Correct
mvn clean test -Pcode-coverage    # Won't trigger report goal
```

**Issue**: Coverage shows 0% for all classes

**Solution**: Check that Surefire uses filtered classes:
```xml
<additionalClasspathElements>
    <additionalClasspathElement>${project.build.directory}/classes-filtered</additionalClasspathElement>
</additionalClasspathElements>
```

## Next Steps

- Review `jmf-rules.txt` and adjust patterns for your project
- Add project-specific rules for DTOs, builders, or generated code
- Integrate with CI/CD pipelines using the XML report
- See integration examples in `examples/` directory

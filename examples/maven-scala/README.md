# Maven Scala Example

Minimal working example demonstrating **jacoco-method-filter-maven-plugin** with **Scala** sources.

## What This Demonstrates

This project shows how to:

- Use the Maven plugin with Scala code compiled via `scala-maven-plugin`
- Filter Scala case class boilerplate (copy, productArity, equals, hashCode, toString, …) from coverage
- Run ScalaTest tests with filtered coverage analysis
- Generate HTML and XML coverage reports

## Project Structure

```text
maven-scala/
├── pom.xml                       # Maven configuration with Scala + plugin setup
├── jmf-rules.txt                 # Scala-oriented method filtering rules
├── src/main/scala/example/       # Scala application code (case classes)
└── src/test/scala/example/       # ScalaTest specs
```

## Prerequisites

- Java 8 or later
- Maven 3.6+
- jacoco-method-filter plugin published (see main project README)

## Running Coverage Analysis

Execute tests with coverage by activating the `code-coverage` profile:

```bash
mvn clean verify -Pcode-coverage
```

This runs:

1. `scala-maven-plugin` – Compiles Scala source and test code
2. `jacoco-maven-plugin` – Instruments code and attaches JaCoCo agent
3. `jacoco-method-filter-maven-plugin:rewrite` – Annotates filtered methods as @Generated
4. `scalatest-maven-plugin` – Runs ScalaTest specs
5. `jacoco-method-filter-maven-plugin:report` – Generates coverage reports

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

## Expected Coverage Results

With filtering enabled:

- **Business logic methods** (`distanceFromOrigin`, `quadrant`, `applyAmplification`, …):
  Fully covered and reported
- **Case class boilerplate** (`copy`, `productArity`, `productElement`, `equals`, `hashCode`, `toString`): Excluded
 from coverage
- **Synthetic/bridge methods**: Excluded from coverage

Without filtering (standard JaCoCo):

- Coverage percentages diluted by compiler-generated methods
- Reports show trivial Scala boilerplate as "uncovered"

## Customization

### Dry Run Mode

Preview what would be filtered without writing files:

```bash
mvn jacoco-method-filter:rewrite -Djmf.dryRun=true
```

### Generate a Fresh Rules File

```bash
mvn jacoco-method-filter:init-rules
```

## Differences from maven-basic

| Aspect | maven-basic (Java) | maven-scala (Scala) |
|--------|-------------------|---------------------|
| Compiler plugin | `maven-compiler-plugin` | `scala-maven-plugin` |
| Test framework | JUnit 5 | ScalaTest |
| Test runner | `maven-surefire-plugin` | `scalatest-maven-plugin` |
| Source layout | `src/main/java` | `src/main/scala` |
| Filtered boilerplate | getters/setters/equals | case class copy/product/equals |
| Rules focus | Java bean patterns | Scala compiler-generated patterns |

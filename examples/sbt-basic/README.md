# sbt Basic Example

This example demonstrates the recommended way to integrate jacoco-method-filter using the published sbt plugin.

## Project Structure

```txt
sbt-basic/
├── build.sbt                   # Build configuration with plugin enabled
├── project/
│   ├── build.properties        # sbt version
│   └── plugins.sbt             # Plugin dependency
├── jmf-rules.txt               # Coverage filter rules
└── src/
    ├── main/scala/example/     # Sample code
    └── test/scala/example/     # Sample tests
```

## Setup

1. **Plugin Configuration** (`project/plugins.sbt`):

   ```scala
   addSbtPlugin("io.github.moranaapps" % "jacoco-method-filter-sbt" % "1.2.0")
   ```

2. **Build Configuration** (`build.sbt`):

   ```scala
   lazy val root = (project in file("."))
     .enablePlugins(JacocoFilterPlugin)
     .settings(
       name := "sbt-basic-example",
       scalaVersion := "2.13.14"
     )

   // Optional: Command aliases for convenience
   addCommandAlias("jacoco", "; jacocoOn; clean; test; jacocoReportAll; jacocoOff")
   addCommandAlias("jacocoOff", "; set every jacocoPluginEnabled := false")
   addCommandAlias("jacocoOn", "; set every jacocoPluginEnabled := true")
   ```

3. **Rules File** (`jmf-rules.txt`):
   Contains patterns for methods to exclude from coverage.

## Running Coverage

### Full Coverage Cycle

```bash
sbt jacoco
```

This will:

1. Enable JaCoCo instrumentation
2. Clean the build
3. Run tests with coverage
4. Generate filtered coverage reports
5. Disable JaCoCo instrumentation

### Manual Steps

```bash
# Enable coverage
sbt "set every jacocoPluginEnabled := true"

# Run tests
sbt clean test

# Generate report
sbt jacocoReportAll

# Disable coverage (optional)
sbt "set every jacocoPluginEnabled := false"
```

## Output

After running coverage, you'll find:

- **HTML Report**: `target/jacoco/report/index.html`
- **XML Report**: `target/jacoco/report/jacoco.xml`
- **CSV Report**: `target/jacoco/report/jacoco.csv`
- **Filtered Classes**: `target/jmf/classes-filtered/`

## Notes

- The plugin automatically adds JaCoCo dependencies to your build
- Tests are forked automatically to enable the JaCoCo agent
- The rewriter runs before tests to apply coverage filters
- Coverage is disabled by default to avoid overhead during normal development

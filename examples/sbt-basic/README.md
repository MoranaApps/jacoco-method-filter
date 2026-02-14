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

The plugin is already enabled in this example.

Generate rules (recommended if you want to reset from template):

```bash
cd examples/sbt-basic
sbt jmfInitRules
```

This writes `jmf-rules.txt` with sensible defaults for Scala projects.

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

- **HTML Report**: `target/scala-2.12/jacoco-report/index.html`
- **XML Report**: `target/scala-2.12/jacoco-report/jacoco.xml`
- **CSV Report**: `target/scala-2.12/jacoco-report/jacoco.csv`
- **Filtered Classes**: `target/scala-2.12/classes-filtered/`

## Notes

- See [`sbt-plugin/README.md`](../../sbt-plugin/README.md) for complete task and settings reference

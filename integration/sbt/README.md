# sbt Plugin Integration Files (Legacy/Manual Approach)

This directory contains the Scala source files for the sbt plugin that can be manually copied into your project.

## ⚠️ Recommended: Use the Published Plugin

**For most users, we recommend using the published sbt plugin from Maven Central instead of copying these files.**

See the main [README](../../README.md#with-sbt-plugin) for the recommended integration approach using:

```scala
addSbtPlugin("io.github.moranaapps" % "jacoco-method-filter-sbt" % "1.2.0")
```

## When to Use This Manual Approach

The manual copy-paste integration is only recommended for:

- **Advanced debugging**: When you need to modify the plugin logic for troubleshooting
- **Custom builds**: When you have non-standard build requirements that need plugin customization
- **Bleeding edge**: When you want to test unreleased plugin changes from the repository

## How to Use (Manual Integration)

If you have determined that you need the manual integration:

1. Copy both files into your `{root}/project` directory:
   - `JacocoBaseKeysPlugin.scala`
   - `FilteredJacocoAgentPlugin.scala`

2. Enable the plugin in your `build.sbt`:

   ```scala
   lazy val myModule = (project in file("my-module"))
     .enablePlugins(FilteredJacocoAgentPlugin)
   ```

3. Follow the rest of the setup instructions in the main [README](../../README.md).

## Maintenance Warning

**Important**: Files copied manually will not automatically update when new versions are released. You'll need
 to manually track and update them to get bug fixes and new features. The published plugin approach handles versioning
  automatically through your build configuration.

## Files

- **JacocoBaseKeysPlugin.scala**: Base plugin that defines core keys and provides no-op defaults for all projects
- **FilteredJacocoAgentPlugin.scala**: Main plugin implementation that configures JaCoCo agent, runs the method
 filter rewriter, and generates reports

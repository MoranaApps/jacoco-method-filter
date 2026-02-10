lazy val root = (project in file(""))
  // NOTE: `.enablePlugins(JacocoFilterPlugin)` is required for jmfVerify, jmfInitRules,
  // jacocoReportAll and the other plugin tasks to be available. It is commented out here
  // because the plugin dependency in `project/plugins.sbt` is also disabled by default.
  // Uncomment both to enable the full coverage workflow.
  // .enablePlugins(JacocoFilterPlugin)
  .settings(
    name := "sbt-basic-example",
    organization := "io.github.moranaapps",
    scalaVersion := "2.12.21",
    version := "0.1.0-SNAPSHOT",
    
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.18" % Test
    )
  )

// Optional: command aliases for convenience.
//
// They are intentionally commented out to keep this example build minimal and to avoid confusing
// "unknown command/setting" errors when the plugin is not enabled (e.g. when the plugin line in
// `project/plugins.sbt` is still commented out).
//
// If you enable the plugin (see `examples/sbt-basic/README.md`), you can uncomment these aliases
// to get a one-command coverage cycle via `sbt jacoco`.
// addCommandAlias("jacoco", "; jacocoOn; clean; test; jacocoReportAll; jacocoOff")
// addCommandAlias("jacocoOff", "; set every jacocoPluginEnabled := false")
// addCommandAlias("jacocoOn", "; set every jacocoPluginEnabled := true")

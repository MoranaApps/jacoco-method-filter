// CI fixture: minimal single-module project pinned to sbt 1.9.x.
// Guards against regressions that use sbt APIs newer than 1.9 (e.g. the 3-arg
// Command.process overload added in sbt 1.10.0, see issue #76).
lazy val root = (project in file(""))
  .enablePlugins(JacocoFilterPlugin)
  .settings(
    name := "sbt-19x-test",
    organization := "io.github.moranaapps",
    scalaVersion := "2.12.21",
    version := "0.1.0-SNAPSHOT",

    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.18" % Test
    )
  )

addCommandAlias("jacoco", "; jacocoOn; clean; test; jacocoReportAll; jacocoOff")
addCommandAlias("jacocoOff", "; set every jacocoPluginEnabled := false")
addCommandAlias("jacocoOn", "; set every jacocoPluginEnabled := true")

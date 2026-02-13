// CI fixture: Test with Scala 2.11 to verify cross-build compatibility
lazy val root = (project in file(""))
  .enablePlugins(JacocoFilterPlugin)
  .settings(
    name := "sbt-scala211-test",
    organization := "io.github.moranaapps",
    scalaVersion := "2.11.12",
    version := "0.1.0-SNAPSHOT",
    
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.0.9" % Test
    )
  )

addCommandAlias("jacoco", "; jacocoOn; clean; test; jacocoReportAll; jacocoOff")
addCommandAlias("jacocoOff", "; set every jacocoPluginEnabled := false")
addCommandAlias("jacocoOn", "; set every jacocoPluginEnabled := true")

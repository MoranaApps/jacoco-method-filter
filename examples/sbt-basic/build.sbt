lazy val root = (project in file(""))
  .enablePlugins(JacocoFilterPlugin)
  .settings(
    name := "sbt-basic-example",
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

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
    
    // Optional: customize JaCoCo report settings
    // jacocoReportName := "My Project Coverage"
    // jacocoReportFormats := Set("html", "xml")  // skip CSV
    // jacocoSourceEncoding := "UTF-8"
    // jacocoIncludes := Seq("com/example/**")
    // jacocoExcludes := Seq("com/example/generated/**")
  )

addCommandAlias("jacoco", "; jacocoOn; clean; test; jacocoReportAll; jacocoOff")
addCommandAlias("jacocoOff", "; set every jacocoPluginEnabled := false")
addCommandAlias("jacocoOn", "; set every jacocoPluginEnabled := true")

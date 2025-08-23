ThisBuild / scalaVersion := "2.13.14"
ThisBuild / organization := "MoranaApps"
ThisBuild / version      := "0.1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .aggregate(rewriterCore)
  .settings(
    name := "jacoco-method-filter",
    publish / skip := true
  )

lazy val rewriterCore = (project in file("rewriter-core"))
  .settings(
    name := "jacoco-method-filter-core",
    libraryDependencies ++= Seq(
      "org.ow2.asm" % "asm"          % "9.6",
      "org.ow2.asm" % "asm-commons"  % "9.6",
      "com.github.scopt" %% "scopt"  % "4.1.0",
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    ),
    Compile / mainClass := Some("io.moranaapps.jacocomethodfilter.CoverageRewriter")
  )

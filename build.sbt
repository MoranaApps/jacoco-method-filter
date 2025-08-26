import scala.collection.immutable.Seq

ThisBuild / scalaVersion := "2.13.14"
ThisBuild / organization := "MoranaApps"
ThisBuild / version      := "0.1.0-SNAPSHOT"

// --- core tool (2.13)
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

// --- sbt plugin (2.12 / sbt 1.x)
lazy val sbtPlugin = (project in file("sbt-plugin"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "jacoco-method-filter-sbt",
    scalaVersion := "2.12.19",
    libraryDependencies += "org.jacoco" % "org.jacoco.cli" % "0.8.12" classifier "nodeps"
  )

// --- aggregator only
lazy val root = (project in file("."))
  .aggregate(rewriterCore, sbtPlugin)
  .settings(
    name := "jacoco-method-filter",
    publish / skip := true
  )

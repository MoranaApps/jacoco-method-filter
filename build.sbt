import xerial.sbt.Sonatype._

// ---- global ---------------------------------------------------------------
ThisBuild / organization   := "io.github.moranaapps"
ThisBuild / scalaVersion   := "2.13.14"             // default
ThisBuild / crossScalaVersions := Seq("2.12.19", "2.13.14")
ThisBuild / version        := "1.0.0"
ThisBuild / versionScheme  := Some("early-semver")

// Central (bundle flow)
ThisBuild / sonatypeCredentialHost := sonatypeCentralHost
ThisBuild / publishTo              := sonatypePublishToBundle.value

// Project metadata
ThisBuild / homepage := Some(url("https://github.com/MoranaApps/jacoco-method-filter"))
ThisBuild / licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / scmInfo  := Some(
  ScmInfo(
    url("https://github.com/MoranaApps/jacoco-method-filter"),
    "scm:git:git@github.com:MoranaApps/jacoco-method-filter.git"
  )
)
ThisBuild / developers := List(
  Developer("moranaapps", "MoranaApps", "miroslavpojer@seznam.cz", url("https://github.com/MoranaApps"))
)

// ---- modules --------------------------------------------------------------

// CORE LIB (publish this)
lazy val rewriterCore = (project in file("rewriter-core"))
  .settings(
    name := "jacoco-method-filter-core",
    publish / skip := false,

    // CLI entrypoint you already have
    Compile / mainClass := Some("io.moranaapps.jacocomethodfilter.CoverageRewriter"),

    libraryDependencies ++= Seq(
      "org.ow2.asm"            %  "asm"                      % "9.6",
      "org.ow2.asm"            %  "asm-commons"              % "9.6",
      "com.github.scopt"       %% "scopt"                    % "4.1.0",
      // brings 2.13 APIs/collections shims to 2.12
      "org.scala-lang.modules" %% "scala-collection-compat"  % "2.12.0",
      "org.scalatest"          %% "scalatest"                % "3.2.19" % Test
    ),

    Compile / doc / scalacOptions ++= Seq("-no-link-warnings")
  )

// AGGREGATOR (don’t publish)
lazy val root = (project in file("."))
  .aggregate(rewriterCore)
  .settings(
    name := "jacoco-method-filter",
    publish / skip := true
  )

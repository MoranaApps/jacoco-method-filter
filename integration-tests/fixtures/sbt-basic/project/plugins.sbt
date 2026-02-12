// CI fixture: plugin dependency is enabled (unlike examples/sbt-basic where it is
// commented out for safe cloning).  Integration tests copy this directory instead
// of using fragile sed-based uncommenting.
addSbtPlugin("io.github.moranaapps" % "jacoco-method-filter-sbt" % "1.2.0")

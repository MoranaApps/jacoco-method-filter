// CI fixture: verifies the plugin loads and runs on sbt 1.9.x (the 3-arg
// Command.process overload used up to v2.4.0 only exists in sbt >= 1.10.0).
addSbtPlugin("io.github.moranaapps" % "jacoco-method-filter-sbt" % "2.5.0")

# sbt-basic CI Fixture

This is a CI-ready variant of `examples/sbt-basic/` with the plugin **already
enabled** in `build.sbt` and `project/plugins.sbt`.

Integration test scripts copy this fixture into a temp directory and then
overlay the source & rules files from `examples/sbt-basic/`.  This avoids
the fragile `sed`-based uncommenting that was previously needed.

**Do not edit source files or `jmf-rules.txt` here — they live in
`examples/sbt-basic/` and are copied at test time.**

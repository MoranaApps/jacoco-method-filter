package io.moranaapps.jacocomethodfilter

import scopt.OptionParser

import java.nio.file.{Files, Paths}

/** CLI argument parser for CoverageRewriter. Separated from the main entry point
  * so that `main` stays focused on orchestration while parsing/validation logic
  * is self-contained and independently testable.
  */
private[jacocomethodfilter] object CoverageRewriterCli {

  /** Parses command-line arguments into a validated CliConfig.
    *
    * @param args Command-line arguments.
    * @return Some(config) if parsing succeeds, None if parsing fails or --help is used.
    */
  def parse(args: Array[String]): Option[CliConfig] =
    parser.parse(args, CliConfig())

  private lazy val parser: OptionParser[CliConfig] =
    new OptionParser[CliConfig]("jacoco-method-filter") {
      opt[String]("in")
        .required()
        .action((v, c) => c.copy(in = Paths.get(v)))
        .text("Input classes directory")

      opt[String]("out")
        .optional()
        .action((v, c) => c.copy(out = Some(Paths.get(v))))
        .text("Output classes directory (required unless --verify is used)")

      opt[String]("global-rules")
        .optional()
        .action((v, c) => c.copy(globalRules = Some(v)))
        .text("Global rules file path or URL")

      opt[String]("local-rules")
        .optional()
        .action((v, c) => c.copy(localRules = Some(Paths.get(v))))
        .text("Local rules file path")

      opt[Unit]("dry-run")
        .action((_, c) => c.copy(dryRun = true))
        .text("Only print matches; do not modify classes")

      opt[Unit]("verify")
        .action((_, c) => c.copy(verify = true))
        .text("Read-only scan: list all methods that would be excluded by rules")

      opt[Unit]("verify-suggest-includes")
        .action((_, c) => c.copy(verifySuggestIncludes = true))
        .text("When used with --verify, suggest include rules for likely human-written excluded methods")

      checkConfig { cfg =>
        if (!Files.isDirectory(cfg.in)) {
          failure("--in must exist and be a directory")
        } else if (cfg.verifySuggestIncludes && !cfg.verify) {
          failure("--verify-suggest-includes requires --verify")
        } else if (!cfg.verify && cfg.out.isEmpty) {
          failure("--out is required when not in verify mode")
        } else if (cfg.globalRules.isEmpty && cfg.localRules.isEmpty) {
          failure("At least one of --global-rules or --local-rules must be specified")
        } else {
          success
        }
      }
    }
}

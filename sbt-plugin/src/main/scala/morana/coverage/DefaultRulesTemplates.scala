package morana.coverage

import scala.io.Source

/** Default rules templates for jmfInitRules task, loaded from resources. */
object DefaultRulesTemplates {

  /** 
   * Default rules for Scala-only projects.
   * Loaded from jmf-rules.template.txt resource file.
   */
  val scala: String = {
    val stream = getClass.getResourceAsStream("/jmf-rules.template.txt")
    if (stream == null) {
      sys.error("Failed to load jmf-rules.template.txt from resources")
    }
    try {
      Source.fromInputStream(stream, "UTF-8").mkString
    } finally {
      stream.close()
    }
  }

  /**
   * Rules template for Scala + Java mixed projects.
   * Uses the same template as scala (from jmf-rules.template.txt).
   */
  val scalaJava: String = scala
}

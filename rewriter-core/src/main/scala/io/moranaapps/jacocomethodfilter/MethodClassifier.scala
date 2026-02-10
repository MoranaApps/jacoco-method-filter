package io.moranaapps.jacocomethodfilter

import org.objectweb.asm.Opcodes

sealed trait MethodClassification
case object StronglyGenerated extends MethodClassification
case object PossiblyHuman extends MethodClassification

object MethodClassifier {
  
  /**
   * Glob patterns for common compiler-generated method names.
   * Used to identify synthetic/generated methods by name alone.
   */
  val GeneratedNamePatterns: Seq[String] = Seq(
    "$anonfun$*",
    "lambda$*",
    "$default$*",
    "copy$default$*",
    "access$*",
    "$init$",
    "$adapted$*"
  )
  
  private val generatedNameRegexes = GeneratedNamePatterns.map(Glob.toRegex)
  
  /**
   * Classify a method as either StronglyGenerated or PossiblyHuman
   * based on access flags and method name.
   *
   * StronglyGenerated if ANY of:
   * - Access flags include ACC_SYNTHETIC (0x1000)
   * - Access flags include ACC_BRIDGE (0x0040)
   * - Method name matches common compiler-generated patterns
   *
   * PossiblyHuman otherwise.
   *
   * @param methodName the method name
   * @param access the method access flags (ASM Opcodes constants)
   * @return StronglyGenerated or PossiblyHuman
   */
  def classify(methodName: String, access: Int): MethodClassification = {
    // Check ACC_SYNTHETIC flag
    if ((access & Opcodes.ACC_SYNTHETIC) != 0) {
      return StronglyGenerated
    }
    
    // Check ACC_BRIDGE flag
    if ((access & Opcodes.ACC_BRIDGE) != 0) {
      return StronglyGenerated
    }
    
    // Check if name matches generated patterns
    if (generatedNameRegexes.exists(_.matcher(methodName).matches())) {
      return StronglyGenerated
    }
    
    PossiblyHuman
  }
}

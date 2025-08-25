package io.moranaapps.jacocomethodfilter

import java.nio.file.{Files, Path}
import org.objectweb.asm.Opcodes

object TestSupport {
  def tmpFile(prefix: String = "rules-", suffix: String = ".txt"): Path =
    Files.createTempFile(prefix, suffix)

  def write(path: Path, lines: Seq[String]): Path = {
    Files.write(path, lines.mkString(System.lineSeparator()).getBytes("UTF-8"))
    path
  }

  // Build a JVM descriptor: (args...)ret
  def desc(args: String, ret: String): String = s"($args)$ret"

  // Compose a method access value using ASM Opcodes
  def access(public: Boolean = false,
             protectedA: Boolean = false,
             privateA: Boolean = false,
             synthetic: Boolean = false,
             bridge: Boolean = false,
             staticA: Boolean = false,
             abstractA: Boolean = false): Int = {
    var a = 0
    if (public)     a |= Opcodes.ACC_PUBLIC
    if (protectedA) a |= Opcodes.ACC_PROTECTED
    if (privateA)   a |= Opcodes.ACC_PRIVATE
    if (synthetic)  a |= Opcodes.ACC_SYNTHETIC
    if (bridge)     a |= Opcodes.ACC_BRIDGE
    if (staticA)    a |= Opcodes.ACC_STATIC
    if (abstractA)  a |= Opcodes.ACC_ABSTRACT
    a
  }
}

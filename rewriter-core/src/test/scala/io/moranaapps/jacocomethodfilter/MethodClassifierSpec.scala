package io.moranaapps.jacocomethodfilter

import org.scalatest.funsuite.AnyFunSuite
import org.objectweb.asm.Opcodes

class MethodClassifierSpec extends AnyFunSuite {

  // --- ACC_SYNTHETIC flag ---

  test("classify: ACC_SYNTHETIC alone → StronglyGenerated") {
    assert(MethodClassifier.classify("anyName", Opcodes.ACC_SYNTHETIC) == StronglyGenerated)
  }

  test("classify: ACC_SYNTHETIC with other flags → StronglyGenerated") {
    val access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC
    assert(MethodClassifier.classify("regularName", access) == StronglyGenerated)
  }

  // --- ACC_BRIDGE flag ---

  test("classify: ACC_BRIDGE alone → StronglyGenerated") {
    assert(MethodClassifier.classify("anyName", Opcodes.ACC_BRIDGE) == StronglyGenerated)
  }

  test("classify: ACC_BRIDGE with other flags → StronglyGenerated") {
    val access = Opcodes.ACC_PUBLIC | Opcodes.ACC_BRIDGE
    assert(MethodClassifier.classify("regularName", access) == StronglyGenerated)
  }

  test("classify: ACC_SYNTHETIC | ACC_BRIDGE → StronglyGenerated") {
    val access = Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE
    assert(MethodClassifier.classify("regularName", access) == StronglyGenerated)
  }

  // --- Name patterns: $anonfun$* ---

  test("classify: $anonfun$ prefix → StronglyGenerated") {
    assert(MethodClassifier.classify("$anonfun$myFunc$1", Opcodes.ACC_PUBLIC) == StronglyGenerated)
    assert(MethodClassifier.classify("$anonfun$do$2", Opcodes.ACC_PRIVATE) == StronglyGenerated)
  }

  // --- Name patterns: lambda$* ---

  test("classify: lambda$ prefix → StronglyGenerated") {
    assert(MethodClassifier.classify("lambda$main$0", Opcodes.ACC_PUBLIC) == StronglyGenerated)
    assert(MethodClassifier.classify("lambda$handler$1", 0) == StronglyGenerated)
  }

  // --- Name patterns: $default$* ---

  test("classify: $default$ prefix → StronglyGenerated") {
    assert(MethodClassifier.classify("$default$1", Opcodes.ACC_PUBLIC) == StronglyGenerated)
    assert(MethodClassifier.classify("$default$2", 0) == StronglyGenerated)
  }

  // --- Name patterns: copy$default$* ---

  test("classify: copy$default$ prefix → StronglyGenerated") {
    assert(MethodClassifier.classify("copy$default$1", Opcodes.ACC_PUBLIC) == StronglyGenerated)
    assert(MethodClassifier.classify("copy$default$3", 0) == StronglyGenerated)
  }

  // --- Name patterns: access$* ---

  test("classify: access$ prefix → StronglyGenerated") {
    assert(MethodClassifier.classify("access$000", Opcodes.ACC_PUBLIC) == StronglyGenerated)
    assert(MethodClassifier.classify("access$100", Opcodes.ACC_STATIC) == StronglyGenerated)
  }

  // --- Name patterns: $init$ ---

  test("classify: $init$ exact → StronglyGenerated") {
    assert(MethodClassifier.classify("$init$", Opcodes.ACC_PUBLIC) == StronglyGenerated)
  }

  // --- Name patterns: $adapted$* ---

  test("classify: $adapted$ prefix → StronglyGenerated") {
    assert(MethodClassifier.classify("$adapted$myMethod", Opcodes.ACC_PUBLIC) == StronglyGenerated)
    assert(MethodClassifier.classify("$adapted$1", 0) == StronglyGenerated)
  }

  // --- PossiblyHuman ---

  test("classify: regular method name with no flags → PossiblyHuman") {
    assert(MethodClassifier.classify("apply", Opcodes.ACC_PUBLIC) == PossiblyHuman)
    assert(MethodClassifier.classify("process", Opcodes.ACC_PUBLIC) == PossiblyHuman)
    assert(MethodClassifier.classify("getData", Opcodes.ACC_PRIVATE) == PossiblyHuman)
    assert(MethodClassifier.classify("<init>", Opcodes.ACC_PUBLIC) == PossiblyHuman)
    assert(MethodClassifier.classify("<clinit>", Opcodes.ACC_STATIC) == PossiblyHuman)
    assert(MethodClassifier.classify("copy", Opcodes.ACC_PUBLIC) == PossiblyHuman)
    assert(MethodClassifier.classify("equals", Opcodes.ACC_PUBLIC) == PossiblyHuman)
    assert(MethodClassifier.classify("hashCode", Opcodes.ACC_PUBLIC) == PossiblyHuman)
    assert(MethodClassifier.classify("toString", Opcodes.ACC_PUBLIC) == PossiblyHuman)
  }

  test("classify: zero access flags with normal name → PossiblyHuman") {
    assert(MethodClassifier.classify("myMethod", 0) == PossiblyHuman)
  }

  test("classify: public + static with normal name → PossiblyHuman") {
    val access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC
    assert(MethodClassifier.classify("main", access) == PossiblyHuman)
  }

  // --- GeneratedNamePatterns constant ---

  test("GeneratedNamePatterns contains expected patterns") {
    val patterns = MethodClassifier.GeneratedNamePatterns
    assert(patterns.contains("$anonfun$*"))
    assert(patterns.contains("lambda$*"))
    assert(patterns.contains("$default$*"))
    assert(patterns.contains("copy$default$*"))
    assert(patterns.contains("access$*"))
    assert(patterns.contains("$init$"))
    assert(patterns.contains("$adapted$*"))
    assert(patterns.size == 7)
  }
}

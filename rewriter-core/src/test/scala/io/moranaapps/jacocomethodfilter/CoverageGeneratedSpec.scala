package io.moranaapps.jacocomethodfilter

import org.scalatest.funsuite.AnyFunSuite

class CoverageGeneratedSpec extends AnyFunSuite {

  test("AnnotationDescriptor follows JVM descriptor format") {
    val desc = CoverageGenerated.AnnotationDescriptor
    assert(desc == "Lio/moranaapps/jacocomethodfilter/CoverageGenerated;")
    assert(desc.startsWith("L"))
    assert(desc.endsWith(";"))
  }

  test("AnnotationDescriptor simple name contains 'Generated'") {
    // JaCoCo ignores annotations whose simple name contains "Generated"
    val desc = CoverageGenerated.AnnotationDescriptor
    val simpleName = desc.stripPrefix("L").stripSuffix(";").split('/').last
    assert(simpleName.contains("Generated"))
  }
}

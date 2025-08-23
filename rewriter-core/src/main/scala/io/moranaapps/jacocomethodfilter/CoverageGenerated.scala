package io.moranaapps.jacocomethodfilter

import java.lang.annotation._

/**
 * Marker annotation intentionally containing "Generated" in its simple name.
 * JaCoCo (>= 0.8.2) ignores classes/methods annotated with an annotation whose
 * simple name contains "Generated" (retention CLASS or RUNTIME) during reporting.
 */
@Retention(RetentionPolicy.CLASS)
@Target(Array(ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR))
final class CoverageGenerated extends scala.annotation.StaticAnnotation

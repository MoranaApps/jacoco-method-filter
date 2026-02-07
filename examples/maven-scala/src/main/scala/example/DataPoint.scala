package example

/**
 * A 2-D data point.
 *
 * Being a case class, the Scala compiler generates boilerplate methods
 * (copy, productArity, productElement, equals, hashCode, toString, etc.)
 * that the coverage-filter rules will mark as @Generated.
 */
case class DataPoint(identifier: String, x: Double, y: Double) {

  /** Euclidean distance from the origin. */
  def distanceFromOrigin: Double = math.sqrt(x * x + y * y)

  /** Returns the 1-based quadrant number, or 0 when on an axis. */
  def quadrant: Int =
    if (x > 0 && y > 0) 1
    else if (x < 0 && y > 0) 2
    else if (x < 0 && y < 0) 3
    else if (x > 0 && y < 0) 4
    else 0
}

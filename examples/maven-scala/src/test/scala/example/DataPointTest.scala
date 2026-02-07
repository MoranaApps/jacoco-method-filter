package example

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DataPointTest extends AnyFlatSpec with Matchers {

  "distanceFromOrigin" should "return correct Euclidean distance" in {
    val pt = DataPoint("P1", 3.0, 4.0)
    pt.distanceFromOrigin shouldBe 5.0 +- 0.001
  }

  "quadrant" should "identify quadrant 1" in {
    DataPoint("Q1", 1.0, 1.0).quadrant shouldBe 1
  }

  it should "identify quadrant 2" in {
    DataPoint("Q2", -1.0, 1.0).quadrant shouldBe 2
  }

  it should "identify quadrant 3" in {
    DataPoint("Q3", -1.0, -1.0).quadrant shouldBe 3
  }

  it should "identify quadrant 4" in {
    DataPoint("Q4", 1.0, -1.0).quadrant shouldBe 4
  }

  it should "return 0 when on an axis" in {
    DataPoint("origin", 0.0, 5.0).quadrant shouldBe 0
  }

  "case class equality" should "hold for identical fields" in {
    val a = DataPoint("A", 5.0, 6.0)
    val b = DataPoint("A", 5.0, 6.0)
    a shouldBe b
    a.hashCode shouldBe b.hashCode
  }
}

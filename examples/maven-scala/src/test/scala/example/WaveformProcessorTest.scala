package example

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class WaveformProcessorTest extends AnyFlatSpec with Matchers {

  "applyAmplification" should "multiply every sample by the amplitude factor" in {
    val proc = WaveformProcessor(44100, 2.0)
    val result = proc.applyAmplification(Array(1.0, 2.0, 3.0))
    result shouldBe Array(2.0, 4.0, 6.0)
  }

  "countPeaksAboveThreshold" should "count local maxima above the threshold" in {
    val proc = WaveformProcessor(44100, 1.0)
    val wave = Array(1.0, 3.0, 2.0, 5.0, 1.0)
    proc.countPeaksAboveThreshold(wave, 2.5) shouldBe 2
  }

  it should "return 0 when no peaks exceed the threshold" in {
    val proc = WaveformProcessor(44100, 1.0)
    val wave = Array(1.0, 2.0, 1.0)
    proc.countPeaksAboveThreshold(wave, 5.0) shouldBe 0
  }
}

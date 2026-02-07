package example

/**
 * Simple waveform utilities.
 *
 * Case class boilerplate (copy, equals, hashCode, …) will be
 * filtered from coverage via `jmf-rules.txt`.
 */
case class WaveformProcessor(samplingRate: Int, amplitudeFactor: Double) {

  /** Scale every sample by the amplitude factor. */
  def applyAmplification(waveform: Array[Double]): Array[Double] =
    waveform.map(_ * amplitudeFactor)

  /** Count local maxima that exceed the given threshold. */
  def countPeaksAboveThreshold(waveform: Array[Double], threshold: Double): Int =
    waveform.sliding(3).count { window =>
      window(1) > window(0) && window(1) > window(2) && window(1) > threshold
    }
}

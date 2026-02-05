package example;

import org.junit.jupiter.api.Test;

class WaveformProcessorTest {

    @Test
    void amplificationMultipliesAllSamples() {
        WaveformProcessor processor = new WaveformProcessor(44100, 2.0);
        double[] input = {1.0, 2.0, 3.0};
        double[] output = processor.applyAmplification(input);
        
        if (Math.abs(output[0] - 2.0) > 0.01) throw new AssertionError("First sample should be 2.0");
        if (Math.abs(output[1] - 4.0) > 0.01) throw new AssertionError("Second sample should be 4.0");
        if (Math.abs(output[2] - 6.0) > 0.01) throw new AssertionError("Third sample should be 6.0");
    }

    @Test
    void peakDetectionCountsLocalMaxima() {
        WaveformProcessor processor = new WaveformProcessor(44100, 1.0);
        double[] wave = {1.0, 3.0, 2.0, 5.0, 1.0};
        int peaks = processor.countPeaksAboveThreshold(wave, 2.5);
        
        if (peaks != 2) throw new AssertionError("Expected 2 peaks above 2.5");
    }

    @Test
    void accessorsWorkCorrectly() {
        WaveformProcessor processor = new WaveformProcessor(48000, 1.5);
        
        if (processor.getSamplingRate() != 48000) throw new AssertionError();
        if (Math.abs(processor.getAmplitudeFactor() - 1.5) > 0.01) throw new AssertionError();
        
        processor.setSamplingRate(96000);
        processor.setAmplitudeFactor(3.0);
        
        if (processor.getSamplingRate() != 96000) throw new AssertionError();
        if (Math.abs(processor.getAmplitudeFactor() - 3.0) > 0.01) throw new AssertionError();
    }
}

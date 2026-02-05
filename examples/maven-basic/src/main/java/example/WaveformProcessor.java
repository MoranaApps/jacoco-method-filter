package example;

public class WaveformProcessor {

    private int samplingRate;
    private double amplitudeFactor;

    public WaveformProcessor(int samplingRate, double amplitudeFactor) {
        this.samplingRate = samplingRate;
        this.amplitudeFactor = amplitudeFactor;
    }

    public double[] applyAmplification(double[] inputWaveform) {
        double[] outputWaveform = new double[inputWaveform.length];
        for (int sampleIndex = 0; sampleIndex < inputWaveform.length; sampleIndex++) {
            outputWaveform[sampleIndex] = inputWaveform[sampleIndex] * amplitudeFactor;
        }
        return outputWaveform;
    }

    public int countPeaksAboveThreshold(double[] waveform, double threshold) {
        int peakCount = 0;
        for (int idx = 1; idx < waveform.length - 1; idx++) {
            boolean isLocalMaximum = waveform[idx] > waveform[idx - 1] && 
                                     waveform[idx] > waveform[idx + 1];
            if (isLocalMaximum && waveform[idx] > threshold) {
                peakCount++;
            }
        }
        return peakCount;
    }

    public int getSamplingRate() {
        return samplingRate;
    }

    public void setSamplingRate(int samplingRate) {
        this.samplingRate = samplingRate;
    }

    public double getAmplitudeFactor() {
        return amplitudeFactor;
    }

    public void setAmplitudeFactor(double amplitudeFactor) {
        this.amplitudeFactor = amplitudeFactor;
    }
}

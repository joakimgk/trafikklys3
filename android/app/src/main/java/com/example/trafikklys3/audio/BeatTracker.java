package com.example.trafikklys3.audio;

/**
 * Finds the tempo of music from raw audio.
 *
 * The signal is split into a bass band (kick drums) and a treble band (snares, hi-hats). Each
 * band gives a loudness curve, and the sudden rises in those curves are the beats. The tempo is
 * the interval at which the pattern of rises repeats, found by autocorrelation over the last
 * few seconds. Loudness is compared on a log scale, so loud (even clipping) music works.
 */
public class BeatTracker {

    public static class Result {
        public final double bpm;
        public final double confidence;  // 0..1: how clearly the beat repeats

        Result(double bpm, double confidence) {
            this.bpm = bpm;
            this.confidence = confidence;
        }
    }

    private static final int ENV_RATE = 200;               // loudness frames per second
    private static final int WINDOW = ENV_RATE * 8;        // look at the last 8 seconds
    private static final int MIN_FRAMES = ENV_RATE * 4;    // and wait until there are 4
    private static final int MIN_BPM = 60;
    private static final int MAX_BPM = 200;

    private static final double SILENCE = 0.002;           // below this level (of full scale) there is no music

    private final int hop;
    private final double aLow;
    private final double aHigh;

    private double lpLow, lpHigh;
    private double accLow, accHigh;
    private int accN;
    private double prevLow = Double.NaN, prevHigh = Double.NaN;
    private double level;

    private final float[] onset = new float[WINDOW];
    private int head = 0;
    private int count = 0;

    public BeatTracker(int sampleRate) {
        hop = sampleRate / ENV_RATE;
        aLow = 1 - Math.exp(-2 * Math.PI * 150 / sampleRate);    // bass: below ~150 Hz
        aHigh = 1 - Math.exp(-2 * Math.PI * 1500 / sampleRate);  // treble: above ~1.5 kHz
    }

    public void process(short[] pcm, int n) {
        for (int i = 0; i < n; i++) {
            double x = pcm[i] / 32768.0;
            lpLow += aLow * (x - lpLow);
            lpHigh += aHigh * (x - lpHigh);
            double treble = x - lpHigh;
            accLow += lpLow * lpLow;
            accHigh += treble * treble;
            if (++accN == hop) {
                frame();
                accN = 0;
                accLow = 0;
                accHigh = 0;
            }
        }
    }

    private void frame() {
        double rmsLow = Math.sqrt(accLow / hop);
        double rmsHigh = Math.sqrt(accHigh / hop);
        double eLow = Math.log(rmsLow + 1e-5);
        double eHigh = Math.log(rmsHigh + 1e-5);

        double o = 0;
        if (!Double.isNaN(prevLow)) {
            o = Math.max(0, eLow - prevLow) + Math.max(0, eHigh - prevHigh);
        }
        prevLow = eLow;
        prevHigh = eHigh;

        level += 0.01 * (Math.max(rmsLow, rmsHigh) - level);
        if (level < SILENCE) o = 0;  // don't mistake background noise for beats

        onset[head] = (float) o;
        head = (head + 1) % WINDOW;
        if (count < WINDOW) count++;
    }

    /** The tempo heard in the last few seconds, or null if there isn't enough music to tell. */
    public Result estimate() {
        if (count < MIN_FRAMES || level < SILENCE) return null;

        int n = count;
        double[] a = new double[n];
        int start = (head - n + WINDOW) % WINDOW;
        double mean = 0;
        for (int i = 0; i < n; i++) {
            a[i] = onset[(start + i) % WINDOW];
            mean += a[i];
        }
        mean /= n;
        double energy = 0;
        for (int i = 0; i < n; i++) {
            a[i] -= mean;
            energy += a[i] * a[i];
        }
        if (energy < 1e-9) return null;

        int minLag = ENV_RATE * 60 / MAX_BPM;
        int maxLag = ENV_RATE * 60 / MIN_BPM;
        double[] r = new double[maxLag + 2];
        for (int lag = minLag - 1; lag < r.length && lag < n / 2; lag++) {
            double sum = 0;
            for (int i = 0; i + lag < n; i++) sum += a[i] * a[i + lag];
            r[lag] = sum / (n - lag) / (energy / n);  // 1.0 would be a perfect repeat
        }

        int best = -1;
        double bestScore = -1;
        double rangeMean = 0;
        for (int lag = minLag; lag <= maxLag; lag++) {
            double bpm = 60.0 * ENV_RATE / lag;
            double octaves = Math.log(bpm / 115.0) / Math.log(2);
            // Tempos a factor of two apart look alike to the autocorrelation; people favour about 115 BPM
            double prior = Math.exp(-0.5 * octaves * octaves / (0.8 * 0.8));
            double score = r[lag] * prior;
            rangeMean += r[lag];
            if (score > bestScore) {
                bestScore = score;
                best = lag;
            }
        }
        rangeMean /= (maxLag - minLag + 1);
        if (best < 0) return null;

        // refine between frames: fit a parabola through the peak and its neighbours
        double lag = best;
        double y0 = r[best - 1], y1 = r[best], y2 = r[best + 1];
        double denom = y0 - 2 * y1 + y2;
        if (denom < 0) lag += 0.5 * (y0 - y2) / denom;

        double confidence = Math.max(0, Math.min(1, r[best] - Math.max(0, rangeMean)));
        return new Result(60.0 * ENV_RATE / lag, confidence);
    }
}

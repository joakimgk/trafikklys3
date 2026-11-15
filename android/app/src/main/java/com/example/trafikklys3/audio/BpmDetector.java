package com.example.trafikklys3.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Listens to the room through the microphone and reports the tempo of the music it hears.
 * One estimate a second; the tempo that is reported is the median of the last few, and
 * only when the beat is clear enough.
 */
public class BpmDetector {

    public interface Listener {
        /** Called on a background thread, at most every few seconds. */
        void onBpm(double bpm);
    }

    private static final String TAG = "BpmDetector";

    private static final int SAMPLE_RATE = 44100;
    private static final double MIN_CONFIDENCE = 0.25;
    private static final int KEEP = 5;             // estimates in the median
    private static final long ESTIMATE_MS = 1000;
    private static final long REPORT_MS = 2000;

    private final Listener listener;
    private volatile boolean running;
    private Thread thread;

    public BpmDetector(Listener listener) {
        this.listener = listener;
    }

    /** Starts listening. Needs the microphone permission; returns false if it can't. */
    public boolean start() {
        AudioRecord record = open();
        if (record == null) return false;

        running = true;
        thread = new Thread(() -> listen(record), "bpm-detector");
        thread.start();
        return true;
    }

    public void stop() {
        running = false;
        if (thread != null) {
            try {
                thread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
    }

    // Music wants the raw signal: no automatic gain, no noise suppression
    private AudioRecord open() {
        int[] sources = { MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.MIC };
        int min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) return null;

        for (int source : sources) {
            try {
                AudioRecord record = new AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 16384));
                if (record.getState() == AudioRecord.STATE_INITIALIZED) return record;
                record.release();
            } catch (SecurityException | IllegalArgumentException e) {
                Log.w(TAG, "source " + source + " not usable: " + e);
            }
        }
        return null;
    }

    private void listen(AudioRecord record) {
        BeatTracker tracker = new BeatTracker(SAMPLE_RATE);
        List<Double> recent = new ArrayList<>();
        short[] buffer = new short[4096];
        long lastEstimate = System.currentTimeMillis();
        long lastReport = 0;

        try {
            record.startRecording();
            while (running) {
                int n = record.read(buffer, 0, buffer.length);
                if (n <= 0) break;
                tracker.process(buffer, n);

                long now = System.currentTimeMillis();
                if (now - lastEstimate < ESTIMATE_MS) continue;
                lastEstimate = now;

                BeatTracker.Result result = tracker.estimate();
                if (result == null || result.confidence < MIN_CONFIDENCE) continue;

                recent.add(result.bpm);
                if (recent.size() > KEEP) recent.remove(0);
                if (recent.size() < 3 || now - lastReport < REPORT_MS) continue;

                List<Double> sorted = new ArrayList<>(recent);
                Collections.sort(sorted);
                lastReport = now;
                listener.onBpm(sorted.get(sorted.size() / 2));
            }
        } catch (IllegalStateException e) {
            Log.w(TAG, "recording stopped: " + e);
        } finally {
            try {
                record.stop();
            } catch (IllegalStateException e) {
                // already stopped
            }
            record.release();
        }
    }
}

package com.example.trafikklys3.controller;

import android.os.SystemClock;

/**
 * The show's master timeline: step k starts at epoch + k * period, where one step lasts
 * tempo * ESP_TICK_MS. The units lock to it through the sync beacon and the on-screen
 * simulator reads it too, so screen and lamps follow the same clock.
 */
public class ShowClock {

    // Firmware timer1: TIMER_DELAY = 7000 counts at 80 MHz / 16 = 5 MHz -> 1.4 ms per tick
    public static final double ESP_TICK_MS = 1.4;

    public static class Sample {
        public final int tempo;
        public final long step;
        public final int phase;  // 0..65535 of the way through the step

        Sample(int tempo, long step, int phase) {
            this.tempo = tempo;
            this.step = step;
            this.phase = phase;
        }
    }

    private long epochNs = SystemClock.elapsedRealtimeNanos();
    private int tempo = 60;

    private double periodNs() {
        return tempo * ESP_TICK_MS * 1_000_000.0;
    }

    /** Start the timeline over: step 0 begins now. */
    public synchronized void reset() {
        epochNs = SystemClock.elapsedRealtimeNanos();
    }

    /** Change speed without a jump in the current position. */
    public synchronized void setTempo(int newTempo) {
        long now = SystemClock.elapsedRealtimeNanos();
        double position = (now - epochNs) / periodNs();
        tempo = newTempo;
        epochNs = now - (long) (position * periodNs());
    }

    public synchronized int getTempo() {
        return tempo;
    }

    public synchronized Sample sample() {
        double position = (SystemClock.elapsedRealtimeNanos() - epochNs) / periodNs();
        long step = (long) Math.floor(position);
        int phase = (int) ((position - step) * 65536.0) & 0xFFFF;
        return new Sample(tempo, step, phase);
    }

    public synchronized long stepAt(long nowNs) {
        return (long) Math.floor((nowNs - epochNs) / periodNs());
    }

    public synchronized long startOfStepNs(long step) {
        return epochNs + (long) (step * periodNs());
    }
}

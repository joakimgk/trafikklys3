package com.example.trafikklys3.controller;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.example.trafikklys3.model.Client;
import com.example.trafikklys3.ui.TrafficLight;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plays the programs sent to the units on the on-screen lights, mirroring what the firmware does:
 * a program is staged first and only starts on swap, and every unit shows program[step % length]
 * of the show clock. It shows what the app intends; the real lamps trail it by the network delay.
 */
public class ProgramSimulator {

    // The lamps change this long after the app's clock (WiFi + AP + firmware loop); tune by eye
    private static final long LAMP_DELAY_NS = 25_000_000L;

    private static final byte ALL_ON = 0b111;

    private static class Unit {
        byte[] pending;
        byte[] active;
    }

    private final ShowClock clock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Client, Unit> units = new HashMap<>();

    private boolean running = false;

    private final Runnable tick = this::onTick;

    private void onTick() {
        render(clock.stepAt(SystemClock.elapsedRealtimeNanos() - LAMP_DELAY_NS));
        scheduleNext();
    }

    public ProgramSimulator(ShowClock clock) {
        this.clock = clock;
    }

    private Unit unit(Client c) {
        Unit u = units.get(c);
        if (u == null) {
            u = new Unit();
            units.put(c, u);
        }
        return u;
    }

    private void show(Client c, Unit u, long step) {
        if (u.active == null || u.active.length == 0 || c.mTrafficLight == null) return;
        c.mTrafficLight.setLamps(u.active[(int) Math.floorMod(step, (long) u.active.length)]);
    }

    private void render(long step) {
        for (Map.Entry<Client, Unit> e : units.entrySet()) {
            show(e.getKey(), e.getValue(), Math.max(0, step));
        }
    }

    private void scheduleNext() {
        long now = SystemClock.elapsedRealtimeNanos();
        long next = clock.stepAt(now - LAMP_DELAY_NS) + 1;
        long wakeNs = clock.startOfStepNs(next) + LAMP_DELAY_NS;
        handler.postDelayed(tick, Math.max(1, (wakeNs - now) / 1_000_000L + 1));
    }

    /** Restart the playback loop from the current clock (after reset or tempo change). */
    public void reschedule() {
        handler.post(() -> {
            if (!running) return;
            handler.removeCallbacks(tick);
            tick.run();
        });
    }

    private void ensureRunning() {
        if (running) return;
        running = true;
        tick.run();
    }

    public void stage(Client client, byte[] program) {
        byte[] copy = program.clone();
        handler.post(() -> unit(client).pending = copy);
    }

    public void stageAll(Collection<Client> clients, byte[] program) {
        List<Client> list = new ArrayList<>(clients);
        for (Client c : list) stage(c, program);
    }

    public void swap(Client client) {
        handler.post(() -> {
            Unit u = unit(client);
            swapUnit(u);
            show(client, u, Math.max(0, clock.stepAt(SystemClock.elapsedRealtimeNanos())));
            ensureRunning();
        });
    }

    /** All units start their staged program at step 0 of a fresh timeline. */
    public void swapAll() {
        handler.post(() -> {
            clock.reset();
            for (Unit u : units.values()) swapUnit(u);
            render(0);
            if (running) {
                handler.removeCallbacks(tick);
                scheduleNext();
            } else {
                running = true;
                scheduleNext();
            }
        });
    }

    private void swapUnit(Unit u) {
        if (u.pending == null) return;
        u.active = u.pending;
    }

    /** Back to a static light for one unit. */
    public void clear(Client client) {
        TrafficLight light = client.mTrafficLight;  // the light it is on right now
        handler.post(() -> {
            units.remove(client);
            if (light != null) light.setLamps(ALL_ON);
        });
    }

    public void stopAll() {
        handler.post(() -> {
            handler.removeCallbacks(tick);
            running = false;
            for (Map.Entry<Client, Unit> e : units.entrySet()) {
                if (e.getKey().mTrafficLight != null) e.getKey().mTrafficLight.setLamps(ALL_ON);
            }
            units.clear();
        });
    }

    public void start() {
        handler.post(() -> {
            for (Unit u : units.values()) {
                if (u.active != null) {
                    ensureRunning();
                    return;
                }
            }
        });
    }

    public void stop() {
        handler.post(() -> {
            handler.removeCallbacks(tick);
            running = false;
        });
    }
}

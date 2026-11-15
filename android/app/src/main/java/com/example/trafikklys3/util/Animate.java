package com.example.trafikklys3.util;

import static com.example.trafikklys3.model.Programs.GRN;
import static com.example.trafikklys3.model.Programs.NON;
import static com.example.trafikklys3.model.Programs.RED;
import static com.example.trafikklys3.model.Programs.YLW;

import android.util.Pair;

import com.example.trafikklys3.controller.ShowClock;
import com.example.trafikklys3.model.Client;
import com.example.trafikklys3.model.Program;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class Animate {
    private static byte[] reverse(byte[] p) {
        int len = p.length;
        byte[] rev = new byte[len];
        for (int i = 0; i < len; i++) {
            rev[len - i - 1] = p[i];
        }
        return rev;
    }

    private static void mergePrograms(ArrayList<Program> into, ArrayList<Program> other) {
        for (Program p : other) {
            for (Program u : into) {
                if (u.mClient.clientID.equals(p.mClient.clientID)) {
                    u.mProgram = concat(u.mProgram, p.mProgram);
                }
            }
        }
    }

    private static ArrayList<Program> reverse(ArrayList<Program> program) {
        ArrayList<Program> reversedProgram = new ArrayList<>(program.size());
        for (Program p : program) {
            reversedProgram.add(new Program(reverse(p.mProgram), p.mClient));
        }
        //Collections.reverse(program);
        return reversedProgram;
    }

    private static byte[] TRAIL = {
            RED, YLW, GRN
    };

    private static byte[] replace(byte[] dd, byte[] val, int startPos) {
        byte[] newArray = new byte[dd.length];
        for (int i = 0; i < dd.length; i++) {
            newArray[i] = i >= startPos && i - startPos < val.length ? val[i - startPos] : dd[i];
        }
        return newArray;
    }

    private static boolean isReversed(Client c) {
        return c.mTrafficLight.getCellOrientation() == 180 || c.mTrafficLight.getCellOrientation() == 270;
    }

    private static boolean isHorizontal(Client c) {
        return (c.mTrafficLight.getCellOrientation() % 180) == 90;
    }

    private static boolean isVertical(Client c) {
        return (c.mTrafficLight.getCellOrientation() % 180) == 0;
    }

    private static int countVerticalMiddles(ArrayList<Client> clients) {
        int count = 0;
        for (int i = 0; i < clients.size(); i++) {
            if (i == 0 || i == clients.size() -1) continue;
            Client c = clients.get(i);
            if (isVertical(c)) {
                count++;
            }
        }
        return count;
    }

    public static byte[] concat(byte[] a, byte[] b) {
        int len = a.length + b.length;
        byte[] newArray = new byte[len];
        for (int i = 0; i < len; i++) {
            newArray[i] = i < a.length ? a[i] : b[i - a.length];
        }
        return newArray;
    }

    private static byte[] repeat(byte b, int num) {
        byte[] newBytes = new byte[num];
        for (int i = 0; i < num; i++) {
            newBytes[i] = b;
        }
        return newBytes;
    }

    private static byte[] timeShift(byte[] b) {
        int len = b.length;
        int n = (int)Math.ceil((double)len/ 2.0);
        int newLen = n * (n + 1) - (n % 2);
        byte[] newArray = new byte[newLen];
        int pos = 0;
        for (int i = 0; i < len; i++) {
            newArray = replace(newArray, repeat(b[i], Math.abs(n - i)), pos);
            pos += Math.abs(n - i);
        }
        return newArray;
    }

    public static ArrayList<Program> Pendulum(ArrayList<Client> clients) {
        ArrayList<Program> program = Wave(clients);
        for (Program p : program) {
            p.mProgram = timeShift(p.mProgram);
        }
        return program;
    }

    public static ArrayList<Program> Sink(ArrayList<Pair<Client, Client>> horizontalPairs) {
        ArrayList<Program> program = new ArrayList<>();

        // determine "mirrored pairs" (RYG-GYR or GYR-RYG)
        for (Pair<Client, Client> p : horizontalPairs) {
            byte[] b = p.first.mTrafficLight.getCellOrientation() == 90 ? reverse(TRAIL) : TRAIL;
            program.add(new Program(b, p.first));
            program.add(new Program(b, p.second));
        }

        return program;
    }

    public static ArrayList<Program> Wave(ArrayList<Client> clients) {
        ArrayList<Program> program = Trail(clients);
        ArrayList<Program> program2 = reverse(Trail(clients));

        mergePrograms(program, program2);
        return program;
    }

    public static ArrayList<Program> Trail(ArrayList<Client> clients) {
        Collections.sort(clients, new Client.ClientsComparator());
        int verticalMiddles = countVerticalMiddles(clients);

        int len = clients.size() + verticalMiddles;
        ArrayList<Program> p = new ArrayList<>(len);

        // fill all with empty programs, of correct length
        byte[] emptyBytes = new byte[len * 3];
        Arrays.fill(emptyBytes, NON);

        int pos = 0;
        for (int i = 0; i < clients.size(); i++) {
            Client client = clients.get(i);
            byte[] prog = isReversed(client) ? reverse(TRAIL) : TRAIL;

            if (i > 0 && i < clients.size() && isVertical(client)) {
                // add extra step
                Program tmp = new Program(replace(
                        emptyBytes,
                        concat(reverse(prog), prog),
                        pos * 3
                ), client);
                pos += 2;
                p.add(tmp);
            } else {
                Program tmp = new Program(replace(
                        emptyBytes,
                        prog,
                        pos * 3
                ), client);
                pos++;
                p.add(tmp);
            }
        }
        return p;
    }


    // ---- Traffic lights ----
    // Every step lasts one tempo, so "10 x tempo" is ten steps in a row.

    private static final int LONG_STEPS = 10;
    private static final int SHORT_STEPS = 2;
    private static final int BLINK_STEPS = 5;
    private static final int CLEAR_STEPS = 2;  // all red between two directions

    // One road of an intersection: green, yellow, then red for the whole time the other road
    // has red+yellow, green, yellow (with an all-red gap on each side), and red+yellow to go.
    private static final byte[] INTERSECTION_CYCLE = concat(
            concat(repeat(GRN, LONG_STEPS), repeat(YLW, SHORT_STEPS)),
            concat(repeat(RED, CLEAR_STEPS + SHORT_STEPS + LONG_STEPS + SHORT_STEPS + CLEAR_STEPS),
                    repeat((byte) (RED | YLW), SHORT_STEPS)));

    // red, red+yellow, green, yellow, and back to red
    private static final byte[] TRAFFIC_CYCLE = concat(
            concat(repeat(RED, LONG_STEPS), repeat((byte) (RED | YLW), SHORT_STEPS)),
            concat(repeat(GRN, LONG_STEPS), repeat(YLW, SHORT_STEPS)));

    private static byte[] rotate(byte[] p, int shift) {
        byte[] out = new byte[p.length];
        for (int i = 0; i < p.length; i++) {
            out[i] = p[(i + shift) % p.length];
        }
        return out;
    }

    /** Every light runs the normal red - red+yellow - green - yellow cycle, all in step. */
    public static ArrayList<Program> Traffic(ArrayList<Client> clients) {
        ArrayList<Program> program = new ArrayList<>(clients.size());
        for (Client c : clients) {
            program.add(new Program(TRAFFIC_CYCLE.clone(), c));
        }
        return program;
    }

    /** Every other light (left to right) is half a cycle behind: one road has green while the
     *  other has red. They switch through yellow, with a moment of red on both in between. */
    public static ArrayList<Program> Intersection(ArrayList<Client> clients) {
        Collections.sort(clients, new Client.ClientsComparator());
        ArrayList<Program> program = new ArrayList<>(clients.size());
        for (int i = 0; i < clients.size(); i++) {
            int shift = (i % 2 == 0) ? 0 : INTERSECTION_CYCLE.length / 2;
            program.add(new Program(rotate(INTERSECTION_CYCLE, shift), clients.get(i)));
        }
        return program;
    }

    // While "on" lasts up to this long, a blink is on and off for equally long. Slower than
    // that, the dark time shrinks: a long dark gap looks bad on a light that is on for seconds.
    private static final double BLINK_EVEN_LIMIT_MS = 2000;
    private static final double BLINK_SHRINK_EXPONENT = 0.5;

    /** How a blink is laid out: how many steps on and off, and the step length the units need. */
    public static class BlinkPlan {
        public final int onSteps;
        public final int offSteps;
        public final int tempoTicks;

        BlinkPlan(int onSteps, int offSteps, int tempoTicks) {
            this.onSteps = onSteps;
            this.offSteps = offSteps;
            this.tempoTicks = tempoTicks;
        }
    }

    /**
     * "On" lasts 5 steps of the user's tempo. Off lasts as long, until that is longer than
     * BLINK_EVEN_LIMIT_MS; after that it gets shorter the slower the tempo is. A step can't be
     * split, so a short dark time is made with a finer step: more steps, each shorter.
     */
    public static BlinkPlan planBlink(int tempoTicks) {
        double onMs = BLINK_STEPS * tempoTicks * ShowClock.ESP_TICK_MS;
        double offMs = onMs <= BLINK_EVEN_LIMIT_MS
                ? onMs
                : BLINK_EVEN_LIMIT_MS * Math.pow(BLINK_EVEN_LIMIT_MS / onMs, BLINK_SHRINK_EXPONENT);

        int ratio = (int) Math.max(1, Math.round(onMs / offMs));  // on is this many times longer
        int onSteps = BLINK_STEPS * ratio;
        int ticks = (int) Math.max(1, Math.round((double) tempoTicks / ratio));
        return new BlinkPlan(onSteps, BLINK_STEPS, ticks);
    }

    /** All lights blink one colour, slowly. */
    public static ArrayList<Program> Blink(ArrayList<Client> clients, byte color, BlinkPlan plan) {
        byte[] pattern = concat(repeat(color, plan.onSteps), repeat(NON, plan.offSteps));
        ArrayList<Program> program = new ArrayList<>(clients.size());
        for (Client c : clients) {
            program.add(new Program(pattern.clone(), c));
        }
        return program;
    }

}

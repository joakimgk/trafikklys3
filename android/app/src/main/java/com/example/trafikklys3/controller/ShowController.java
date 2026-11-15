package com.example.trafikklys3.controller;

import static com.example.trafikklys3.network.EspProtocol.CMD_PROGRAM;
import static com.example.trafikklys3.network.EspProtocol.CMD_SWAP;
import static com.example.trafikklys3.network.EspProtocol.CMD_TEMPO;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.trafikklys3.model.Client;
import com.example.trafikklys3.model.ClientListener;
import com.example.trafikklys3.model.ClientRegistry;
import com.example.trafikklys3.model.LightStatus;
import com.example.trafikklys3.model.Program;
import com.example.trafikklys3.model.Programs;
import com.example.trafikklys3.network.EspProtocol;
import com.example.trafikklys3.network.NetworkSender;
import com.example.trafikklys3.ui.TrafficLight;
import com.example.trafikklys3.ui.TrafficLightContainer;
import com.example.trafikklys3.util.Animate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ShowController implements ClientListener {

    private static final String TAG = "ShowController";

    private final NetworkSender network;

    private final TrafficLightContainer container;

    private ClientRegistry registry;

    private ArrayList<Program> currentProgram;

    private final Runnable presenceCheck = this::checkPresence;
    private Runnable presenceListener;
    private Consumer<Client> unitListener;

    private final ShowClock clock;
    private final ProgramSimulator simulator;

    private int tempo = 60;

    // Shows built for the placed units; the user picks one
    private static final String[] SHOW_NAMES = {
            "Trail", "Wave", "Pendulum", "Traffic", "Intersection",
            "Blink red", "Blink yellow", "Blink green"};
    private static final int MAX_PROGRAM_LENGTH = 255;  // what fits in one packet
    private int showIndex = 0;

    private boolean running = false;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    public ShowController(NetworkSender network,
                          TrafficLightContainer trafficLightContainer,
                          ClientRegistry registry,
                          ShowClock clock) {
        this.clock = clock;
        this.simulator = new ProgramSimulator(clock);
        this.network = network;
        this.container = trafficLightContainer;
        this.registry = registry;
    }

    public void start() {
        Log.d("ShowController", "start()");
        simulator.start();
        uiHandler.removeCallbacks(presenceCheck);
        uiHandler.post(presenceCheck);
    }

    public void stop() {
        Log.d("ShowController", "stop()");
        simulator.stop();
        uiHandler.removeCallbacks(presenceCheck);
    }

    /** Called (on the UI thread) when a unit announces itself as new, or has just been powered on. */
    public void setUnitListener(Consumer<Client> listener) {
        unitListener = listener;
    }

    private void notifyUnit(Client client) {
        uiHandler.post(() -> {
            if (unitListener != null) unitListener.accept(client);
        });
    }

    /** Called after every status refresh (once a second), on the UI thread. */
    public void setPresenceListener(Runnable listener) {
        presenceListener = listener;
    }

    public int getTempo() {
        return tempo;
    }

    public void setTempo(int tempo) {
        Log.d("ShowController", "setTempo: " + tempo);
        this.tempo = tempo;
        clock.setTempo(tempo);
        simulator.reschedule();
        byte[] payload = new byte[] { (byte) (tempo >> 8), (byte) tempo };
        network.sendToAll(EspProtocol.buildCommand(CMD_TEMPO, payload));
        network.syncBurst();
    }

    public void transmitProgram(ArrayList<Program> program) {
        currentProgram = program;
        for (Program p : program) {
            network.sendUnicast(p.mClient, EspProtocol.buildCommand(CMD_PROGRAM, p.mProgram));
            simulator.stage(p.mClient, p.mProgram);
        }
    }

    // Units found by the network. Lights are placed by the user, so nothing to draw here.
    @Override
    public void onClientAdded(Client client) {
        Log.v(TAG, "onClientAdded clientID = " + client.clientID);
        notifyUnit(client);
    }

    @Override
    public void onClientUpdated(Client client) {
        Log.v(TAG, "onClientUpdated clientID = " + client.clientID);
        notifyUnit(client);  // a unit that was just powered on needs a light too
        if (currentProgram == null) return;
        for (Program p : currentProgram) {
            if (p.mClient.clientID.equals(client.clientID)) {
                network.sendUnicast(p.mClient, EspProtocol.buildCommand(CMD_PROGRAM, p.mProgram));
                network.sendUnicast(p.mClient, EspProtocol.buildCommand(CMD_SWAP));
                simulator.stage(p.mClient, p.mProgram);
                simulator.swap(p.mClient);
            }
        }
    }

    public void startShow() {
        registry.updateGroups();

        // Every light on screen takes part. One without a unit gets a stand-in that only
        // exists on screen, so a layout can be tried out before any unit is assigned.
        ArrayList<Client> clients = new ArrayList<>();
        for (TrafficLight light : container.getLights()) {
            Client c = light.getClient();
            if (c == null) c = standIn(light);
            clients.add(c);
        }
        if (clients.isEmpty()) return;

        simulator.stopAll();  // forget the previous show, including old stand-ins
        for (TrafficLight light : container.getLights()) light.setManualBits(-1);

        ArrayList<Program> program = buildShow(clients);
        if (tooLong(program)) {
            Log.w(TAG, SHOW_NAMES[showIndex] + " is too long for this many units, using Trail");
            showIndex = 0;
            program = buildShow(clients);
        }

        // make sure the units run at the tempo the app shows
        setTempo(tempo);
        transmitProgram(program);

        network.sendToAll(EspProtocol.buildCommand(CMD_SWAP));
        simulator.swapAll();
        network.syncBurst();
        running = true;
    }

    /** Is a show running (on screen and on the units)? */
    public boolean isRunning() {
        return running;
    }

    private final Map<Integer, Client> standIns = new HashMap<>();

    private Client standIn(TrafficLight light) {
        Client c = standIns.get(light.getLightId());
        if (c == null) {
            c = new Client(-light.getLightId(), "light-" + light.getLightId(), null);
            standIns.put(light.getLightId(), c);
        }
        c.mTrafficLight = light;
        return c;
    }

    /** Light one lamp on each of these lights by hand. Pressing the same colour again turns it off. */
    public void setManualColor(List<TrafficLight> lights, byte color) {
        boolean allSet = !lights.isEmpty();
        for (TrafficLight light : lights) {
            if (light.getManualBits() != color) allSet = false;
        }
        byte bits = allSet ? Programs.NON : color;

        for (TrafficLight light : lights) {
            light.setManualBits(bits);
            byte[] program = new byte[] { bits };

            Client c = light.getClient();
            if (c == null) {
                c = standIn(light);  // no unit: the light only changes on screen
            } else {
                network.sendUnicast(c, EspProtocol.buildCommand(CMD_PROGRAM, program));
                network.sendUnicast(c, EspProtocol.buildCommand(CMD_SWAP));
                if (currentProgram != null) {
                    for (Program p : currentProgram) {
                        if (p.mClient == c) p.mProgram = program;  // a reconnecting unit gets this, not the show
                    }
                }
            }
            preview(c, program);
        }
    }

    public String getShowName() {
        return SHOW_NAMES[showIndex];
    }

    public String[] getShowNames() {
        return SHOW_NAMES.clone();
    }

    public int getShowIndex() {
        return showIndex;
    }

    /** Switch to a show and start it right away. */
    public void selectShow(int index) {
        showIndex = Math.floorMod(index, SHOW_NAMES.length);
        startShow();
    }

    public void nextShow() {
        selectShow(showIndex + 1);
    }

    // Each builder sorts the list it is given, so hand it a copy
    private ArrayList<Program> buildShow(ArrayList<Client> clients) {
        ArrayList<Client> copy = new ArrayList<>(clients);
        switch (showIndex) {
            case 1:
                return Animate.Wave(copy);
            case 2:
                return Animate.Pendulum(copy);
            case 3:
                return Animate.Traffic(copy);
            case 4:
                return Animate.Intersection(copy);
            case 5:
                return Animate.Blink(copy, Programs.RED);
            case 6:
                return Animate.Blink(copy, Programs.YLW);
            case 7:
                return Animate.Blink(copy, Programs.GRN);
            default:
                return Animate.Trail(copy);
        }
    }

    private boolean tooLong(ArrayList<Program> program) {
        for (Program p : program) {
            if (p.mProgram.length > MAX_PROGRAM_LENGTH) return true;
        }
        return false;
    }

    /** Stop the show on screen and on every unit: the lamps go dark. */
    public void stopProgram() {
        stopShow();
        for (TrafficLight light : container.getLights()) light.setManualBits(-1);
        network.sendToAll(EspProtocol.buildCommand(CMD_PROGRAM, new byte[] { Programs.NON }));
        network.sendToAll(EspProtocol.buildCommand(CMD_SWAP));
    }

    /** Back to the idle state used during setup: nothing animated, nothing resent to the units. */
    public void stopShow() {
        running = false;
        currentProgram = null;
        simulator.stopAll();
    }

    /** Show a unit's identify blink on its on-screen light too. */
    public void preview(Client client, byte[] program) {
        simulator.stage(client, program);
        simulator.swap(client);
    }

    public void stopPreview(Client client) {
        simulator.clear(client);
    }

    /** Is any unit reporting in right now? */
    public boolean hasOnlineUnit() {
        for (Client c : registry.getClients()) {
            if (c.isOnline()) return true;
        }
        return false;
    }

    private void checkPresence() {
        // A unit that is online but has no light yet is "available" to every unassigned light
        boolean freeUnit = false;
        for (Client c : registry.getClients()) {
            if (c.mTrafficLight == null && c.isOnline()) freeUnit = true;
        }

        for (TrafficLight light : container.getLights()) {
            Client c = light.getClient();
            if (c == null) {
                light.setStatus(freeUnit ? LightStatus.ONLINE : LightStatus.UNASSIGNED);
            } else {
                light.setStatus(c.isOnline() ? LightStatus.ONLINE : LightStatus.OFFLINE);
            }
        }

        if (presenceListener != null) presenceListener.run();
        uiHandler.postDelayed(presenceCheck, 1000);
    }
}

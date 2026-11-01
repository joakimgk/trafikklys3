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
import com.example.trafikklys3.model.PairingStore;
import com.example.trafikklys3.model.Program;
import com.example.trafikklys3.network.EspProtocol;
import com.example.trafikklys3.network.NetworkSender;
import com.example.trafikklys3.ui.TrafficLight;
import com.example.trafikklys3.ui.TrafficLightContainer;
import com.example.trafikklys3.util.Animate;

import java.util.ArrayList;

public class ShowController implements ClientListener {

    private static final String TAG = "ShowController";

    private final NetworkSender network;

    private final TrafficLightContainer container;

    private ClientRegistry registry;
    private final PairingStore pairingStore;

    private ArrayList<Program> currentProgram;

    private static final long AUTO_START_DELAY_MS = 3000;

    private final Runnable autoStart = this::autoStartShow;

    private final Runnable presenceCheck = this::checkPresence;

    private final ShowClock clock;
    private final ProgramSimulator simulator;

    private int tempo = 60;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    public ShowController(NetworkSender network,
                          TrafficLightContainer trafficLightContainer,
                          ClientRegistry registry,
                          PairingStore pairingStore,
                          ShowClock clock) {
        this.clock = clock;
        this.simulator = new ProgramSimulator(clock);
        this.pairingStore = pairingStore;
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

    public int getTempo() {
        return tempo;
    }

    public void setTempo(int tempo) {
        Log.d("ShowController", "setTempo: " + tempo);
        this.tempo = tempo;
        clock.setTempo(tempo);
        simulator.reschedule();
        byte[] payload = new byte[] { (byte) (tempo & 0xFF) };
        network.sendToAll(EspProtocol.buildCommand(CMD_TEMPO, payload));
        network.syncBurst();
        //network.sendBroadcast(EspProtocol.buildCommand(CMD_TEMPO, payload));
    }

    public void transmitProgram(byte[] program) {
        network.sendToAll(EspProtocol.buildCommand(CMD_PROGRAM, program));
        simulator.stageAll(registry.getClients(), program);
    }

    public void transmitProgram(ArrayList<Program> program) {
        currentProgram = program;
        for (Program p : program) {
            network.sendUnicast(p.mClient, EspProtocol.buildCommand(CMD_PROGRAM, p.mProgram));
            simulator.stage(p.mClient, p.mProgram);
        }
    }

    public void resetProgram() {
        network.sendToAll(EspProtocol.buildCommand(CMD_SWAP));
        simulator.swapAll();
        network.syncBurst();
    }

    /** Back to the idle state used during setup: nothing animated, nothing resent to the units. */
    public void stopShow() {
        uiHandler.removeCallbacks(autoStart);
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

    private void checkPresence() {
        for (Client c : registry.getClients()) {
            if (c.mTrafficLight != null) {
                c.mTrafficLight.setOnline(c.isOnline());
            }
        }
        uiHandler.postDelayed(presenceCheck, 1000);
    }

    // After an app restart: only start once every known unit is placed on screen
    private void autoStartShow() {
        for (Client c : registry.getClients()) {
            if (c.mTrafficLight == null) return;
        }
        startShow();
    }

    @Override
    public void onClientAdded(Client client) {
        uiHandler.post(() -> {
            TrafficLight light = container.addCell();
            // Known unit: put it back where it was, already paired
            if (pairingStore.restore(client, light)) {
                // The app doesn't know what the units are running after a restart:
                // (re)start the show once the known units have had time to report in
                uiHandler.removeCallbacks(autoStart);
                uiHandler.postDelayed(autoStart, AUTO_START_DELAY_MS);
            }
            light.setOnline(client.isOnline());
            container.invalidate();
        });
    }

    @Override
    public void onClientUpdated(Client client) {
        Log.v(TAG, "onClientUpdated clientID = " + client.clientID);
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
        ArrayList<Client> clients = new ArrayList<>();
        for (Client c : registry.getClients()) {
            if (c.mTrafficLight != null) clients.add(c);  // only units placed on screen
        }
        if (clients.isEmpty()) return;
        //transmitProgram(Animate.Trail());
        //transmitProgram(Animate.Wave());
        //transmitProgram(Animate.Sink());
        //transmitProgram(Animate.Pendulum());

        //ArrayList<Program> p = Animate.Trail();
        // make sure the units run at the tempo the app shows
        setTempo(tempo);
        transmitProgram(Animate.Trail(clients));

        network.sendToAll(EspProtocol.buildCommand(CMD_SWAP));
        simulator.swapAll();
        network.syncBurst();
    }
}
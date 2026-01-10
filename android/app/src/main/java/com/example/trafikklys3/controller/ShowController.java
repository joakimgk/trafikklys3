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
import com.example.trafikklys3.model.Program;
import com.example.trafikklys3.network.EspProtocol;
import com.example.trafikklys3.network.NetworkSender;
import com.example.trafikklys3.ui.TrafficLightContainer;
import com.example.trafikklys3.util.Animate;

import java.util.ArrayList;

public class ShowController implements ClientListener {

    private final NetworkSender network;

    private final TrafficLightContainer container;

    private ClientRegistry registry;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    public ShowController(NetworkSender network,
                          TrafficLightContainer trafficLightContainer,
                          ClientRegistry registry) {
        this.network = network;
        this.container = trafficLightContainer;
        this.registry = registry;
    }

    public void start() {
        Log.d("ShowController", "start()");
    }

    public void stop() {
        Log.d("ShowController", "stop()");
    }

    public void setTempo(int tempo) {
        Log.d("ShowController", "setTempo: " + tempo);
        byte[] payload = new byte[] { (byte) (tempo & 0xFF) };
        network.sendToAll(EspProtocol.buildCommand(CMD_TEMPO, payload));
        //network.sendBroadcast(EspProtocol.buildCommand(CMD_TEMPO, payload));
    }

    public void transmitProgram(byte[] program) {
        network.sendToAll(EspProtocol.buildCommand(CMD_PROGRAM, program));
    }

    public void transmitProgram(ArrayList<Program> program) {
        for (Program p : program) {
            network.sendUnicast(p.mClient, EspProtocol.buildCommand(CMD_PROGRAM, p.mProgram));
        }
    }

    public void resetProgram() {
        network.sendToAll(EspProtocol.buildCommand(CMD_SWAP));
    }

    @Override
    public void onClientAdded(Client client) {
        uiHandler.post(() -> {
            container.addCell();
            container.invalidate();
        });
    }

    @Override
    public void onClientUpdated(Client client) {

    }

    public void startShow() {
        registry.updateGroups();
        ArrayList<Client> clients = new ArrayList<>(registry.getClients());
        //transmitProgram(Animate.Trail());
        //transmitProgram(Animate.Wave());
        //transmitProgram(Animate.Sink());
        //transmitProgram(Animate.Pendulum());

        //ArrayList<Program> p = Animate.Trail();
        transmitProgram(Animate.Trail(clients));

        network.sendToAll(EspProtocol.buildCommand(CMD_SWAP));
    }
}
package com.example.trafikklys3.controller;

import static com.example.trafikklys3.network.EspProtocol.CMD_PROGRAM;
import static com.example.trafikklys3.network.EspProtocol.CMD_RESET;
import static com.example.trafikklys3.network.EspProtocol.CMD_TEMPO;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.trafikklys3.model.Client;
import com.example.trafikklys3.model.ClientListener;
import com.example.trafikklys3.network.EspProtocol;
import com.example.trafikklys3.network.NetworkSender;
import com.example.trafikklys3.network.ServerService;
import com.example.trafikklys3.ui.TrafficLightContainer;

public class ShowController implements ClientListener {

    private final NetworkSender network;

    private final TrafficLightContainer container;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    public ShowController(NetworkSender network, TrafficLightContainer trafficLightContainer) {
        this.network = network;
        this.container = trafficLightContainer;
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

    public void changeProgram(byte[] program) {
        network.sendToAll(EspProtocol.buildCommand(CMD_PROGRAM, program));
    }

    public void resetProgram() {
        network.sendToAll(EspProtocol.buildCommand(CMD_RESET));
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
}
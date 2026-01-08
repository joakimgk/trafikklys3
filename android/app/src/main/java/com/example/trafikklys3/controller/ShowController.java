package com.example.trafikklys3.controller;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.trafikklys3.model.Client;
import com.example.trafikklys3.model.ClientRegistry;
import com.example.trafikklys3.ui.TrafficLightContainer;

public class ShowController implements ClientRegistry.Listener {
    private final TrafficLightContainer container;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    public ShowController(TrafficLightContainer trafficLightContainer) {
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
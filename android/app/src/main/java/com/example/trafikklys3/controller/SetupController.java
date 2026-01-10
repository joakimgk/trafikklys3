package com.example.trafikklys3.controller;

import static com.example.trafikklys3.model.Programs.PROGRAMS;
import static com.example.trafikklys3.network.EspProtocol.CMD_PROGRAM;
import static com.example.trafikklys3.network.EspProtocol.CMD_SWAP;

import com.example.trafikklys3.model.Client;
import com.example.trafikklys3.model.ClientRegistry;
import com.example.trafikklys3.network.EspProtocol;
import com.example.trafikklys3.network.ServerService;
import com.example.trafikklys3.ui.TrafficLight;
import com.example.trafikklys3.ui.TrafficLightContainer;

import java.util.ArrayList;
import java.util.List;

enum SetupPhase {
    IDLE,           // not in setup mode
    WAITING,        // setup started, but no client selected yet
    IDENTIFYING,    // ESP blinking
    PLACING,        // user dragging
    ROTATING,       // user rotating
    CONFIRMED,      // mapped, waiting for Next
    DONE            // all clients mapped
}

public class SetupController implements TrafficLightListener {

    private final ClientRegistry clientRegistry;
    private final TrafficLightContainer lightContainer;
    private ServerService serverService;

    private List<Client> setupClients;
    private List<TrafficLight> setupLights;
    private int currentIndex = -1;

    private Client currentClient;
    private TrafficLight currentLight;

    private SetupPhase phase = SetupPhase.IDLE;

    public SetupController(ClientRegistry registry,
                           TrafficLightContainer container,
                           ServerService serverService) {
        this.clientRegistry = registry;
        this.lightContainer = container;
        this.serverService = serverService;
    }

    public void startSetup() {
        setupClients = new ArrayList<>(clientRegistry.getClients());
        setupLights = new ArrayList<>(lightContainer.getLights());

        if (setupClients.isEmpty() || setupLights.isEmpty()) return;

        phase = SetupPhase.WAITING;
        currentIndex = -1;

        for (Client c : clientRegistry.getClients()) {
            c.stopIdentify();
        }

        // Ensure all lights are visibly "unassigned"
        for (TrafficLight tl : setupLights) {
            tl.setInactive();
        }

        advanceToNextClient();
    }

    private void advanceToNextClient() {
        stopIdentification();

        currentIndex++;

        if (currentIndex >= setupClients.size()) {
            phase = SetupPhase.DONE;
            onSetupFinished();
            return;
        }

        currentClient = setupClients.get(currentIndex);
        currentLight = null;

        startIdentification(currentClient);
        phase = SetupPhase.IDENTIFYING;
    }

    private void onSetupFinished() {
        serverService.sendToAll(EspProtocol.buildCommand(CMD_PROGRAM, PROGRAMS[1]));
        serverService.sendToAll(EspProtocol.buildCommand(CMD_SWAP));
    }

    private void startIdentification(Client client) {
        client.identify();
    }

    private void stopIdentification() {
        if (currentClient != null) {
            currentClient.stopIdentify();
        }
    }

    public SetupPhase getPhase() {
        return phase;
    }

    public boolean isAssigning() {
        return (phase != SetupPhase.IDLE && phase != SetupPhase.DONE);
    }

    @Override
    public void onTrafficLightClicked(TrafficLight light) {
        if (phase != SetupPhase.IDENTIFYING) return;

        currentLight = light;
        //currentLight.setHighlighted(true);

        bind(currentClient, currentLight);

        phase = SetupPhase.PLACING;
        advanceToNextClient();
    }

    private void bind(Client client, TrafficLight light) {
        client.setTrafficLight(light);
        light.setActive();
    }

    @Override
    public void onTrafficLightDragged(TrafficLight light, float dx, float dy) {

    }
}



package com.example.trafikklys3.network;

import com.example.trafikklys3.controller.ShowController;
import com.example.trafikklys3.controller.TrafficLightListener;
import com.example.trafikklys3.model.Client;
import com.example.trafikklys3.model.ClientRegistry;
import com.example.trafikklys3.model.SetupPhase;
import com.example.trafikklys3.ui.TrafficLight;
import com.example.trafikklys3.ui.TrafficLightContainer;

import java.util.ArrayList;
import java.util.List;

public class SetupService implements TrafficLightListener {

    private final ClientRegistry clientRegistry;
    private final TrafficLightContainer lightContainer;
    private ServerService serverService;
    private ShowController showController;

    private List<Client> setupClients;
    private List<TrafficLight> setupLights;
    private int currentIndex = -1;

    private Client currentClient;
    private TrafficLight currentLight;

    private SetupPhase phase = SetupPhase.IDLE;

    public SetupService(ClientRegistry registry,
                        TrafficLightContainer container,
                        ServerService serverService,
                        ShowController controller) {

        this.clientRegistry = registry;
        this.lightContainer = container;
        this.serverService = serverService;
        this.showController = controller;
    }

    public void startSetup() {
        setupClients = new ArrayList<>(clientRegistry.getClients());
        setupLights = new ArrayList<>(lightContainer.getLights());

        if (setupClients.isEmpty() || setupLights.isEmpty()) return;

        phase = SetupPhase.WAITING;
        currentIndex = -1;

        for (Client c : clientRegistry.getClients()) {
            serverService.stopIdentify(c);
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
        //serverService.sendToAll(EspProtocol.buildCommand(CMD_PROGRAM, PROGRAMS[1]));
        //serverService.sendToAll(EspProtocol.buildCommand(CMD_SWAP));
        showController.startShow();
    }

    private void startIdentification(Client client) {
        serverService.identify(client);
    }

    private void stopIdentification() {
        if (currentClient != null) {
            serverService.stopIdentify(currentClient);
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



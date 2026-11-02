package com.example.trafikklys3.network;

import com.example.trafikklys3.controller.ShowController;
import com.example.trafikklys3.model.Client;
import com.example.trafikklys3.model.ClientRegistry;
import com.example.trafikklys3.model.LightStore;
import com.example.trafikklys3.model.Programs;
import com.example.trafikklys3.model.SetupPhase;
import com.example.trafikklys3.ui.TrafficLight;
import com.example.trafikklys3.ui.TrafficLightContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Setup goes through the units that have no light yet, one at a time: the unit blinks (on the
 * real light and on the icon it is tied to right now), the user picks the matching light on
 * screen, moves or rotates it if needed, and presses the setup button to assign it.
 * When the last unit is assigned the show starts.
 */
public class SetupService {

    private final ClientRegistry clientRegistry;
    private final TrafficLightContainer lightContainer;
    private final ServerService serverService;
    private final ShowController showController;
    private final LightStore lightStore;

    private Supplier<TrafficLight> selection = () -> null;
    private Consumer<TrafficLight> selector = light -> { };

    private List<Client> setupClients;
    private int currentIndex = -1;

    private Client currentClient;
    private TrafficLight previewLight;

    private SetupPhase phase = SetupPhase.IDLE;

    public SetupService(ClientRegistry registry,
                        TrafficLightContainer container,
                        ServerService serverService,
                        ShowController controller,
                        LightStore lightStore) {

        this.clientRegistry = registry;
        this.lightContainer = container;
        this.serverService = serverService;
        this.showController = controller;
        this.lightStore = lightStore;
    }

    /** How setup reads and changes the light the user has picked on screen. */
    public void setSelectionAccess(Supplier<TrafficLight> get, Consumer<TrafficLight> set) {
        selection = get;
        selector = set;
    }

    /** Returns null when setup started, otherwise the reason it didn't. */
    public String startSetup() {
        setupClients = new ArrayList<>();
        for (Client c : clientRegistry.getClients()) {
            if (c.isOnline() && c.mTrafficLight == null) setupClients.add(c);
        }
        if (setupClients.isEmpty()) {
            return "Every unit already has a light. Remove a light to set its unit up again.";
        }

        // Nothing runs while the units are being set up: all the lamps go dark
        showController.stopShow();
        showController.setTempo(showController.getTempo());
        for (Client c : clientRegistry.getClients()) {
            if (c.isOnline()) serverService.stopIdentify(c);
        }

        currentIndex = -1;
        currentClient = null;
        previewLight = null;
        advanceToNextClient();
        return null;
    }

    /** The user has the right light picked for the blinking unit: assign it and move on. */
    public String confirmCurrent() {
        if (phase != SetupPhase.IDENTIFYING || currentClient == null) return null;

        if (previewLight.getClient() != null) {
            return "That light already has a unit. Pick a free light.";
        }

        serverService.stopIdentify(currentClient);
        showController.stopPreview(currentClient);

        previewLight.setClient(currentClient);
        currentClient.setTrafficLight(previewLight);
        lightStore.save(lightContainer.getLights());

        advanceToNextClient();
        return null;
    }

    private void advanceToNextClient() {
        currentIndex++;

        if (currentIndex >= setupClients.size()) {
            phase = SetupPhase.DONE;
            currentClient = null;
            previewLight = null;
            showController.startShow();
            return;
        }

        currentClient = setupClients.get(currentIndex);

        TrafficLight light = selection.get();
        if (light == null || light.getClient() != null) light = firstFreeLight();
        if (light == null) {
            // no free light on screen: make one
            light = lightContainer.addNewLight();
            lightStore.save(lightContainer.getLights());
        }

        // The icon is tied to the unit right away so it can blink along with it
        currentClient.setTrafficLight(light);
        previewLight = light;
        selector.accept(light);

        serverService.identify(currentClient);
        showController.preview(currentClient, Programs.IDENTIFY);
        phase = SetupPhase.IDENTIFYING;
    }

    private TrafficLight firstFreeLight() {
        for (TrafficLight l : lightContainer.getLights()) {
            if (l.getClient() == null) return l;
        }
        return null;
    }

    /** The user tapped a light during setup. Returns false if it can't be used for this unit. */
    public boolean onLightSelected(TrafficLight light) {
        if (phase != SetupPhase.IDENTIFYING || currentClient == null) return true;
        if (light.getClient() != null) return false;
        if (light == previewLight) return true;

        // move the blinking from the old icon to the new one
        showController.stopPreview(currentClient);
        currentClient.setTrafficLight(light);
        previewLight = light;
        showController.preview(currentClient, Programs.IDENTIFY);
        return true;
    }

    public SetupPhase getPhase() {
        return phase;
    }

    public boolean isAssigning() {
        return phase == SetupPhase.IDENTIFYING;
    }

    public String getButtonLabel() {
        if (!isAssigning()) return "Setup";
        int n = currentIndex + 1;
        return (n == setupClients.size() ? "Finish " : "Next ") + n + "/" + setupClients.size();
    }
}

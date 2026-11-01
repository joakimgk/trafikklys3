package com.example.trafikklys3.network;

import com.example.trafikklys3.controller.ShowController;
import com.example.trafikklys3.controller.TrafficLightListener;
import com.example.trafikklys3.model.Client;
import com.example.trafikklys3.model.ClientRegistry;
import com.example.trafikklys3.model.PairingStore;
import com.example.trafikklys3.model.Programs;
import com.example.trafikklys3.model.SetupPhase;
import com.example.trafikklys3.ui.TrafficLight;
import com.example.trafikklys3.ui.TrafficLightContainer;

import java.util.ArrayList;
import java.util.List;

/**
 * Setup runs one unit at a time: the unit's lamps blink (on the real light and on its on-screen
 * icon), the user drags and rotates the icon to match where that light stands, and presses the
 * setup button to confirm. When the last unit is confirmed the show starts.
 */
public class SetupService implements TrafficLightListener {

    private final ClientRegistry clientRegistry;
    private final TrafficLightContainer lightContainer;
    private ServerService serverService;
    private ShowController showController;
    private final PairingStore pairingStore;

    private List<Client> setupClients;
    private List<TrafficLight> setupLights;
    private int currentIndex = -1;

    private Client currentClient;
    private TrafficLight currentLight;

    private SetupPhase phase = SetupPhase.IDLE;

    public SetupService(ClientRegistry registry,
                        TrafficLightContainer container,
                        ServerService serverService,
                        ShowController controller,
                        PairingStore pairingStore) {

        this.clientRegistry = registry;
        this.lightContainer = container;
        this.serverService = serverService;
        this.showController = controller;
        this.pairingStore = pairingStore;
    }

    public void startSetup() {
        // Remembered units that are offline keep their icon and pairing
        setupClients = new ArrayList<>();
        List<TrafficLight> offlineLights = new ArrayList<>();
        for (Client c : clientRegistry.getClients()) {
            if (c.isOnline()) setupClients.add(c);
            else if (c.mTrafficLight != null) offlineLights.add(c.mTrafficLight);
        }
        setupLights = new ArrayList<>(lightContainer.getLights());
        setupLights.removeAll(offlineLights);

        if (setupClients.isEmpty() || setupLights.isEmpty()) return;

        // Nothing runs while the units are being placed
        showController.stopShow();
        showController.setTempo(showController.getTempo());

        for (Client c : setupClients) {
            serverService.stopIdentify(c);
            c.setTrafficLight(null);
        }

        // Every icon is "unassigned": dim and static
        for (TrafficLight tl : setupLights) {
            tl.setInactive();
            tl.setLamps((byte) 0b111);
        }

        currentIndex = -1;
        currentClient = null;
        currentLight = null;
        advanceToNextClient();
    }

    /** The user has placed the icon for the blinking unit: pair them and move on. */
    public void confirmCurrent() {
        if (phase != SetupPhase.IDENTIFYING || currentClient == null) return;

        serverService.stopIdentify(currentClient);
        showController.stopPreview(currentClient);

        currentLight.setActive();
        pairingStore.save(currentClient);

        advanceToNextClient();
    }

    private void advanceToNextClient() {
        currentIndex++;

        if (currentIndex >= setupClients.size()) {
            phase = SetupPhase.DONE;
            currentClient = null;
            currentLight = null;
            showController.startShow();
            return;
        }

        currentClient = setupClients.get(currentIndex);
        currentLight = firstUnassignedLight();
        if (currentLight == null) {
            // more units than icons: nothing left to place
            phase = SetupPhase.DONE;
            return;
        }

        // The icon is bound right away so it can blink along with the unit
        currentClient.setTrafficLight(currentLight);
        currentLight.setWorking();

        serverService.identify(currentClient);
        showController.preview(currentClient, Programs.IDENTIFY);
        phase = SetupPhase.IDENTIFYING;
    }

    private TrafficLight firstUnassignedLight() {
        for (TrafficLight tl : setupLights) {
            if (!tl.isActive() && !isBound(tl)) return tl;
        }
        return null;
    }

    private boolean isBound(TrafficLight light) {
        for (Client c : setupClients) {
            if (c.mTrafficLight == light) return true;
        }
        return false;
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

    @Override
    public void onTrafficLightChanged(TrafficLight light) {
        // Placement moved or rotated: remember it for the unit it belongs to
        if (!light.isActive()) return;  // still being placed during setup
        for (Client c : clientRegistry.getClients()) {
            if (c.mTrafficLight == light) pairingStore.save(c);
        }
    }

    @Override
    public void onTrafficLightDragged(TrafficLight light, float dx, float dy) {

    }
}

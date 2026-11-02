package com.example.trafikklys3.model;

import android.util.Log;
import android.util.Pair;

import com.example.trafikklys3.network.ServerService;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ClientRegistry {

    private static final String TAG = "ClientRegistry";

    private int ID = 0;

    private final Map<String, Client> clients = new HashMap<>();

    private ClientListener listener;

    public ClientRegistry() {
    }

    public void setListener(ClientListener listener) {
        this.listener = listener;
    }

    /** A unit remembered from an earlier session: listed (as offline) until it announces itself. */
    public synchronized Client addKnown(String clientID) {
        Client client = clients.get(clientID);
        if (client != null) return client;

        client = new Client(ID++, clientID, null);
        client.markOffline();
        clients.put(clientID, client);
        if (listener != null) listener.onClientAdded(client);
        return client;
    }

    public synchronized Client onAnnounce(String clientID, InetAddress addr, boolean hasProgram) {
        Client client = clients.get(clientID);
        boolean isNew = false;
        boolean addressChanged = false;

        if (client != null) client.touch();

        if (client == null) {
            isNew = true;
            client = new Client(ID++, clientID, addr);
            clients.put(clientID, client);
        } else {
            addressChanged = !addr.equals(client.getAddress());  // also true for a remembered unit
            client.setAddress(addr);
        }

        if (listener != null) {
            if (isNew) listener.onClientAdded(client);
            // Only resend the program if the unit lost it (reboot) or moved
            else if (!hasProgram || addressChanged) listener.onClientUpdated(client);
        }

        Log.d(TAG, client.toString());
        return client;
    }

    // A copy, since the receiver thread adds units while the UI iterates
    public synchronized Collection<Client> getClients() {
        return new ArrayList<>(clients.values());
    }

    public synchronized void remove(String clientID) {
        clients.remove(clientID);
    }

    /** Heartbeat from a unit. Old firmware sends no id, so fall back to the sender's address. */
    public synchronized void onHeartbeat(String clientID, InetAddress addr) {
        Client client = clientID != null ? clients.get(clientID) : null;
        if (client == null) {
            for (Client c : clients.values()) {
                if (addr.equals(c.getAddress())) {
                    client = c;
                    break;
                }
            }
        }
        if (client != null) client.touch();
    }

    public synchronized boolean noClients() { return clients.isEmpty(); }

    public static ArrayList<Client> verticalGroup = new ArrayList<Client>();

    public static ArrayList<Pair<Client, Client>> horizontalPairs = new ArrayList<Pair<Client, Client>>();


    public void updateGroups() {

        verticalGroup.clear();
        horizontalPairs.clear();

        ArrayList<Client> placed = new ArrayList<>();
        for (Client c : clients.values()) {
            if (c.mTrafficLight != null) placed.add(c);
        }

        // vertical units
        for (Client c : placed) {
            if (c.mTrafficLight.getCellOrientation() % 180 == 0) {
                verticalGroup.add(c);
            }
        }
        // horizontal pairs (may overlap)
        Client prev = null;
        for (Client c : placed) {
            if (prev != null) {
                if (c.mTrafficLight.getCellOrientation() % 180 == 90) {
                    if (prev.mTrafficLight.getCellOrientation() % 180 == 90) {
                        if (c.mTrafficLight.getCellOrientation() != prev.mTrafficLight.getCellOrientation()) {
                            horizontalPairs.add(new Pair(prev, c));
                        }
                    }
                }
            }
            prev = c;
        }
    }

}

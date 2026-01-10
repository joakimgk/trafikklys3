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

    private final Map<Integer, Client> clients = new HashMap<>();

    private ClientListener listener;

    private ServerService serverService;

    public ClientRegistry(ServerService serverService) {
        this.serverService = serverService;
    }

    public void setListener(ClientListener listener) {
        this.listener = listener;
    }

    public synchronized Client onAnnounce(int clientID, InetAddress addr) {
        Client client = clients.get(clientID);
        boolean isNew = false;

        if (client == null) {
            isNew = true;
            client = new Client(ID++, clientID, addr, serverService);
            clients.put(clientID, client);
        }

        if (listener != null) {
            if (isNew) listener.onClientAdded(client);
            else listener.onClientUpdated(client);
        }

        Log.d(TAG, client.toString());
        return client;
    }

    public Collection<Client> getClients() {
        return clients.values();
    }

    public boolean noClients() { return clients.values().isEmpty(); }

    public static ArrayList<Client> verticalGroup = new ArrayList<Client>();

    public static ArrayList<Pair<Client, Client>> horizontalPairs = new ArrayList<Pair<Client, Client>>();


    public void updateGroups() {

        verticalGroup.clear();
        horizontalPairs.clear();

        // vertical units
        for (Client c : clients.values()) {
            if (c.mTrafficLight.getCellOrientation() % 180 == 0) {
                verticalGroup.add(c);
            }
        }
        // horizontal pairs (may overlap)
        Client prev = null;
        for (int i = 0; i < clients.size(); i++) {
            Client c = clients.get(i);
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

package com.example.trafikklys3.model;

import android.util.Log;

import java.net.InetAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ClientRegistry {

    public interface Listener {
        void onClientAdded(Client client);
        void onClientUpdated(Client client);
    }

    private static final String TAG = "ClientRegistry";

    private int ID = 0;

    private final Map<Integer, Client> clients = new HashMap<>();

    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public synchronized Client onAnnounce(int clientID, InetAddress addr) {
        Client client = clients.get(clientID);
        boolean isNew = false;

        if (client == null) {
            isNew = true;
            client = new Client(ID++, clientID, addr);
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


}

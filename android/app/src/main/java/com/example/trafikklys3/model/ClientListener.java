package com.example.trafikklys3.model;

public interface ClientListener {
    void onClientAdded(Client client);
    void onClientUpdated(Client client);
}

package com.example.trafikklys3.network;

import com.example.trafikklys3.model.Client;

public interface NetworkSender {
    void sendToClient(Client client, byte[] packet);
    void sendBroadcast(byte[] packet);
    void sendToAll(byte[] packet);
}

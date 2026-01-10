package com.example.trafikklys3.network;

import com.example.trafikklys3.model.Client;

public interface NetworkSender {
    void sendUnicast(Client client, byte[] packet);
    void sendBroadcast(byte[] packet);
    void beacon(byte[] packet);
    void sendToAll(byte[] packet);
}

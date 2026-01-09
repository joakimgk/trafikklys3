package com.example.trafikklys3.network;

public class DiscoveryTask implements Runnable {

    private final ServerService server;
    private final byte[] discoveryPacket;

    public DiscoveryTask(ServerService server) {
        this.server = server;
        this.discoveryPacket = EspProtocol.buildCommand(EspProtocol.CMD_DISCOVER);
    }

    @Override
    public void run() {
        server.beacon(discoveryPacket);
    }

}


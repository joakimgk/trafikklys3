package com.example.trafikklys3.network;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class DiscoveryTask implements Runnable {

    private final ServerService server;
    private final InetAddress broadcastAddr;

    public DiscoveryTask(ServerService server) {
        this.server = server;
        try {
            this.broadcastAddr =
                    InetAddress.getByName("255.255.255.255");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        byte[] pkt = EspProtocol.buildCommand(EspProtocol.CMD_DISCOVER);
        server.send(pkt, broadcastAddr);
    }
}


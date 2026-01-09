package com.example.trafikklys3.network;

public class SyncTask implements Runnable {
    private final ServerService server;
    private final byte[] syncPacket;

    public SyncTask(ServerService server) {
        this.server = server;
        this.syncPacket = EspProtocol.buildCommand(EspProtocol.CMD_SYNC);
    }

    @Override
    public void run() {
        if (server.getRegistry().noClients()) return;
        server.sendToAll(syncPacket);
    }
}

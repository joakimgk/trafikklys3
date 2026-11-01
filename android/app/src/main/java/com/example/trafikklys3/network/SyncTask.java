package com.example.trafikklys3.network;

public class SyncTask implements Runnable {
    private final ServerService server;

    public SyncTask(ServerService server) {
        this.server = server;
    }

    @Override
    public void run() {
        server.sendSync();
    }
}

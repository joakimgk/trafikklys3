package com.example.trafikklys3.network;

import static com.example.trafikklys3.network.EspProtocol.buildCommand;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.example.trafikklys3.controller.ShowController;
import com.example.trafikklys3.model.Client;
import com.example.trafikklys3.model.ClientRegistry;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerService implements NetworkSender {

    private static final String TAG = "ServerService";

    private static final int UDP_PORT = 4210;

    private static final int REPEAT_BROADCAST = 3;
    private static final int REPEAT_UNICAST = 1;

    private static final int INTERVAL_BROADCAST = 2;
    private static final int INTERVAL_UNICAST = 60;

    private final Context context;

    private WifiManager wifiManager;
    private WifiManager.MulticastLock multicastLock;

    private InetAddress broadcastAddr;

    private DatagramSocket socket;

    private ExecutorService receiverExecutor;
    private ScheduledExecutorService discoveryScheduler, retransmitScheduler;

    private ClientRegistry registry;
    private EspProtocol protocolHandler;
    private ShowController controller;

    private ExecutorService sendExecutor;


    public ServerService(Context context) {
        this.context = context.getApplicationContext();

        // ---- Client Registry ----
        this.registry = new ClientRegistry();
        this.protocolHandler = new EspProtocol(registry);

        try {
            this.broadcastAddr =
                    InetAddress.getByName("255.255.255.255");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public void start() throws SocketException {
        Log.d(TAG, "Starting");
        sendExecutor = Executors.newSingleThreadExecutor();

        // ---- Wi-Fi / multicast setup ----
        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        multicastLock = wifiManager.createMulticastLock("esp-discovery");
        multicastLock.setReferenceCounted(true);
        multicastLock.acquire();

        // ---- Socket ----
        socket = new DatagramSocket(UDP_PORT);
        socket.setReuseAddress(true);
        socket.setBroadcast(true);

        // ---- Receiver thread ----
        receiverExecutor = Executors.newSingleThreadExecutor();
        receiverExecutor.execute(new UdpReceiver(socket, protocolHandler::handleUdpPacket));

        // ---- Retransmit thread ----
        retransmitScheduler = Executors.newSingleThreadScheduledExecutor();

        // ---- Discovery broadcast ----
        startUdpDiscovery();
    }

    public ClientRegistry getRegistry() {
        return registry;
    }


    public void stop() {
        if (sendExecutor != null) {
            sendExecutor.shutdownNow();
        }
        if (socket != null) socket.close();

        if (receiverExecutor != null)
            receiverExecutor.shutdownNow();

        if (discoveryScheduler != null)
            discoveryScheduler.shutdownNow();

        if (retransmitScheduler != null)
            retransmitScheduler.shutdownNow();

        if (multicastLock != null && multicastLock.isHeld())
            multicastLock.release();
    }

    // ---- One-off UDP send (unicast or broadcast) ----
    private synchronized void send(byte[] packet, InetAddress addr) {
        try {
            DatagramPacket p =
                    new DatagramPacket(packet, packet.length, addr, UDP_PORT);
            socket.send(p);
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }
    }

    // ---- Discovery broadcast scheduling ----
    private void startUdpDiscovery() {
        discoveryScheduler = Executors.newSingleThreadScheduledExecutor();
        discoveryScheduler.scheduleAtFixedRate(
                new DiscoveryTask(this),
                0, 10, TimeUnit.SECONDS
        );
    }

    public void sendRepeated(
            byte[] packet,
            InetAddress addr,
            int repeatCount,
            long intervalMs) {

        final AtomicInteger remaining = new AtomicInteger(repeatCount);

        retransmitScheduler.scheduleAtFixedRate(() -> {
            if (remaining.decrementAndGet() < 0) {
                return;
            }

            sendExecutor.execute(() ->
                send(packet, addr)
            );

        }, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void sendToClient(Client client, byte[] packet) {
        // Unicast
        sendRepeated(packet, client.getAddress(), REPEAT_UNICAST, INTERVAL_UNICAST);
        /*
        sendExecutor.execute(() ->
            send(packet, client.getAddress())
        );*/
    }

    @Override
    public void sendBroadcast(byte[] packet) {
        if (registry.noClients()) return;
        sendRepeated(packet, this.broadcastAddr, REPEAT_BROADCAST, INTERVAL_BROADCAST);
        /*sendExecutor.execute(() ->
            send(packet, this.broadcastAddr)
        );*/
    }

    @Override
    public void beacon(byte[] packet) {
        sendRepeated(packet, this.broadcastAddr, REPEAT_BROADCAST, INTERVAL_BROADCAST);
        /*sendExecutor.execute(() ->
                send(packet, this.broadcastAddr) // sends ONCE
        );*/
    }

    @Override
    public void sendToAll(byte[] packet) {
        for (Client c : registry.getClients()) {
            sendToClient(c, packet);
        }
    }
}

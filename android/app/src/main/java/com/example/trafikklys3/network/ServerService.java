package com.example.trafikklys3.network;

import static com.example.trafikklys3.network.EspProtocol.buildCommand;

import android.content.Context;
import android.net.wifi.WifiManager;

import com.example.trafikklys3.controller.ShowController;
import com.example.trafikklys3.model.ClientRegistry;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServerService {

    private static final String TAG = "ServerService";

    private static final int UDP_PORT = 4210;

    private final Context context;

    private WifiManager wifiManager;
    private WifiManager.MulticastLock multicastLock;

    private DatagramSocket socket;

    private ExecutorService receiverExecutor;
    private ScheduledExecutorService discoveryScheduler;

    private ClientRegistry registry;
    private EspProtocol protocolHandler;
    private ShowController controller;


    public ServerService(Context context, ShowController controller) {
        this.context = context.getApplicationContext();
        this.controller = controller;

        // ---- Client Registry ----
        this.registry = new ClientRegistry();
        this.registry.setListener(controller);
        this.protocolHandler = new EspProtocol(registry);
    }

    public void start() throws SocketException {
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

        // ---- Discovery broadcast ----
        startUdpDiscovery();
    }


    public void stop() {
        if (socket != null) socket.close();

        if (receiverExecutor != null)
            receiverExecutor.shutdownNow();

        if (discoveryScheduler != null)
            discoveryScheduler.shutdownNow();

        if (multicastLock != null && multicastLock.isHeld())
            multicastLock.release();
    }

    // ---- One-off UDP send (unicast or broadcast) ----
    public synchronized void send(byte[] packet, InetAddress addr) {
        try {
            DatagramPacket p =
                    new DatagramPacket(packet, packet.length, addr, UDP_PORT);
            socket.send(p);
        } catch (IOException e) {
            // log / handle
        }
    }

    // ---- Discovery broadcast scheduling ----
    private void startUdpDiscovery() {
        discoveryScheduler = Executors.newSingleThreadScheduledExecutor();
        discoveryScheduler.scheduleAtFixedRate(
                new DiscoveryTask(this),
                0, 1, TimeUnit.SECONDS
        );
    }
}

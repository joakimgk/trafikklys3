package com.example.trafikklys3.network;

import static com.example.trafikklys3.network.EspProtocol.CMD_DISCOVER;

import android.util.Log;

import com.example.trafikklys3.model.ClientRegistry;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.function.BiConsumer;

public class UdpReceiver implements Runnable {

    private static final String TAG = "UdpReceiver";

    private final DatagramSocket socket;
    private final PacketHandler handler;
    private ClientRegistry registry;

    public interface PacketHandler {
        void onPacket(InetAddress from, byte[] data, int length, EspDecoder.OnPacketHandler packetHandler);
    }

    public UdpReceiver(DatagramSocket socket, PacketHandler handler, ClientRegistry registry) {
        this.socket = socket;
        this.handler = handler;
        this.registry = registry;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[512];

        while (!socket.isClosed()) {
            try {
                DatagramPacket packet =
                        new DatagramPacket(buffer, buffer.length);

                socket.receive(packet);


                // pakk ut / dekod innhold

                switch (cmd) {

                    case CMD_DISCOVER: {  // CMD_ANNOUNCE

                        registr.onAnnounce()

                        handler.onPacket(
                                packet.getAddress(),
                                packet.getData(),
                                packet.getLength(),
                                (String id, InetAddress address) -> registry.onAnnounce(id, address)

                        );

            } catch (IOException e) {
                if (!socket.isClosed()) {
                    Log.e(TAG, "UDP receive error", e);
                }
                break;
            }
        }
    }
}

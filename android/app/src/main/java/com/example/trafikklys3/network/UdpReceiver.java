package com.example.trafikklys3.network;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UdpReceiver implements Runnable {

    private static final String TAG = "UdpReceiver";

    private final DatagramSocket socket;
    private final PacketHandler handler;

    public interface PacketHandler {
        void onPacket(InetAddress from, byte[] data, int length);
    }

    public UdpReceiver(DatagramSocket socket, PacketHandler handler) {
        this.socket = socket;
        this.handler = handler;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[512];

        while (!socket.isClosed()) {
            try {
                DatagramPacket packet =
                        new DatagramPacket(buffer, buffer.length);

                socket.receive(packet);

                handler.onPacket(
                        packet.getAddress(),
                        packet.getData(),
                        packet.getLength()
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

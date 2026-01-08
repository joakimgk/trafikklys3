package com.example.trafikklys3.network;

import android.util.Log;

import com.example.trafikklys3.model.ClientRegistry;

import java.net.InetAddress;

public final class EspProtocol {
    private static final String TAG = "EspProtocol";

    public static ClientRegistry registry;

    public EspProtocol(ClientRegistry registry) {
        this.registry = registry;
    }

    // ---- Command codes (App -> ESP) ----
    public static final byte CMD_DISCOVER = 0x77;
    public static final byte CMD_READY    = 0x00;
    public static final byte CMD_SYNC     = 0x02;
    public static final byte CMD_PROGRAM  = 0x03;
    public static final byte CMD_APPLY    = 0x04;

    public static byte[] buildCommand(byte cmd, byte[] payload) {
        int payloadLen = (payload != null) ? payload.length : 0;

        if (payloadLen > 255) {
            throw new IllegalArgumentException("Payload too large (max 255 bytes)");
        }

        byte[] packet = new byte[2 + payloadLen];
        packet[0] = cmd;
        packet[1] = (byte) payloadLen;

        if (payloadLen > 0) {
            System.arraycopy(payload, 0, packet, 2, payloadLen);
        }

        return packet;
    }

    public static byte[] buildCommand(byte cmd) {
        return buildCommand(cmd, null);
    }



    public void handleUdpPacket(
            InetAddress from,
            byte[] data,
            int length) {

        if (length < 2) return;

        int cmd = data[0] & 0xFF;
        int len = data[1] & 0xFF;

        if (len + 2 > length) {
            Log.w(TAG, "Malformed UDP packet");
            return;
        }

        byte[] payload = new byte[len];
        System.arraycopy(data, 2, payload, 0, len);

        handlePacket(cmd, payload, from);
    }


    private void handlePacket(
            int cmd,
            byte[] payload,
            InetAddress from) {

        switch (cmd) {

            case CMD_DISCOVER: {  // CMD_ANNOUNCE
                if (payload.length < 4) return;

                int espId =
                        ((payload[0] & 0xFF) << 24) |
                                ((payload[1] & 0xFF) << 16) |
                                ((payload[2] & 0xFF) << 8) |
                                (payload[3] & 0xFF);

                Log.d(TAG,
                        "ESP announce id=" + espId +
                                " ip=" + from.getHostAddress());

                registry.onAnnounce(espId, from);

                break;
            }
        }
    }
}

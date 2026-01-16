package com.example.trafikklys3.network;

import android.util.Log;

import com.example.trafikklys3.model.ClientRegistry;

import java.net.InetAddress;

public final class EspProtocol {
    private static final String TAG = "EspProtocol";

    private static boolean testUnits = true;  // invert program (LED) bits

    public static ClientRegistry registry;

    public EspProtocol(ClientRegistry registry) {
        this.registry = registry;
    }

    // ---- Command codes (App -> ESP) ----
    public static final byte CMD_DISCOVER = 0x77;

    public static final byte CMD_TEMPO    = 0x01;
    public static final byte CMD_RESET    = 0x02;
    public static final byte CMD_PROGRAM  = 0x03;
    public static final byte CMD_SWAP     = 0x04;
    public static final byte CMD_SYNC     = 0x05;

    public static final byte CMD_IDENTIFY = 0x06;
    public static final byte CMD_READY = 0x07;

    private static byte[] invertedCopy(byte[] data) {
        if (data == null) return null;
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) ~data[i];
        }
        return out;
    }

    public static byte[] buildCommand(byte cmd, byte[] payload) {
        int payloadLen = (payload != null) ? payload.length : 0;

        if (payloadLen > 255) {
            throw new IllegalArgumentException("Payload too large (max 255 bytes)");
        }

        byte[] encodedPayload = payload;
        if (testUnits && cmd == CMD_PROGRAM && payloadLen > 0) {
            encodedPayload = invertedCopy(payload);
        }

        byte[] packet = new byte[2 + payloadLen];
        packet[0] = cmd;
        packet[1] = (byte) payloadLen;



        if (payloadLen > 0) {
            System.arraycopy(encodedPayload, 0, packet, 2, payloadLen);
        }

        return packet;
    }

    public static byte[] buildCommand(byte cmd) {
        return buildCommand(cmd, null);
    }




}

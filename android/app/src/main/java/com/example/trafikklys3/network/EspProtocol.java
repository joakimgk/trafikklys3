package com.example.trafikklys3.network;

public final class EspProtocol {
    private EspProtocol() {} // prevent instantiation

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


    // ---- Optional: ESP -> App message types ----
    public static final String MSG_CLIENT_ID = "clientID";
}

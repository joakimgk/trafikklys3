package com.example.trafikklys3.network;

import static com.example.trafikklys3.network.EspProtocol.CMD_DISCOVER;
import static com.example.trafikklys3.network.EspProtocol.CMD_IDENTIFY;

import android.util.Log;

import com.example.trafikklys3.model.ClientRegistry;

import java.net.InetAddress;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class EspDecoder {

    private static final String TAG = "EspDecoder";

    public static void handleUdpPacket(
            InetAddress from,
            byte[] data,
            int length,
            ClientRegistry registry) {

        if (length < 2) return;

        int cmd = data[0] & 0xFF;
        int len = data[1] & 0xFF;

        if (len + 2 > length) {
            Log.w(TAG, "Malformed UDP packet");
            return;
        }

        byte[] payload = new byte[len];
        System.arraycopy(data, 2, payload, 0, len);

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

                registry.onAnnounce(espId + "", from);

                break;
            }

            /*
            case CMD_IDENTIFY:

                // .... ... processs!

                packetHandler.noe(espId, from);

             */
        }
    }
}

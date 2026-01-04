package com.example.trafikklys3.network;

import static com.example.trafikklys3.network.EspProtocol.CMD_DISCOVER;
import static com.example.trafikklys3.network.EspProtocol.buildCommand;

import java.net.InetSocketAddress;
import java.net.Socket;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

public class ServerService {

    private static final String TAG = "ServerService";
    private static final int TCP_PORT = 10000;
    private static final int UDP_SEND_PORT = 4210;
    private static final int UDP_READ_PORT = 4211;

    private Thread tcpThread;
    private Thread udpThread;
    private Thread udpRxThread;
    private DatagramSocket udpRxSocket;

    private volatile boolean running = false;

    public void start(Context context) {
        running = true;
        //startTcpServer();
        startUdpBroadcast(context);
    }

    public void stop() {
        running = false;
    }

    private void startTcpServer() {
        tcpThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
                Log.d(TAG, "TCP server listening on " + TCP_PORT);

                while (running) {
                    Socket client = serverSocket.accept();
                    Log.d(TAG, "ESP connected: " + client.getInetAddress());
                }
            } catch (IOException e) {
                Log.e(TAG, "TCP server error", e);
            }
        }, "TCP-Server");

        tcpThread.start();
    }

    private void startUdpReceiver() {

        udpRxThread = new Thread(() -> {
            try {
                udpRxSocket = new DatagramSocket(UDP_READ_PORT);
                udpRxSocket.setReuseAddress(true);

                Log.d(TAG, "UDP receiver listening on " + UDP_READ_PORT);

                byte[] buffer = new byte[512];

                while (running) {
                    DatagramPacket packet =
                            new DatagramPacket(buffer, buffer.length);

                    udpRxSocket.receive(packet);

                    InetAddress fromAddr = packet.getAddress();
                    int length = packet.getLength();

                    handleUdpPacket(fromAddr, buffer, length);
                }

            } catch (Exception e) {
                if (running) {
                    Log.e(TAG, "UDP receiver error", e);
                }
            }
        }, "UDP-Rx");

        udpRxThread.start();
    }


    private void handleUdpPacket(
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

        Log.d(TAG,
                "UDP RX cmd=" + cmd +
                        " len=" + len +
                        " from=" + from.getHostAddress());

        handlePacket(cmd, payload, from);
    }

    private void handlePacket(
            int cmd,
            byte[] payload,
            InetAddress from) {

        switch (cmd) {

            case 0x01: {  // CMD_ANNOUNCE
                if (payload.length < 4) return;

                int espId =
                        ((payload[0] & 0xFF) << 24) |
                                ((payload[1] & 0xFF) << 16) |
                                ((payload[2] & 0xFF) << 8) |
                                (payload[3] & 0xFF);

                Log.d(TAG,
                        "ESP announce id=" + espId +
                                " ip=" + from.getHostAddress());

                // TODO:
                // - add/update ESP in registry
                // - mark lastSeen
                // - notify ShowController / UI

                break;
            }
        }
    }



    private void startUdpBroadcast(Context context) {

        udpThread = new Thread(() -> {

            WifiManager wifi =
                    (WifiManager) context.getApplicationContext()
                            .getSystemService(Context.WIFI_SERVICE);

            WifiManager.MulticastLock multicastLock =
                    wifi.createMulticastLock("trafikklys_udp");
            multicastLock.setReferenceCounted(true);
            multicastLock.acquire();

            DatagramSocket socket = null;
            int count = 0;

            try {
                InetAddress broadcastAddr = getBroadcastAddress(wifi);

                Log.d(TAG, "UDP broadcast address: " + broadcastAddr.getHostAddress());
                Log.e(TAG, "Server IP      = " + getWifiIp(context));

                socket = new DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(0));
                socket.setBroadcast(true);

                // TODO: Support empty messages (len = 0)
                byte[] dummyData = new byte[] {
                        0x01, 0x02, 0x03
                };

                byte[] payload = buildCommand(CMD_DISCOVER, dummyData);

                while (running) {

                    DatagramPacket packet =
                            new DatagramPacket(
                                    payload,
                                    payload.length,
                                    broadcastAddr,
                                    UDP_SEND_PORT
                            );

                    socket.send(packet);
                    if (count++ % 20 == 0) Log.d(TAG, "UDP broadcast sent");

                    Thread.sleep(1000);
                }

            } catch (Exception e) {
                Log.e(TAG, "UDP broadcast error", e);

            } finally {
                if (socket != null) {
                    socket.close();
                }
                multicastLock.release();
            }

        }, "UDP-Broadcast");

        udpThread.start();
    }

    private static String getWifiIp(Context context) {
        WifiManager wifi =
                (WifiManager) context.getApplicationContext()
                        .getSystemService(Context.WIFI_SERVICE);

        int ip = wifi.getConnectionInfo().getIpAddress();

        return (ip & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                ((ip >> 24) & 0xFF);
    }


    private static InetAddress getBroadcastAddress(WifiManager wifi)
            throws IOException {

        DhcpInfo dhcp = wifi.getDhcpInfo();
        if (dhcp == null) {
            throw new IOException("No DHCP info");
        }

        // Convert little-endian to big-endian
        int ip = Integer.reverseBytes(dhcp.ipAddress);
        int mask = dhcp.netmask != 0
                ? Integer.reverseBytes(dhcp.netmask)
                : 0xFFFFFF00; // fallback /24

        int broadcast = (ip & mask) | ~mask;

        byte[] quads = new byte[] {
                (byte) ((broadcast >> 24) & 0xFF),
                (byte) ((broadcast >> 16) & 0xFF),
                (byte) ((broadcast >> 8) & 0xFF),
                (byte) (broadcast & 0xFF)
        };

        return InetAddress.getByAddress(quads);
    }


}
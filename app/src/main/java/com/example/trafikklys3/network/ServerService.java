package com.example.trafikklys3.network;

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
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ServerService {

    private static final String TAG = "ServerService";
    private static final int TCP_PORT = 10000;
    private static final int UDP_PORT = 4210;

    private Thread tcpThread;
    private Thread udpThread;
    private volatile boolean running = false;

    public void start(Context context) {
        running = true;
        startTcpServer();
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

                byte[] payload =
                        ("TL_SERVER:10000").getBytes(StandardCharsets.UTF_8);

                while (running) {

                    DatagramPacket packet =
                            new DatagramPacket(
                                    payload,
                                    payload.length,
                                    broadcastAddr,
                                    UDP_PORT
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
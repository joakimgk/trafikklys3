package com.example.trafikklys3.model;

import static com.example.trafikklys3.network.EspProtocol.CMD_IDENTIFY;
import static com.example.trafikklys3.network.EspProtocol.CMD_PROGRAM;
import static com.example.trafikklys3.network.EspProtocol.CMD_READY;
import static com.example.trafikklys3.network.EspProtocol.CMD_SWAP;

import com.example.trafikklys3.network.EspProtocol;
import com.example.trafikklys3.network.ServerService;
import com.example.trafikklys3.ui.TrafficLight;

import java.net.InetAddress;
import java.sql.Timestamp;

import java.util.Comparator;

public class Client {

    Timestamp created;
    int ID;
    public long clientID;
    InetAddress addr;
    byte[] buffer;
    public TrafficLight mTrafficLight;
    private final ServerService serverService;

    boolean active = false;

    public Client (int id, long cid, InetAddress addr, ServerService serverService) {
        this.ID = id;
        this.clientID = cid;
        this.addr = addr;
        this.serverService = serverService;
        //rotation = ID % 2 == 0 ? 90 : 0;
        this.created = new Timestamp(System.currentTimeMillis());
        mTrafficLight = null; // not mapped up in UX yet!
    }

    public InetAddress getAddress() {
        return this.addr;
    }

    public void setTrafficLight(TrafficLight unit) {
        this.mTrafficLight = unit;
    }

    public void identify() {
        serverService.sendUnicast(this, EspProtocol.buildCommand(CMD_PROGRAM, Programs.IDENTIFY));
        serverService.sendUnicast(this, EspProtocol.buildCommand(CMD_SWAP));
    }

    public void stopIdentify() {
        serverService.sendUnicast(this, EspProtocol.buildCommand(CMD_PROGRAM, new byte[]{Programs.NON}));
        serverService.sendUnicast(this, EspProtocol.buildCommand(CMD_SWAP));
    }

    @Override
    public String toString() {
        return "Client{" +
                "ID=" + ID +
                ", clientID=" + clientID +
                ", addr=" + addr +
                ", active=" + active +
                '}';
    }

    public static class ClientsComparator implements Comparator<Client> {

        @Override
        public int compare(Client o1, Client o2) {
            return o1.mTrafficLight.cellX > o2.mTrafficLight.cellX ? 1 : -1;
        }
    }
}


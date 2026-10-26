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
    public String clientID;
    InetAddress addr;
    byte[] buffer;
    public TrafficLight mTrafficLight;

    boolean active = false;

    public Client (int id, String cid, InetAddress addr) {
        this.ID = id;
        this.clientID = cid;
        this.addr = addr;
        //rotation = ID % 2 == 0 ? 90 : 0;
        this.created = new Timestamp(System.currentTimeMillis());
        mTrafficLight = null; // not mapped up in UX yet!
    }

    public InetAddress getAddress() {
        return this.addr;
    }

    public void setAddress(InetAddress addr) {
        this.addr = addr;
    }

    public void setTrafficLight(TrafficLight unit) {
        this.mTrafficLight = unit;
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


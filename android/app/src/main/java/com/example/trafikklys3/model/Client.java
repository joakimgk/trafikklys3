package com.example.trafikklys3.model;

import com.example.trafikklys3.ui.TrafficLight;

import java.net.InetAddress;
import java.sql.Timestamp;

public class Client {

    Timestamp created;
    int ID;
    long clientID;
    InetAddress addr;
    byte[] buffer;
    TrafficLight mTrafficLight;

    boolean active = false;

    public Client (int id, long cid, InetAddress addr) {
        this.ID = id;
        this.clientID = cid;
        this.addr = addr;
        //rotation = ID % 2 == 0 ? 90 : 0;
        this.created = new Timestamp(System.currentTimeMillis());
        mTrafficLight = null; // not mapped up in UX yet!
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
}

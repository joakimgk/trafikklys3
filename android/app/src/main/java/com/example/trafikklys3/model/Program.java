package com.example.trafikklys3.model;

public class Program {

    public byte[] mProgram;

    public Client mClient;

    public Program(byte[] p) {
        mProgram = p;
    }

    public Program(byte[] p, Client c) {
        mProgram = p;
        mClient = c;
    }

}
package com.example.trafikklys3.model;

public class Programs {

    public static byte NON = (byte) 0b00000000;
    public static byte RED = (byte) 0b00000001;
    public static byte YLW = (byte) 0b00000010;
    public static byte GRN = (byte) 0b00000100;

    public static byte ALL = (byte) 0b00000111;

    public static final byte[] IDENTIFY  = {
            ALL,
            NON
    };

    public static final byte[] CALIBRATE = {
            RED, YLW, GRN
    };

    public static final byte[] PAUSE = {
            (byte) 0x00,
            (byte) 0x00,
            (byte) 0x00
    };

    public static final byte[][] PROGRAMS = {
            {
                    GRN, GRN, GRN, GRN, GRN, GRN, GRN, GRN,
                    RED, RED, RED, RED, RED, RED, RED, RED
            },
            {
                    GRN, YLW, RED, YLW
            },
            {
                    (byte) (RED | GRN), YLW, (byte) (RED | GRN)
            }
    };
}

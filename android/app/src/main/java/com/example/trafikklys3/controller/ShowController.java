package com.example.trafikklys3.controller;

import android.content.Context;
import android.util.Log;

public class ShowController {

    public ShowController(Context context) {
    }

    public void start() {
        Log.d("ShowController", "start()");
    }

    public void stop() {
        Log.d("ShowController", "stop()");
    }

    public void setTempo(int tempo) {
        Log.d("ShowController", "setTempo: " + tempo);
    }
}
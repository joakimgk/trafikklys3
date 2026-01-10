package com.example.trafikklys3.controller;

import com.example.trafikklys3.ui.TrafficLight;

public interface TrafficLightListener {
    void onTrafficLightClicked(TrafficLight light);
    void onTrafficLightDragged(TrafficLight light, float dx, float dy);
    boolean isAssigning();
}

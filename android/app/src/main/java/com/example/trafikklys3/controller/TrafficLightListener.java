package com.example.trafikklys3.controller;

import com.example.trafikklys3.ui.TrafficLight;

public interface TrafficLightListener {
    void onTrafficLightDragged(TrafficLight light, float dx, float dy);
    void onTrafficLightChanged(TrafficLight light);
    boolean isAssigning();
}

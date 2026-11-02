package com.example.trafikklys3.controller;

import com.example.trafikklys3.ui.TrafficLight;

public interface TrafficLightListener {
    /** The user tapped (or dragged) a light. */
    void onTrafficLightSelected(TrafficLight light);

    /** A light was moved or rotated. */
    void onTrafficLightChanged(TrafficLight light);
}

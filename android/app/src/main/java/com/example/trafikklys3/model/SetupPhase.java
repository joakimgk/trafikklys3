package com.example.trafikklys3.model;

public enum SetupPhase {
    IDLE,           // not in setup mode
    WAITING,        // setup started, but no client selected yet
    IDENTIFYING,    // ESP blinking
    PLACING,        // user dragging
    ROTATING,       // user rotating
    CONFIRMED,      // mapped, waiting for Next
    DONE            // all clients mapped
}

package com.example.trafikklys3.model;

public enum LightStatus {
    UNASSIGNED,     // no unit belongs to this light, and none is available
    ONLINE,         // its unit is reporting in (or a free unit is available for it)
    OFFLINE         // it has a unit, but the unit has stopped reporting
}

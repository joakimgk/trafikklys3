package com.example.trafikklys3.ui;

import android.graphics.Canvas;

import com.example.trafikklys3.model.LightNode;

public class TrafficLightDrawable {

    private final LightNode node;
    private boolean selected;

    public TrafficLightDrawable(LightNode node) {
        this.node = node;
    }

    public void draw(Canvas canvas) {
        // use node.x / node.y
    }

    public boolean hitTest(float x, float y) {
        // geometry only
        return false;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public LightNode getNode() {
        return node;
    }
}

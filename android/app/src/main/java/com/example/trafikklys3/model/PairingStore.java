package com.example.trafikklys3.model;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.trafikklys3.ui.TrafficLight;

/**
 * Remembers which on-screen light belongs to which unit, so setup survives an app restart.
 */
public class PairingStore {

    private static final String PREFS = "pairing";

    private final SharedPreferences prefs;

    public PairingStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public java.util.Set<String> savedIds() {
        return new java.util.HashSet<>(prefs.getAll().keySet());
    }

    public void save(Client client) {
        TrafficLight light = client.mTrafficLight;
        if (light == null) return;
        prefs.edit()
                .putString(client.clientID,
                        light.cellX + "," + light.cellY + "," + light.getCellOrientation())
                .apply();
    }

    /** Applies the saved placement to the light and binds it to the client. Returns false if unpaired. */
    public boolean restore(Client client, TrafficLight light) {
        String saved = prefs.getString(client.clientID, null);
        if (saved == null) return false;

        String[] parts = saved.split(",");
        if (parts.length != 3) return false;

        try {
            light.restore(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return false;
        }
        client.setTrafficLight(light);
        return true;
    }
}

package com.example.trafikklys3.model;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.trafikklys3.ui.TrafficLight;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Remembers the lights on screen (id, place, rotation and which unit belongs to each),
 * so the layout survives an app restart.
 */
public class LightStore {

    private static final String PREFS = "lights";
    private static final String OLD_PREFS = "pairing";  // one "x,y,orientation" entry per unit
    private static final String KEY = "all";

    public static class Record {
        public final int id;
        public final int x;
        public final int y;
        public final int orientation;
        public final String clientId;  // null if no unit is assigned

        Record(int id, int x, int y, int orientation, String clientId) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.orientation = orientation;
            this.clientId = clientId;
        }
    }

    private final SharedPreferences prefs;
    private final SharedPreferences oldPrefs;

    public LightStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        oldPrefs = context.getSharedPreferences(OLD_PREFS, Context.MODE_PRIVATE);
    }

    public void save(List<TrafficLight> lights) {
        StringBuilder sb = new StringBuilder();
        for (TrafficLight l : lights) {
            Client c = l.getClient();
            sb.append(l.getLightId()).append(',')
                    .append(l.cellX).append(',')
                    .append(l.cellY).append(',')
                    .append(l.getCellOrientation()).append(',')
                    .append(c != null ? c.clientID : "")
                    .append('\n');
        }
        prefs.edit().putString(KEY, sb.toString()).apply();
    }

    public List<Record> load() {
        List<Record> out = new ArrayList<>();
        String saved = prefs.getString(KEY, null);
        if (saved == null) return migrateOld();

        for (String line : saved.split("\n")) {
            String[] p = line.split(",", -1);
            if (p.length != 5) continue;
            try {
                out.add(new Record(Integer.parseInt(p[0]), Integer.parseInt(p[1]),
                        Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                        p[4].isEmpty() ? null : p[4]));
            } catch (NumberFormatException e) {
                // skip a damaged line
            }
        }
        return out;
    }

    // Earlier versions saved one placement per unit; turn those into lights
    private List<Record> migrateOld() {
        List<Record> out = new ArrayList<>();
        int id = 1;
        for (Map.Entry<String, ?> e : oldPrefs.getAll().entrySet()) {
            String[] p = String.valueOf(e.getValue()).split(",");
            if (p.length != 3) continue;
            try {
                out.add(new Record(id++, Integer.parseInt(p[0]), Integer.parseInt(p[1]),
                        Integer.parseInt(p[2]), e.getKey()));
            } catch (NumberFormatException ex) {
                // skip a damaged entry
            }
        }
        return out;
    }
}

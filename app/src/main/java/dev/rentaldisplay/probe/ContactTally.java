package dev.rentaldisplay.probe;

import java.util.Map;
import java.util.TreeMap;

/** Counts primary touch-downs by input origin and contact profile so a run can be summarised in one log line. */
final class ContactTally {
    private final Map<String,Integer> counts = new TreeMap<>();
    void add(String origin, String profile) { counts.merge(origin + "/" + profile, 1, Integer::sum); }
    void reset() { counts.clear(); }
    int total() { int n = 0; for (int v : counts.values()) n += v; return n; }
    String summary() {
        if (counts.isEmpty()) return "none";
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String,Integer> e : counts.entrySet()) {
            if (out.length() > 0) out.append(", ");
            out.append(e.getKey()).append('=').append(e.getValue());
        }
        return out.toString();
    }
}

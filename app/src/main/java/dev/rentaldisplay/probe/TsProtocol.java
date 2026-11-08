package dev.rentaldisplay.probe;

import java.util.HashMap;
import java.util.Map;

/**
 * Parser for the head unit's text touch protocol, as implemented by the car-pushed helper
 * com.garmin.android.apps.usblink.UsbLinkTS: space-separated key=value pairs, one command per line.
 * "a=init sw sh cw ch" gives phone (server) and car (client) sizes; "a=ts x y b" (or bare "x y b")
 * is a touch in car coordinates where b is the MotionEvent action.
 */
final class TsProtocol {
    static final int PORT = 8051;
    static final String BANNER = "USBLINKTS v1.0";
    enum Kind { INIT, TOUCH, UNKNOWN }

    static final class Command {
        final Kind kind;
        final int sw, sh, cw, ch, x, y, b;
        Command(Kind kind, int sw, int sh, int cw, int ch, int x, int y, int b) {
            this.kind = kind; this.sw = sw; this.sh = sh; this.cw = cw; this.ch = ch; this.x = x; this.y = y; this.b = b;
        }
    }

    static Map<String,String> fields(String line) {
        Map<String,String> map = new HashMap<>();
        if (line == null) return map;
        for (String entry : line.trim().split(" ")) {
            int eq = entry.indexOf('=');
            if (eq > 0 && eq < entry.length() - 1) map.put(entry.substring(0, eq), entry.substring(eq + 1));
        }
        return map;
    }

    private static Integer number(Map<String,String> map, String key) {
        String value = map.get(key);
        if (value == null) return null;
        try { return Integer.valueOf(value); } catch (NumberFormatException e) { return null; }
    }

    static Command parse(String line) {
        Map<String,String> map = fields(line);
        String a = map.get("a");
        if ("init".equals(a)) {
            Integer sw = number(map,"sw"), sh = number(map,"sh"), cw = number(map,"cw"), ch = number(map,"ch");
            if (sw != null && sh != null && cw != null && ch != null) return new Command(Kind.INIT, sw, sh, cw, ch, 0, 0, 0);
        } else if (a == null || "ts".equals(a)) {
            Integer x = number(map,"x"), y = number(map,"y"), b = number(map,"b");
            if (x != null && y != null && b != null) return new Command(Kind.TOUCH, 0, 0, 0, 0, x, y, b);
        }
        return new Command(Kind.UNKNOWN, 0, 0, 0, 0, 0, 0, 0);
    }

    /** Normalised position within the car's coordinate space; NaN when the size is unknown. */
    static double normal(int value, int size) { return size > 0 ? (double) value / size : Double.NaN; }

    static String action(int b) {
        switch (b) {
            case 0: return "DOWN";
            case 1: return "UP";
            case 2: return "MOVE";
            case 3: return "CANCEL";
            default: return "ACTION_" + b;
        }
    }
}

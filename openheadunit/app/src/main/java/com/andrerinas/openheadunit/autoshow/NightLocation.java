package com.andrerinas.openheadunit.autoshow;

/**
 * Where to work out sunrise and sunset when the phone has never given the app a position. The app's stored
 * default is a point in Israel, which puts the day/night change hours away from the real one anywhere else
 * (about six hours off in Taiwan). The phone's time zone is always known, and it says roughly how far east or
 * west the phone is, which is most of what sunrise and sunset depend on.
 */
public final class NightLocation {
    private NightLocation() {}

    /** Longitude (degrees) the time zone's standard offset points at: 15 degrees per hour, kept within -180..180. */
    public static double longitudeForOffsetMillis(long standardOffsetMillis) {
        double hours = standardOffsetMillis / 3600000.0;
        return Math.max(-180.0, Math.min(180.0, hours * 15.0));
    }

    /** A position worth storing: on the globe and not the (0, 0) a missing fix is often reported as. */
    public static boolean plausible(double latitude, double longitude) {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) return false;
        if (latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0) return false;
        return !(latitude == 0.0 && longitude == 0.0);
    }

    /** "3 min", "5 h", "12 days": how old a position is, for the log. */
    public static String age(long millis) {
        long minutes = Math.max(0, millis) / 60000;
        if (minutes < 120) return minutes + " min";
        long hours = minutes / 60;
        if (hours < 48) return hours + " h";
        return (hours / 24) + " days";
    }
}

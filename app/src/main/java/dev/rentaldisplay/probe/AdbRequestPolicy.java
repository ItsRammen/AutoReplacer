package dev.rentaldisplay.probe;

/**
 * Re-send policy for the optional Garmin useAdb request. The inspected Garmin app repeats the request about
 * every two seconds while the car has not reported ADB as available; this version stops after a small bound.
 */
final class AdbRequestPolicy {
    static final long INTERVAL_MS = 2000;
    static final int MAX_REQUESTS = 5;

    static boolean due(boolean selected, boolean videoStarted, boolean carReportsAdb, int sent, long now, long lastSent) {
        if (!selected || !videoStarted || carReportsAdb) return false;
        if (sent < 1 || sent >= MAX_REQUESTS) return false; // the first request belongs to the opening handshake
        return now - lastSent >= INTERVAL_MS;
    }

    /** Mirrors the inspected client: VIDEO_READY's seventh integer replaces the flag; an ADB status of 1 sets it. */
    static boolean afterVideoReady(int seventhInteger) { return seventhInteger == 1; }
    static boolean afterStatus(boolean current, int status) { return status == 1 || current; }
}

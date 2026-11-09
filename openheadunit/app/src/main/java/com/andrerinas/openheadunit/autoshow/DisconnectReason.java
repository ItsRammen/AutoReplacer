package com.andrerinas.openheadunit.autoshow;

/**
 * Turns the car link's technical failure text (errno names, protocol states) into a sentence for the
 * person using the app. The technical text is still written to the event log.
 */
public final class DisconnectReason {
    private DisconnectReason() {}

    public static String describe(String raw, boolean error) {
        String r = raw == null ? "" : raw;
        if (contains(r, "VIDEO_NOT_READY"))
            return "The car is not ready for video. Open Auto+ Show on the car, then plug in again.";
        if (contains(r, "No VIDEO_READY"))
            return "The car did not respond. Open Auto+ Show on the car, then plug in again.";
        if (contains(r, "No first video buffer"))
            return "The car never started showing video. Plug in again.";
        if (contains(r, "Video output made no progress"))
            return "Video stopped reaching the car. Plug in again.";
        if (contains(r, "Stationary grid stopped"))
            return "The car reported that it is moving, so AutoReplacer stopped. Use it while parked.";
        if (contains(r, "USB could not be opened"))
            return "The USB connection could not be opened. Close other apps that use it and plug in again.";
        if (contains(r, "write timed out") || contains(r, "Writer stalled"))
            return "The car stopped accepting video. Check the cable and plug in again.";
        if (contains(r, "Unsupported display") || contains(r, "Unknown video encoding") || contains(r, "Raw image"))
            return "The car asked for a picture format AutoReplacer does not support.";
        if (contains(r, "EIO") || contains(r, "ENODEV") || contains(r, "No such device") || contains(r, "read interrupted")
                || contains(r, "Session closed") || contains(r, "USB is not open") || contains(r, "read failed") || contains(r, "write failed"))
            return "The USB connection to the car was lost (the cable was unplugged, or the car restarted its USB).";
        return error ? "The car connection ended unexpectedly." : "The car closed the connection.";
    }

    private static boolean contains(String text, String part) { return text.toLowerCase(java.util.Locale.US).contains(part.toLowerCase(java.util.Locale.US)); }
}

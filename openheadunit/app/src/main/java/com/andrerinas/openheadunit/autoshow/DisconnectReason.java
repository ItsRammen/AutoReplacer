package com.andrerinas.openheadunit.autoshow;

/**
 * Sorts the car link's technical failure text (errno names, protocol states) into a reason the person using
 * the app can be told about. The words for each reason live in the app's string resources (so they can be
 * translated); the technical text is still written to the event log.
 */
public final class DisconnectReason {
    public enum Code {
        NOT_READY, NO_RESPONSE, NO_FIRST_BUFFER, NO_PROGRESS, MOVING, USB_OPEN, STALLED, UNSUPPORTED, USB_LOST,
        UNEXPECTED, CLOSED
    }

    private DisconnectReason() {}

    public static Code classify(String raw, boolean error) {
        String r = raw == null ? "" : raw;
        if (contains(r, "VIDEO_NOT_READY")) return Code.NOT_READY;
        if (contains(r, "No VIDEO_READY")) return Code.NO_RESPONSE;
        if (contains(r, "No first video buffer")) return Code.NO_FIRST_BUFFER;
        if (contains(r, "Video output made no progress")) return Code.NO_PROGRESS;
        if (contains(r, "Stationary grid stopped")) return Code.MOVING;
        if (contains(r, "USB could not be opened")) return Code.USB_OPEN;
        if (contains(r, "write timed out") || contains(r, "Writer stalled")) return Code.STALLED;
        if (contains(r, "Unsupported display") || contains(r, "Unknown video encoding") || contains(r, "Raw image")) return Code.UNSUPPORTED;
        if (contains(r, "EIO") || contains(r, "ENODEV") || contains(r, "No such device") || contains(r, "read interrupted")
                || contains(r, "Session closed") || contains(r, "USB is not open") || contains(r, "read failed") || contains(r, "write failed"))
            return Code.USB_LOST;
        return error ? Code.UNEXPECTED : Code.CLOSED;
    }

    private static boolean contains(String text, String part) { return text.toLowerCase(java.util.Locale.US).contains(part.toLowerCase(java.util.Locale.US)); }
}

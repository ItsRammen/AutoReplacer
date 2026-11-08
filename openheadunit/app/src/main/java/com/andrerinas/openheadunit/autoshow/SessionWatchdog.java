package com.andrerinas.openheadunit.autoshow;

/** Incoming messages are event-driven; their silence is not a stream heartbeat. */
public final class SessionWatchdog {
    public static String failure(long now, long openedAt, boolean videoReady, long readyAt,
            long videoBuffers, long lastVideoWrite) {
        if (!videoReady && now - openedAt > 15000)
            return "No VIDEO_READY within 15 seconds; select Auto+ Show and reconnect";
        if (videoReady && videoBuffers == 0 && now - readyAt > 15000)
            return "No first video buffer within 15 seconds of VIDEO_READY";
        if (videoBuffers > 0 && now - lastVideoWrite > 15000)
            return "Video output made no progress for 15 seconds";
        return null;
    }
}

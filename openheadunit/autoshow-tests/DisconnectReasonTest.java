package com.andrerinas.openheadunit.autoshow;

public final class DisconnectReasonTest {
    private static int assertions;
    private static void check(boolean value, String label) { assertions++; if (!value) throw new AssertionError(label); }
    private static boolean mentions(String raw, String part) { return DisconnectReason.describe(raw, true).contains(part); }
    public static void main(String[] args) {
        check(mentions("IOException: read failed: EIO (I/O error)", "USB connection to the car was lost"), "EIO");
        check(mentions("write failed: ENODEV (No such device)", "USB connection to the car was lost"), "ENODEV");
        check(mentions("read interrupted by close() on another thread", "USB connection to the car was lost"), "closed under a read");
        check(mentions("No first video buffer within 60 seconds of VIDEO_READY", "never started showing video"), "first buffer");
        check(mentions("No VIDEO_READY within 15 seconds; select Auto+ Show and reconnect", "did not respond"), "no video ready");
        check(mentions("Car reported VIDEO_NOT_READY; reopen Auto+ Show on the car and reconnect", "not ready for video"), "video not ready");
        check(mentions("USB write timed out at video 4000 bytes", "stopped accepting video"), "write timeout");
        check(mentions("Stationary grid stopped: car reported motion or unrecognised state", "while parked"), "motion");
        check(mentions("Unsupported display: 99x99 dpi=1", "format AutoReplacer does not support"), "display");
        check("The car closed the connection.".equals(DisconnectReason.describe("", false)), "clean close");
        check("The car connection ended unexpectedly.".equals(DisconnectReason.describe("something odd", true)), "unknown error");
        check("The car closed the connection.".equals(DisconnectReason.describe(null, false)), "null reason");
        for (String raw : new String[] {"read failed: EIO", "write failed: ENODEV", "No first video buffer within 60 seconds of VIDEO_READY"})
            check(!DisconnectReason.describe(raw, true).contains("EIO") && !DisconnectReason.describe(raw, true).contains("VIDEO_READY"), "no jargon leaks: " + raw);
        System.out.println("DisconnectReasonTest OK (" + assertions + " assertions)");
    }
}

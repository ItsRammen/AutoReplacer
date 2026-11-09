package com.andrerinas.openheadunit.autoshow;

public final class DisconnectReasonTest {
    private static int assertions;
    private static void check(boolean value, String label) { assertions++; if (!value) throw new AssertionError(label); }
    private static boolean is(String raw, DisconnectReason.Code code) { return DisconnectReason.classify(raw, true) == code; }
    public static void main(String[] args) {
        check(is("IOException: read failed: EIO (I/O error)", DisconnectReason.Code.USB_LOST), "EIO");
        check(is("write failed: ENODEV (No such device)", DisconnectReason.Code.USB_LOST), "ENODEV");
        check(is("read interrupted by close() on another thread", DisconnectReason.Code.USB_LOST), "closed under a read");
        check(is("No first video buffer within 60 seconds of VIDEO_READY", DisconnectReason.Code.NO_FIRST_BUFFER), "first buffer");
        check(is("No VIDEO_READY within 15 seconds; select Auto+ Show and reconnect", DisconnectReason.Code.NO_RESPONSE), "no video ready");
        check(is("Car reported VIDEO_NOT_READY; reopen Auto+ Show on the car and reconnect", DisconnectReason.Code.NOT_READY), "video not ready");
        check(is("Video output made no progress", DisconnectReason.Code.NO_PROGRESS), "no progress");
        check(is("USB could not be opened", DisconnectReason.Code.USB_OPEN), "usb open");
        check(is("USB write timed out at video 4000 bytes", DisconnectReason.Code.STALLED), "write timeout");
        check(is("Stationary grid stopped: car reported motion or unrecognised state", DisconnectReason.Code.MOVING), "motion");
        check(is("Unsupported display: 99x99 dpi=1", DisconnectReason.Code.UNSUPPORTED), "display");
        check(DisconnectReason.classify("", false) == DisconnectReason.Code.CLOSED, "clean close");
        check(DisconnectReason.classify("something odd", true) == DisconnectReason.Code.UNEXPECTED, "unknown error");
        check(DisconnectReason.classify(null, false) == DisconnectReason.Code.CLOSED, "null reason");
        System.out.println("DisconnectReasonTest OK (" + assertions + " assertions)");
    }
}

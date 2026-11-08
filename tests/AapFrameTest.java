package dev.rentaldisplay.probe;

public final class AapFrameTest {
    private static int assertions;
    private static void check(boolean value, String label) {
        assertions++; if (!value) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        byte[] req = AapFrame.versionRequest(1, 1);
        byte[] expected = {0x00, 0x03, 0x00, 0x06, 0x00, 0x01, 0x00, 0x01, 0x00, 0x01};
        check(java.util.Arrays.equals(req, expected), "version request frame: control channel, single plain fragment, id 1, v1.1");
        byte[] ok = {0x00, 0x03, 0x00, 0x08, 0x00, 0x02, 0x00, 0x01, 0x00, 0x07, 0x00, 0x00};
        AapFrame.VersionResponse r = AapFrame.parseVersionResponse(ok, ok.length);
        check(r != null && r.ok() && r.major == 1 && r.minor == 7 && r.status == 0, "version response parsed");
        byte[] mismatch = {0x00, 0x03, 0x00, 0x08, 0x00, 0x02, 0x00, 0x01, 0x00, 0x01, (byte) 0xff, (byte) 0xff};
        AapFrame.VersionResponse m = AapFrame.parseVersionResponse(mismatch, mismatch.length);
        check(m != null && !m.ok() && m.status == 0xffff, "mismatch status kept");
        check(AapFrame.parseVersionResponse(ok, 11) == null, "short read rejected");
        byte[] other = ok.clone(); other[5] = 0x03;
        check(AapFrame.parseVersionResponse(other, other.length) == null, "other message id rejected");
        byte[] fragment = ok.clone(); fragment[1] = 0x01;
        check(AapFrame.parseVersionResponse(fragment, fragment.length) == null, "multi-fragment frame rejected");
        check(AapFrame.parseVersionResponse(null, 0) == null, "null rejected");
        check(AapFrame.hex(req, req.length).equals("00 03 00 06 00 01 00 01 00 01"), "hex dump");
        check(AapFrame.HEAD_UNIT_SERVER_PORT == 5277, "head unit server port");
        System.out.println("AapFrameTest: "+assertions+" assertions passed");
    }
}

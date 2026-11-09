package dev.rentaldisplay.probe;

/**
 * Minimal Android Auto Protocol (AAP) framing for the first, unencrypted step only: the version
 * exchange on the control channel. Frame: channel(1) flags(1) length(2, big-endian) payload;
 * payload = message id (2) + data. Flags 0x03 = first|last fragment, plain text.
 */
final class AapFrame {
    static final int CONTROL_CHANNEL = 0;
    static final int FLAGS_SINGLE_PLAIN = 0x03;
    static final int MSG_VERSION_REQUEST = 0x0001;
    static final int MSG_VERSION_RESPONSE = 0x0002;
    static final int HEAD_UNIT_SERVER_PORT = 5277;

    static byte[] versionRequest(int major, int minor) {
        byte[] payload = { 0x00, (byte) MSG_VERSION_REQUEST,
                (byte) (major >> 8), (byte) major, (byte) (minor >> 8), (byte) minor };
        byte[] frame = new byte[4 + payload.length];
        frame[0] = (byte) CONTROL_CHANNEL; frame[1] = (byte) FLAGS_SINGLE_PLAIN;
        frame[2] = (byte) (payload.length >> 8); frame[3] = (byte) payload.length;
        System.arraycopy(payload, 0, frame, 4, payload.length);
        return frame;
    }

    static final class VersionResponse {
        final int channel, flags, length, messageId, major, minor, status;
        VersionResponse(int channel, int flags, int length, int messageId, int major, int minor, int status) {
            this.channel = channel; this.flags = flags; this.length = length; this.messageId = messageId;
            this.major = major; this.minor = minor; this.status = status;
        }
        boolean ok() { return messageId == MSG_VERSION_RESPONSE && status == 0; }
    }

    private static int u16(byte[] b, int i) { return ((b[i] & 0xff) << 8) | (b[i + 1] & 0xff); }

    /** Parses a single-fragment version response (id, major, minor, status); null if the bytes are not one. */
    static VersionResponse parseVersionResponse(byte[] b, int len) {
        if (b == null || len < 12) return null;
        int channel = b[0] & 0xff, flags = b[1] & 0xff, length = u16(b, 2);
        if ((flags & 0x03) != 0x03 || length < 8 || len < 4 + length) return null;
        int id = u16(b, 4);
        if (id != MSG_VERSION_RESPONSE) return null;
        return new VersionResponse(channel, flags, length, id, u16(b, 6), u16(b, 8), u16(b, 10));
    }

    static String hex(byte[] b, int len) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < Math.min(len, 64); i++) { if (i > 0) s.append(' '); s.append(String.format("%02x", b[i] & 0xff)); }
        if (len > 64) s.append(" …");
        return s.toString();
    }
}

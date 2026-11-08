package dev.rentaldisplay.probe;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Original implementation of the observed USB Link framing. No Android dependencies. */
public final class Wire {
    public static final int MAX_PAYLOAD = 4 * 1024 * 1024 - 8;
    public static final int VIDEO_READY_BYTES = 61;
    /** Observed Garmin terminate frame carries the zero-filled parameter buffer. */
    public static byte[] terminationPayload() { return new byte[VIDEO_READY_BYTES]; }
    public static final class Message {
        public final int service, type;
        public final byte[] payload;
        Message(int service, int type, byte[] payload) {
            this.service = service; this.type = type; this.payload = payload;
        }
    }

    public static byte[] header(int service, int type, int length) {
        if (service < 0 || service > 65535 || type < 0 || type > 65535
                || length < 0 || length > MAX_PAYLOAD) throw new IllegalArgumentException("Invalid frame");
        return ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
                .putShort((short) service).putShort((short) type).putInt(length).array();
    }

    public static byte[] ints(int... values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * 4).order(ByteOrder.BIG_ENDIAN);
        for (int value : values) buffer.putInt(value);
        return buffer.array();
    }

    public static void write(OutputStream output, int service, int type, byte[] data) throws IOException {
        byte[] h = header(service, type, data.length);
        int first = Math.min(data.length, 16384 - h.length);
        byte[] chunk = new byte[h.length + first];
        System.arraycopy(h, 0, chunk, 0, h.length);
        System.arraycopy(data, 0, chunk, h.length, first);
        output.write(chunk);
        for (int off = first; off < data.length; ) {
            int count = Math.min(16384, data.length - off);
            output.write(data, off, count); off += count;
        }
    }

    public static byte[] bluetoothName(String name) throws IOException {
        if (name == null || name.trim().isEmpty() || name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0
                || name.indexOf('\0') >= 0 || name.getBytes(StandardCharsets.UTF_8).length > 248)
            throw new IOException("Phone Bluetooth name is missing or unsupported");
        return (name + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    public static String bluetoothPeerState(byte[] payload, String phoneName) throws IOException {
        if (payload.length == 0 || payload.length > 512) throw new IOException("Invalid Bluetooth status size");
        String name = new String(payload, StandardCharsets.UTF_8).trim();
        int end = name.indexOf("\r\n");
        if (end >= 0) name = name.substring(0, end);
        if (name.equals(phoneName)) return "this phone";
        if (name.equals("none")) return "no device";
        return "another device or unrecognised name";
    }

    /** Keep native accessory reads at 16 KiB; tiny header reads can truncate USB transfers. */
    public static InputStream usbInput(InputStream device) {
        return new InputStream() {
            private final byte[] buffer = new byte[16384];
            private int offset, limit;
            @Override public int read(byte[] target, int start, int length) throws IOException {
                if (start < 0 || length < 0 || length > target.length - start) throw new IndexOutOfBoundsException();
                if (length == 0) return 0;
                while (offset == limit) {
                    int n = device.read(buffer, 0, buffer.length);
                    if (n < 0) return -1;
                    offset = 0; limit = n;
                }
                int count = Math.min(length, limit - offset);
                System.arraycopy(buffer, offset, target, start, count);
                offset += count; return count;
            }
            @Override public int read() throws IOException {
                byte[] one = new byte[1];
                return read(one, 0, 1) < 0 ? -1 : one[0] & 255;
            }
            @Override public void close() throws IOException { device.close(); }
        };
    }

    public static Message read(InputStream input) throws IOException {
        byte[] header = new byte[8];
        readFully(input, header);
        ByteBuffer b = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
        int service = b.getShort() & 65535, type = b.getShort() & 65535, length = b.getInt();
        if (length < 0 || length > MAX_PAYLOAD) throw new IOException("Invalid payload length: " + length);
        byte[] payload = new byte[length];
        readFully(input, payload);
        return new Message(service, type, payload);
    }

    private static void readFully(InputStream input, byte[] target) throws IOException {
        int offset = 0;
        while (offset < target.length) {
            int count = input.read(target, offset, target.length - offset);
            if (count < 0) throw new EOFException("USB ended after " + offset + "/" + target.length + " bytes");
            if (count > 0) offset += count;
        }
    }

    public static int[] videoParameters(byte[] payload) throws IOException {
        if (payload.length < VIDEO_READY_BYTES) throw new IOException("Short VIDEO_READY: " + payload.length);
        ByteBuffer b = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
        int[] values = new int[7];
        for (int i = 0; i < values.length; i++) values[i] = b.getInt();
        int width = values[0], height = values[1], dpi = values[2];
        if (width < 64 || height < 64 || width > 1920 || height > 1080
                || (width & 1) != 0 || (height & 1) != 0 || dpi < 60 || dpi > 640) {
            throw new IOException("Unsupported display: " + width + "x" + height + " dpi=" + dpi);
        }
        // The reference client treats zero as raw RGB and nonzero as AVC. Only the
        // expected AVC enum (1) is enabled here until real-car logs establish others.
        if (values[3] != 0 && values[3] != 1) throw new IOException("Unknown video encoding: " + values[3]);
        if (values[3] == 0 && (long) width * height * 3 + 8 > MAX_PAYLOAD)
            throw new IOException("Raw image exceeds protocol frame limit");
        return values;
    }
}

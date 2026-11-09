package com.andrerinas.openheadunit.autoshow;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;

/** Executable protocol tests; do not require an Android runtime. */
public final class WireTest {
    private static int assertions;
    private static void check(boolean result, String label) {
        assertions++; if (!result) throw new AssertionError(label);
    }
    private static byte[] packet(int service, int type, byte[] data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(Wire.header(service, type, data.length)); out.write(data); return out.toByteArray();
    }
    private interface Throwing { void run() throws Exception; }
    private static void rejects(Throwing fn, String label) throws Exception {
        try { fn.run(); } catch (IOException | IllegalArgumentException expected) { assertions++; return; }
        throw new AssertionError(label);
    }
    private static byte[] ready(int width, int height, int dpi, int encoding) {
        byte[] data = new byte[61];
        System.arraycopy(Wire.ints(width, height, dpi, encoding, 1, 2, 0), 0, data, 0, 28); return data;
    }
    public static void main(String[] args) throws Exception {
        byte[] payload = {11, 22, 33, 44, 55};
        byte[] framed = packet(2, 1, payload);
        check(Arrays.equals(Arrays.copyOf(framed, 8), new byte[]{0,2,0,1,0,0,0,5}), "big-endian header");
        InputStream fragments = new ByteArrayInputStream(framed) {
            @Override public synchronized int read(byte[] b, int off, int len) { return super.read(b, off, Math.min(1, len)); }
        };
        Wire.Message m = Wire.read(fragments);
        check(m.service == 2 && m.type == 1 && Arrays.equals(m.payload, payload), "fragmented reads");
        ByteArrayOutputStream combined = new ByteArrayOutputStream();
        combined.write(packet(2, 6, new byte[0])); combined.write(packet(65535, 65535, payload));
        InputStream input = new ByteArrayInputStream(combined.toByteArray());
        check(Wire.read(input).payload.length == 0, "empty keepalive");
        m = Wire.read(input);
        check(m.service == 65535 && m.type == 65535 && m.payload.length == 5, "coalesced reads and unsigned fields");
        InputStream usb = Wire.usbInput(new ByteArrayInputStream(combined.toByteArray()) {
            @Override public synchronized int read(byte[] b, int off, int len) {
                check(len == 16384, "native USB reads retain a full transfer-sized buffer");
                return super.read(b, off, len);
            }
        });
        check(Wire.read(usb).type == 6, "buffered USB first message");
        check(Arrays.equals(Wire.read(usb).payload, payload), "buffered USB retains remainder of transfer");
        rejects(() -> Wire.read(new ByteArrayInputStream(new byte[]{0,2})), "truncated header");
        rejects(() -> Wire.read(new ByteArrayInputStream(Arrays.copyOf(framed, framed.length - 1))), "truncated payload");
        rejects(() -> Wire.read(new ByteArrayInputStream(new byte[]{0,2,0,1,-1,-1,-1,-1})), "negative size");
        rejects(() -> Wire.read(new ByteArrayInputStream(new byte[]{0,2,0,1,0x7f,-1,-1,-1})), "oversized size before allocation");
        rejects(() -> Wire.header(65536, 1, 0), "service range");
        rejects(() -> Wire.header(1, -1, 0), "type range");
        rejects(() -> Wire.header(1, 1, Wire.MAX_PAYLOAD + 1), "outbound limit");
        check(Wire.videoParameters(ready(800,480,160,1))[0] == 800, "AVC negotiation");
        check(Wire.videoParameters(ready(800,480,160,0))[3] == 0, "raw negotiation");
        rejects(() -> Wire.videoParameters(new byte[28]), "truncated VIDEO_READY");
        rejects(() -> Wire.videoParameters(ready(0,480,160,1)), "zero width");
        rejects(() -> Wire.videoParameters(ready(801,480,160,1)), "odd YUV width");
        rejects(() -> Wire.videoParameters(ready(800,480,0,1)), "invalid density");
        rejects(() -> Wire.videoParameters(ready(800,480,160,2)), "unknown encoding");
        rejects(() -> Wire.videoParameters(ready(1920,1080,160,0)), "oversized raw frame");
        rejects(() -> Wire.videoParameters(ready(4096,4096,160,1)), "oversized display");
        ByteArrayOutputStream usbWrites = new ByteArrayOutputStream() {
            @Override public synchronized void write(byte[] b, int off, int len) {
                check(len <= 16384, "USB writes respect 16 KiB transfer size");
                super.write(b, off, len);
            }
        };
        byte[] video = new byte[40000];
        for (int i = 0; i < video.length; i++) video[i] = (byte) i;
        Wire.write(usbWrites, 1, 2, video);
        Wire.write(usbWrites, 1, 5, new byte[0]);
        InputStream replay = new ByteArrayInputStream(usbWrites.toByteArray());
        check(Arrays.equals(Wire.read(replay).payload, video), "large video survives chunked transport");
        check(Wire.read(replay).type == 5 && replay.read() == -1, "following control frame remains aligned");
        check(Arrays.equals(Wire.bluetoothName("Test phone"), "Test phone\r\n".getBytes(StandardCharsets.UTF_8)),
                "Bluetooth setup uses UTF-8 name and CRLF terminator");
        check(Wire.bluetoothPeerState("Test phone\r\n".getBytes(StandardCharsets.UTF_8), "Test phone").equals("this phone"),
                "paired phone status recognised without logging its name");
        check(Wire.bluetoothPeerState("none\r\n".getBytes(StandardCharsets.UTF_8), "Test phone").equals("no device"),
                "empty car Bluetooth profile recognised");
        check(Wire.bluetoothPeerState("Other phone\r\n".getBytes(StandardCharsets.UTF_8), "Test phone").startsWith("another device"),
                "other phone identity redacted");
        rejects(() -> Wire.bluetoothName("Injected\r\nname"), "Bluetooth line injection");
        rejects(() -> Wire.bluetoothName(null), "missing Bluetooth name");
        rejects(() -> Wire.bluetoothPeerState(new byte[513], "Test phone"), "unbounded Bluetooth status");
        ByteArrayOutputStream adbRequest = new ByteArrayOutputStream();
        Wire.write(adbRequest,1,8,Wire.ints(1));
        check(Arrays.equals(adbRequest.toByteArray(),new byte[]{0,1,0,8,0,0,0,4,0,0,0,1}),
                "observed Garmin ADB setup request framing");
        ByteArrayOutputStream terminate = new ByteArrayOutputStream();
        Wire.write(terminate,1,4,Wire.terminationPayload());
        byte[] terminatingFrame = terminate.toByteArray();
        check(Arrays.equals(Arrays.copyOf(terminatingFrame,8),new byte[]{0,1,0,4,0,0,0,61}),
                "shutdown matches observed Garmin service/type and 61-byte parameter-buffer length");
        check(Arrays.equals(Arrays.copyOfRange(terminatingFrame,8,terminatingFrame.length),new byte[61]),
                "termination parameter buffer is zero-filled");
        Wire.write(terminate,1,5,new byte[0]);
        InputStream shutdownReplay = new ByteArrayInputStream(terminate.toByteArray());
        check(Wire.read(shutdownReplay).payload.length==61 && Wire.read(shutdownReplay).type==5
                && shutdownReplay.read()==-1,"shutdown does not leave bytes misaligned for next control message");
        System.out.println("WireTest: " + assertions + " assertions passed");
    }
}

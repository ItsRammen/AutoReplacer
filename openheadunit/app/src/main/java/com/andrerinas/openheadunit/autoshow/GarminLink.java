package com.andrerinas.openheadunit.autoshow;

import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.SystemClock;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Garmin USB Link session with an Auto+ Show head unit: opens the accessory, sends the session
 * request, receives VIDEO_READY, replies to the car's safety state and carries video buffers.
 * Ported from the AutoReplacer probe's UsbSession; the video source is supplied by the caller.
 */
public final class GarminLink {
    public interface Listener {
        void log(String text);
        /** The car's Bluetooth address from VIDEO_READY, or null if it is missing or malformed. Called once, just before videoReady. */
        void carBluetooth(String addressOrNull);
        /** Called on the reader thread once, when the car announces its video size. */
        void videoReady(int width, int height, int encoding);
        /**
         * The car's safety state: {@code restricted} is its driving-restriction flag, {@code unlocked} whether this
         * session has answered it with "allowed" (only done when the car reports no restriction and no motion).
         */
        void safetyState(int restricted, int speed, boolean unlocked);
        /** Called once when the session is over. */
        void ended(String reason, boolean error);
    }

    private final UsbManager manager;
    private final UsbAccessory accessory;
    private final Listener listener;
    private final int phoneWidth, phoneHeight, phoneDpi;
    private final Object writeLock = new Object(), resourceLock = new Object();
    private final AtomicBoolean closing = new AtomicBoolean(), released = new AtomicBoolean();
    private final AtomicBoolean keepalivePending = new AtomicBoolean();
    private final AtomicLong frames = new AtomicLong();
    private final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor();
    private final SerialWriter writer;
    private final Semaphore videoSlots = new Semaphore(3);
    private volatile AccessoryIo io;
    private volatile boolean videoReady, initialRequestSent, maskSent;

    // The last messages in both directions, so a drop can be read against what was happening just before it.
    private final java.util.ArrayDeque<String> traffic = new java.util.ArrayDeque<>();
    private long videoCountSec, videoBytesSec, videoMaxSec, lastReceivedAt, lastSentAt;

    private void note(String text) {
        long at = SystemClock.elapsedRealtime() - openedAt;
        synchronized (traffic) {
            traffic.addLast("+" + at + "ms " + text);
            while (traffic.size() > 48) traffic.removeFirst();
        }
    }

    /** Called once a second: folds the video buffers of that second into one entry. */
    private void noteVideoSecond() {
        long n, bytes, max;
        synchronized (traffic) { n = videoCountSec; bytes = videoBytesSec; max = videoMaxSec; videoCountSec = videoBytesSec = videoMaxSec = 0; }
        if (n > 0) note("tx video " + n + " buffers " + bytes + " bytes, largest " + max);
    }

    private void dumpTraffic(Exception error) {
        long now = SystemClock.elapsedRealtime();
        listener.log("GARMIN_DROP_CONTEXT: " + error.getClass().getSimpleName() + " after " + (openedAt == 0 ? 0 : now - openedAt) + " ms open; "
                + "last receive " + (lastReceivedAt == 0 ? "never" : (now - lastReceivedAt) + " ms ago") + ", last send "
                + (lastSentAt == 0 ? "never" : (now - lastSentAt) + " ms ago") + ", write in progress: " + activeWrite
                + " (" + writer.activeMillis() + " ms), video buffers sent " + frames.get());
        java.util.List<String> lines;
        synchronized (traffic) { lines = new java.util.ArrayList<>(traffic); }
        StringBuilder row = new StringBuilder();
        int count = 0;
        for (String line : lines) {
            if (row.length() > 0) row.append(" | ");
            row.append(line);
            if (++count % 6 == 0) { listener.log("GARMIN_LAST_TRAFFIC: " + row); row.setLength(0); }
        }
        if (row.length() > 0) listener.log("GARMIN_LAST_TRAFFIC: " + row);
    }
    private volatile long lastWrite, openedAt, readyAt, lastVideoWrite;
    private volatile String activeWrite = "none";

    public GarminLink(UsbManager manager, UsbAccessory accessory, Listener listener,
            int phoneWidth, int phoneHeight, int phoneDpi) {
        this.manager = manager; this.accessory = accessory; this.listener = listener;
        this.phoneWidth = phoneWidth; this.phoneHeight = phoneHeight; this.phoneDpi = phoneDpi;
        writer = new SerialWriter(8, this::fail);
    }

    public void start() { new Thread(this::run, "garmin-link-read").start(); }
    public long frames() { return frames.get(); }
    public boolean isClosing() { return closing.get(); }
    public boolean isVideoReady() { return videoReady; }

    private void run() {
        try {
            // A link that was just replaced may still hold the accessory's input stream for a moment.
            AccessoryIo opened = null;
            IllegalStateException busy = null;
            for (int attempt = 0; attempt < 6 && opened == null; attempt++) {
                try {
                    opened = AccessoryIo.open(manager, accessory);
                } catch (IllegalStateException e) {
                    String text = e.getMessage();
                    if (text == null || !text.contains("already open") || closing.get()) throw e;
                    busy = e;
                    listener.log("GARMIN_USB_BUSY: the previous connection still holds the accessory (attempt " + (attempt + 1) + ")");
                    Thread.sleep(400);
                }
            }
            if (opened == null) throw busy;
            synchronized (resourceLock) {
                if (closing.get()) { opened.close(); return; }
                io = opened;
            }
            openedAt = SystemClock.elapsedRealtime();
            listener.log("GARMIN_USB_OPEN: " + opened.mode);
            clock.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
            queue("opening handshake", () -> {
                writeRaw("USBL".getBytes(StandardCharsets.US_ASCII));
                send(1, 1, Wire.ints(phoneWidth, phoneHeight, phoneDpi, 0, 1, 2, 0));
                initialRequestSent = true;
                listener.log("GARMIN_SESSION_REQUEST: advertised " + phoneWidth + "x" + phoneHeight + " dpi=" + phoneDpi);
            });
            while (!closing.get()) {
                Wire.Message message = Wire.read(opened.input);
                if (closing.get()) break;
                received(message);
            }
        } catch (Exception e) { if (!closing.get()) fail(e); }
        finally { release(); }
    }

    private void tick() {
        if (closing.get()) return;
        noteVideoSecond();
        long now = SystemClock.elapsedRealtime();
        if (lastWrite != 0 && now - lastWrite > 5000) { fail(new IOException("USB write timed out at " + activeWrite)); return; }
        if (writer.activeMillis() > 6000) { fail(new IOException("Writer stalled at " + writer.active())); return; }
        String failure = SessionWatchdog.failure(now, openedAt, videoReady, readyAt, frames.get(), lastVideoWrite);
        if (failure != null) { fail(new IOException(failure)); return; }
        if (initialRequestSent && frames.get() > 0 && keepalivePending.compareAndSet(false, true)) {
            if (!queue("keepalive", () -> {
                try { send(1, 5, new byte[0]); } finally { keepalivePending.set(false); }
            })) keepalivePending.set(false);
        }
    }

    private boolean queue(String label, SerialWriter.Task action) {
        if (closing.get()) return false;
        return writer.submit(label, () -> { if (!closing.get()) action.run(); });
    }

    private void received(Wire.Message message) throws IOException {
        lastReceivedAt = SystemClock.elapsedRealtime();
        note("rx " + message.service + "/" + message.type + " " + message.payload.length + "B");
        if (message.service != 2) { listener.log("GARMIN_RX_UNHANDLED service=" + message.service + " type=" + message.type); return; }
        switch (message.type) {
            case 1: {
                int[] p = Wire.videoParameters(message.payload);
                listener.log("GARMIN_VIDEO_READY: " + p[0] + "x" + p[1] + " dpi=" + p[2] + " encoding=" + p[3]
                        + " version=" + p[4] + "/" + p[5] + " adbStatus=" + p[6]);
                if (videoReady) return;
                readyAt = SystemClock.elapsedRealtime(); videoReady = true;
                listener.carBluetooth(Wire.carBluetoothAddress(message.payload));
                listener.videoReady(p[0], p[1], p[3]);
                break;
            }
            case 2: throw new IOException("Car reported VIDEO_NOT_READY; reopen Auto+ Show on the car and reconnect");
            case 6: break; // keepalive answer
            case 8:
                if (message.payload.length >= 8) {
                    ByteBuffer probe = ByteBuffer.wrap(message.payload);
                    listener.log("GARMIN_HID_TEST x=" + probe.getInt() + " y=" + probe.getInt() + " (ignored)");
                }
                break;
            case 9: {
                if (message.payload.length < 8) throw new IOException("Short safety-state message");
                ByteBuffer state = ByteBuffer.wrap(message.payload);
                int restricted = state.getInt(), speed = state.getInt();
                listener.log("GARMIN_SAFETY_STATE restricted=" + restricted + " speed=" + speed);
                DiagnosticPolicy.requireStationary(restricted, speed);
                if (DiagnosticPolicy.safetyMaskReplyAllowed(restricted, speed)) {
                    queue("safety mask reply", () -> {
                        send(1, 17, Wire.ints(0, 1));
                        maskSent = true;
                        listener.log("GARMIN_SAFETY_MASK_SENT mask=0 allowed=1");
                    });
                } else {
                    listener.log("GARMIN_SAFETY_NOT_ANSWERED: the car reports a restriction; it is not overridden");
                }
                listener.safetyState(restricted, speed, maskSent || DiagnosticPolicy.safetyMaskReplyAllowed(restricted, speed));
                break;
            }
            case 11:
                if (message.payload.length >= 4)
                    listener.log("GARMIN_ADB_STATUS=" + ByteBuffer.wrap(message.payload).getInt());
                break;
            default: listener.log("GARMIN_RX_UNKNOWN type=" + message.type);
        }
    }

    /** Queues one encoded video buffer for the car. Blocks briefly when the car is slow; false if the link is closed. */
    public boolean sendVideo(byte[] data) {
        // The car announces VIDEO_READY before it wants any picture; buffers sent earlier make it reset the USB link.
        if (!videoReady) return !closing.get();
        try {
            while (!closing.get()) {
                if (videoSlots.tryAcquire(200, TimeUnit.MILLISECONDS)) {
                    boolean queued = queue("video " + data.length + " bytes", () -> {
                        try {
                            send(1, 2, data);
                            if (closing.get()) return;
                            lastVideoWrite = SystemClock.elapsedRealtime();
                            long count = frames.incrementAndGet();
                            if (count == 1) {
                                listener.log("GARMIN_FIRST_VIDEO_BUFFER_SENT");
                                send(1, 3, Wire.ints(phoneWidth, phoneHeight, 2));
                                listener.log("GARMIN_ORIENTATION_REPORT " + phoneWidth + "x" + phoneHeight + " orientation=2");
                            }
                            if (count % 300 == 0) listener.log("GARMIN_VIDEO_BUFFERS=" + count);
                        } finally { videoSlots.release(); }
                    });
                    if (!queued) videoSlots.release();
                    return queued;
                }
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return false;
    }

    private void send(int service, int type, byte[] data) throws IOException {
        synchronized (writeLock) {
            if (released.get()) throw new IOException("Session closed");
            activeWrite = "service=" + service + " type=" + type + " payload=" + data.length;
            lastWrite = SystemClock.elapsedRealtime();
            try {
                AccessoryIo target = io;
                if (target == null) throw new IOException("USB is not open");
                Wire.write(target.output, service, type, data);
                lastSentAt = SystemClock.elapsedRealtime();
                if (service == 1 && type == 2) {
                    synchronized (traffic) { videoCountSec++; videoBytesSec += data.length; videoMaxSec = Math.max(videoMaxSec, data.length); }
                } else note("tx " + service + "/" + type + " " + data.length + "B");
            } finally { lastWrite = 0; activeWrite = "none"; }
        }
    }

    private void writeRaw(byte[] data) throws IOException {
        synchronized (writeLock) {
            if (released.get()) throw new IOException("Session closed");
            lastWrite = SystemClock.elapsedRealtime();
            try { io.output.write(data); } finally { lastWrite = 0; }
        }
    }

    /** Closes the streams now, without the termination message, so a new link can open the accessory straight away. */
    public void abort(String reason) {
        closing.set(true);
        listener.log("GARMIN_ABORT: " + reason);
        release();
    }

    public void stop(String reason) {
        if (!closing.compareAndSet(false, true)) return;
        listener.log("GARMIN_STOP: " + reason);
        if (lastWrite == 0) writer.submit("termination", () -> {
            try {
                if (io != null) { send(1, 4, Wire.terminationPayload()); listener.log("GARMIN_SESSION_TERMINATE_SENT"); }
            } finally { release(); }
        });
        new Thread(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            release();
        }, "garmin-link-close").start();
    }

    private void fail(Exception error) {
        if (!closing.compareAndSet(false, true)) return;
        listener.log("GARMIN_ERROR: " + error.getClass().getSimpleName() + ": " + error.getMessage());
        release(error);
    }

    private void release() { release(null); }

    private void release(Exception error) {
        closing.set(true);
        if (!released.compareAndSet(false, true)) return;
        if (error != null) { try { dumpTraffic(error); } catch (Exception ignored) {} }
        clock.shutdownNow(); writer.close();
        synchronized (resourceLock) { if (io != null) io.close(); }
        listener.log("GARMIN_CLOSED: video buffers=" + frames.get());
        listener.ended(error == null ? "closed" : error.getMessage(), error != null);
    }
}

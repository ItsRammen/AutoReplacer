package dev.rentaldisplay.probe;

import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.SystemClock;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

final class UsbSession {
    interface Listener {
        void state(String text); void changed(); void ended(UsbSession session); void reconnectRequired();
        void inputDiagnostic(String reason);
    }
    private final UsbManager manager;
    private final UsbAccessory accessory;
    private final ProbeLog log;
    private final Listener listener;
    private final GridState grid;
    private final int phoneWidth, phoneHeight, phoneDpi;
    private final boolean requestAdbSetup;
    private boolean adbDiagnosticReported;
    private final Object writeLock = new Object(), resourceLock = new Object();
    private final AtomicBoolean closing = new AtomicBoolean(), released = new AtomicBoolean();
    private final AtomicBoolean keepalivePending = new AtomicBoolean();
    private final AtomicLong frames = new AtomicLong(), bytes = new AtomicLong(), messages = new AtomicLong();
    private final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor();
    private final SerialWriter writer;
    private final Semaphore videoSlots = new Semaphore(3);
    private final CountDownLatch readerDone = new CountDownLatch(1);
    private volatile boolean resourcesClosed;
    private volatile AccessoryIo io;
    private volatile TestVideo video;
    private volatile Thread videoThread;
    private volatile long lastReply, lastWrite, openedAt, readyAt, lastVideoWrite;
    private long lastQuietNote;
    private volatile String activeWrite = "none";
    private volatile boolean videoReady, initialRequestSent;
    private long writes;

    UsbSession(UsbManager manager, UsbAccessory accessory, ProbeLog log, Listener listener,
            GridState grid, int phoneWidth, int phoneHeight, int phoneDpi, boolean requestAdbSetup) {
        this.manager = manager; this.accessory = accessory; this.log = log; this.listener = listener;
        this.grid = grid; this.phoneWidth = phoneWidth; this.phoneHeight = phoneHeight; this.phoneDpi = phoneDpi;
        this.requestAdbSetup = requestAdbSetup;
        writer = new SerialWriter(8, this::fail);
    }
    void start() { new Thread(this::run, "usb-link-read").start(); }
    long frames() { return frames.get(); }
    boolean resourcesReleased() {
        Thread encoder = videoThread;
        return resourcesClosed && readerDone.getCount()==0 && writer.terminated()
                && (encoder == null || !encoder.isAlive());
    }
    private void note(String text) { log.add(text); listener.changed(); }

    private void run() {
        try {
            AccessoryIo opened = AccessoryIo.open(manager, accessory);
            synchronized (resourceLock) {
                if (closing.get()) { opened.close(); return; }
                io = opened;
            }
            openedAt = lastReply = SystemClock.elapsedRealtime();
            note("USB_OPEN: " + opened.mode);
            listener.state("USB opened · waiting for car");
            clock.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
            queue("opening handshake", () -> {
                writeRaw("USBL".getBytes(StandardCharsets.US_ASCII));
                note("TX opening header USBL");
                send(1, 1, Wire.ints(phoneWidth, phoneHeight, phoneDpi, 0, 1, 2, 0));
                initialRequestSent = true;
                note("TX session request: phoneView=" + phoneWidth + "x" + phoneHeight + " dpi=" + phoneDpi
                        + " protocol=1.2 stage=0; no Bluetooth-name exchange");
                if (requestAdbSetup) {
                    note("ADB_SETUP_REQUEST_BEGIN: user-selected; phone debugging was already enabled; authorization may still be required");
                    send(1,8,Wire.ints(1));
                    note("ADB_SETUP_REQUEST_SENT: observed Garmin useAdb request; touch operation remains unverified");
                }
            });
            while (!closing.get()) {
                Wire.Message message = Wire.read(opened.input);
                if (closing.get()) break;
                messages.incrementAndGet(); lastReply = SystemClock.elapsedRealtime();
                received(message);
            }
        } catch (Exception e) { if (!closing.get()) fail(e); }
        finally { try { release(); } finally { readerDone.countDown(); } }
    }
    private void tick() {
        if (closing.get()) return;
        long now = SystemClock.elapsedRealtime();
        if (lastWrite != 0 && now - lastWrite > 5000) {
            fail(new IOException("USB write timed out at " + activeWrite)); return;
        }
        if (writer.activeMillis() > 6000) {
            fail(new IOException("Writer stalled at " + writer.active())); return;
        }
        String failure = SessionWatchdog.failure(now,openedAt,videoReady,readyAt,frames.get(),lastVideoWrite);
        if (failure != null) { fail(new IOException(failure)); return; }
        if (videoReady && frames.get() > 0 && now-lastReply > 15000 && now-lastQuietNote > 30000) {
            lastQuietNote = now;
            note("RX_QUIET: no incoming message for "+((now-lastReply)/1000)
                    +"s; video buffers="+frames.get()+"; event-driven silence alone does not end the stream; safety reports remain enforced");
        }
        if (!videoReady && now - openedAt > 10000) listener.state("Waiting for VIDEO_READY · check car screen");
        // Do not fill the outbound FIFO with pings before the first video buffer.
        if (initialRequestSent && frames.get() > 0 && keepalivePending.compareAndSet(false, true)) {
            if (!queue("keepalive", () -> {
                try { send(1, 5, new byte[0]); } finally { keepalivePending.set(false); }
            })) keepalivePending.set(false);
        }
        listener.changed();
    }
    private boolean queue(String label, SerialWriter.Task action) {
        if (closing.get()) return false;
        return writer.submit(label, () -> { if (!closing.get()) action.run(); });
    }

    private void received(Wire.Message message) throws IOException {
        log.inspectRx(message);
        if (message.service != 2 || message.type != 6)
            note("RX service=" + message.service + " type=" + message.type + " bytes=" + message.payload.length);
        if (message.service != 2) {
            note("Unimplemented service " + message.service + "; no response sent"); return;
        }
        switch (message.type) {
            case 1:
                int[] p = Wire.videoParameters(message.payload);
                note("VIDEO_READY: " + p[0] + "x" + p[1] + " dpi=" + p[2] + " encoding=" + p[3]
                        + " version=" + p[4] + "/" + p[5] + " adbStatus=" + p[6]);
                if (videoReady) { note("Repeated VIDEO_READY ignored"); return; }
                readyAt = SystemClock.elapsedRealtime(); videoReady = true;
                grid.videoSize(p[0],p[1]);
                listener.inputDiagnostic("VIDEO_READY");
                listener.state("Car negotiated video · starting encoder");
                note("ENCODER_START_REQUESTED: reader remains free to receive car messages");
                // The observed client sends its first video buffer BEFORE its orientation report.
                video = new TestVideo(p[0], p[1], p[3], grid, new TestVideo.Output() {
                    public void frame(byte[] data) throws IOException {
                        if (closing.get()) return;
                        try { videoSlots.acquire(); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Video stopped", e); }
                        if (!queue("video " + data.length + " bytes", () -> {
                            try {
                                send(1, 2, data);
                                if (closing.get()) return;
                                lastVideoWrite = SystemClock.elapsedRealtime();
                                long count = frames.incrementAndGet();
                                if (count == 1) {
                                    note("FIRST_VIDEO_BUFFER_SENT: display not visually confirmed");
                                    listener.state("Video buffers sent · check car display");
                                    send(1, 3, Wire.ints(phoneWidth, phoneHeight, 2));
                                    note("TX orientation report after first video buffer");
                                }
                                if (count % 25 == 0) note("TX video buffers=" + count + " total bytes=" + bytes.get());
                            } finally { videoSlots.release(); }
                        })) videoSlots.release();
                    }
                    public void note(String text) { UsbSession.this.note(text); }
                    public void failed(Exception error) { fail(error); }
                });
                videoThread = new Thread(video, "generated-test-video");
                videoThread.start();
                break;
            case 2: throw new IOException("Car reported VIDEO_NOT_READY; reopen Auto+ Show on car, then Retry connection");
            case 6: break;
            case 8:
                if (message.payload.length >= 8) {
                    ByteBuffer probe = ByteBuffer.wrap(message.payload);
                    note("CAR_HID_PROBE_REQUEST x=" + probe.getInt() + " y=" + probe.getInt()
                            + "; request is not an observed Android touch; no injection sent");
                } else note("Short HID-probe request");
                break;
            case 9:
                if (message.payload.length < 8) throw new IOException("Short safety-state message");
                ByteBuffer state = ByteBuffer.wrap(message.payload);
                int restricted = state.getInt(), speed = state.getInt();
                note("Car safety-state fields: restricted=" + restricted + ", speed=" + speed);
                DiagnosticPolicy.requireStationary(restricted,speed);
                if (restricted == 1) {
                    note("STATIONARY_RESTRICTION: generated grid continues at reported speed=0; no restriction override sent");
                    listener.state("Stationary touch grid · car restrictions active");
                }
                break;
            case 11:
                if (message.payload.length >= 4) {
                    note("ADB_STATUS=" + ByteBuffer.wrap(message.payload).getInt()
                            + "; car-reported status, not proof of authorization or working input");
                    if (!adbDiagnosticReported) { adbDiagnosticReported = true; listener.inputDiagnostic("CAR_ADB_STATUS"); }
                }
                else note("Short ADB-status message");
                break;
            default: note("Unknown message ignored; no control response sent");
        }
    }

    private void send(int service, int type, byte[] data) throws IOException {
        synchronized (writeLock) {
            if (released.get()) throw new IOException("Session closed");
            activeWrite = "service=" + service + " type=" + type + " payload=" + data.length;
            lastWrite = SystemClock.elapsedRealtime();
            boolean verbose = writes < 10;
            if (verbose) note("TX_BEGIN " + activeWrite);
            try {
                AccessoryIo target = io;
                if (target == null) throw new IOException("USB is not open");
                Wire.write(target.output, service, type, data);
                bytes.addAndGet(data.length + 8); writes++;
                if (verbose) note("TX_DONE " + activeWrite + " elapsedMs=" + (SystemClock.elapsedRealtime() - lastWrite));
            } finally { lastWrite = 0; activeWrite = "none"; }
        }
    }
    private void writeRaw(byte[] data) throws IOException {
        synchronized (writeLock) {
            if (released.get()) throw new IOException("Session closed");
            activeWrite = "opening header USBL"; lastWrite = SystemClock.elapsedRealtime();
            note("TX_BEGIN " + activeWrite);
            try { io.output.write(data); bytes.addAndGet(data.length); }
            finally { lastWrite = 0; activeWrite = "none"; }
        }
    }

    void stop(String reason) {
        if (!closing.compareAndSet(false, true)) return;
        note("STOP: " + reason);
        if (video != null) video.stop();
        if (videoThread != null) videoThread.interrupt();
        if (lastWrite == 0) writer.submit("termination", () -> {
            try {
                if (io != null) {
                    note("SESSION_TERMINATE_BEGIN: observed Garmin shutdown payload; bounded by close deadline");
                    send(1, 4, Wire.terminationPayload());
                    note("SESSION_TERMINATE_SENT: write completed; car acknowledgement not established");
                }
            } finally { release(); }
        });
        new Thread(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            if (!released.get() && lastWrite != 0) listener.reconnectRequired();
            release();
        }, "usb-link-close-deadline").start();
    }
    private void fail(Exception error) {
        if (!closing.compareAndSet(false, true)) return;
        note("ERROR: " + error.getClass().getSimpleName() + ": " + error.getMessage());
        note("RECOVERY: Retry connection after old USB workers close; if the car refuses, reopen Auto+ Show on car or reconnect cable");
        listener.reconnectRequired(); listener.state("Stopped · use Retry connection"); release();
    }
    private void release() {
        closing.set(true);
        if (!released.compareAndSet(false, true)) return;
        clock.shutdownNow(); writer.close();
        if (video != null) video.stop();
        if (videoThread != null) videoThread.interrupt();
        synchronized (resourceLock) { if (io != null) io.close(); }
        resourcesClosed = true;
        note("CLOSED: video buffers=" + frames.get() + ", tx bytes=" + bytes.get() + ", rx messages=" + messages.get());
        listener.ended(this);
    }
}

package dev.rentaldisplay.probe;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

final class ProbeLog {
    private final LogStore store;
    private final File exportDirectory;
    private String failure;
    private long inspectionUntil;
    private int inspectionMessages, inspectionOmitted;
    ProbeLog(Context context) {
        exportDirectory = new File(context.getCacheDir(),"log-exports");
        LogStore opened;
        try { opened = new LogStore(new File(context.getFilesDir(),"latest-probe.log"),180); }
        catch (IOException e) { opened = null; failure = e.getMessage(); }
        store = opened;
    }
    synchronized void add(String message) {
        if (store == null) return;
        try { store.append(Instant.now() + " " + message + "\n"); }
        catch (IOException e) { failure = e.getMessage(); }
    }
    synchronized void beginInspection() {
        inspectionUntil = android.os.SystemClock.elapsedRealtime()+20000;
        inspectionMessages=0; inspectionOmitted=0;
    }
    synchronized void inspectRx(Wire.Message message) {
        if (android.os.SystemClock.elapsedRealtime() >= inspectionUntil) return;
        if (inspectionMessages++ >= 128) { inspectionOmitted++; return; }
        // VIDEO_READY's tail contains identifiers: retain only its seven numeric fields.
        int limit = message.service==2 && message.type==1 ? 28 : 64;
        int count=Math.min(limit,message.payload.length);
        StringBuilder hex=new StringBuilder();
        char[] digits="0123456789abcdef".toCharArray();
        for (int i=0;i<count;i++) {
            int value=message.payload[i]&255;
            hex.append(digits[value>>>4]).append(digits[value&15]);
        }
        add("INSPECTION_RX service="+message.service+" type="+message.type+" bytes="+message.payload.length
                +" prefixHex="+hex+" omittedBytes="+(message.payload.length-count));
    }
    synchronized void endInspection() {
        inspectionUntil=0;
        add("INSPECTION_RX_SUMMARY messages="+inspectionMessages+" omittedByLimit="+inspectionOmitted
                +"; complete accessory protocol messages only; excludes separate HID/ADB USB traffic");
    }
    synchronized String text() {
        return (failure == null ? "" : "Log storage error: " + failure + "\n") + (store == null ? "" : store.preview());
    }
    synchronized String fullText() throws IOException {
        requireStore(); return store.fullText(LogStore.TEXT_LIMIT);
    }
    synchronized File snapshot() throws IOException {
        requireStore();
        if (!exportDirectory.isDirectory() && !exportDirectory.mkdirs()) throw new IOException("Cannot create log export directory");
        File destination = ExportFiles.resolve(exportDirectory,"rental-display-log-" + UUID.randomUUID() + ".txt");
        store.snapshot(destination); return destination;
    }
    private void requireStore() throws IOException {
        if (store == null || failure != null) throw new IOException("Log storage unavailable: " + failure);
    }
}

package dev.rentaldisplay.probe;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

/** Bounded preview, complete retained file, and immutable export snapshots. */
final class LogStore {
    static final int TEXT_LIMIT = 256 * 1024;
    private final File file;
    private final int previewLines;
    private final ArrayDeque<String> recent = new ArrayDeque<>();
    LogStore(File file, int previewLines) throws IOException {
        this.file = file; this.previewLines = previewLines;
        if (previewLines < 1) throw new IllegalArgumentException("Preview must retain at least one line");
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create log directory");
        if (file.isFile()) try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8))) {
            String line; while ((line=reader.readLine()) != null) remember(line + "\n");
        }
    }
    private void remember(String line) {
        recent.addLast(line); while (recent.size() > previewLines) recent.removeFirst();
    }
    synchronized void append(String line) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file,true)) { out.write(line.getBytes(StandardCharsets.UTF_8)); }
        remember(line);
    }
    synchronized String preview() { StringBuilder s = new StringBuilder(); for (String line : recent) s.append(line); return s.toString(); }
    synchronized long size() { return file.length(); }
    synchronized void snapshot(File destination) throws IOException {
        if (file.getCanonicalFile().equals(destination.getCanonicalFile())) throw new IOException("Snapshot must be a separate file");
        try (OutputStream out = new FileOutputStream(destination)) {
            if (file.isFile()) try (FileInputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[8192]; int count;
                while ((count=in.read(buffer)) != -1) out.write(buffer,0,count);
            }
        }
    }
    synchronized String fullText(int limit) throws IOException { return readText(file,limit); }
    static String readText(File file,int limit) throws IOException {
        long length = file.length();
        if (length > limit) throw new IOException("Full log is " + length + " bytes. Use Share full log or Save full log to get everything.");
        try (FileInputStream in = new FileInputStream(file)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream((int)length);
            byte[] buffer = new byte[8192]; int count;
            while ((count=in.read(buffer)) != -1) {
                if (out.size() + count > limit) throw new IOException("Log exceeds text limit; use Share full log or Save full log.");
                out.write(buffer,0,count);
            }
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }
}

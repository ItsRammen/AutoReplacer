package dev.rentaldisplay.probe;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public final class LogStoreTest {
    private static int assertions;
    private static void check(boolean condition,String label) {
        assertions++; if (!condition) throw new AssertionError(label);
    }
    private interface Throwing { void run() throws Exception; }
    private static void rejects(Throwing task,String label) throws Exception {
        try { task.run(); } catch (IOException expected) { assertions++; return; }
        throw new AssertionError(label);
    }
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("probe-log-tests-");
        try {
            File file = temp.resolve("full.log").toFile();
            LogStore log = new LogStore(file,3);
            StringBuilder expected = new StringBuilder("FIRST_RECORD\n"); log.append("FIRST_RECORD\n");
            for (int i=0;i<1500;i++) {
                String line = "record-"+i+" "+String.join("",java.util.Collections.nCopies(400,"x"))+"\n";
                log.append(line); expected.append(line);
            }
            check(log.size()>512*1024,"test exceeds the previous silent truncation threshold");
            String full = log.fullText(1024*1024);
            check(full.equals(expected.toString()),"full retained log includes first and last records without truncation");
            check(!log.preview().contains("FIRST_RECORD") && log.preview().split("\n").length==3,"preview is bounded independently of full file");
            rejects(() -> log.fullText(LogStore.TEXT_LIMIT),"oversized clipboard text fails instead of returning a partial log");
            check(log.fullText(1024*1024).equals(full),"failed clipboard read leaves full log intact");
            File snapshot = temp.resolve("snapshot.txt").toFile(); log.snapshot(snapshot);
            log.append("AFTER_SNAPSHOT\n");
            check(Arrays.equals(Files.readAllBytes(snapshot.toPath()),full.getBytes(StandardCharsets.UTF_8)),"shared snapshot stays immutable after further logging");
            check(log.fullText(1024*1024).endsWith("AFTER_SNAPSHOT\n"),"live collection continues after a snapshot");
            rejects(() -> log.snapshot(file),"snapshot cannot truncate its own source");
            LogStore reopened = new LogStore(file,3);
            check(reopened.preview().endsWith("AFTER_SNAPSHOT\n"),"recent preview is restored after reopening");
            check(reopened.fullText(1024*1024).startsWith("FIRST_RECORD\n"),"full log survives reopening");
            AtomicReference<Exception> failure = new AtomicReference<>();
            Thread writer = new Thread(() -> {
                try { for (int i=0;i<150;i++) log.append("CONCURRENT_"+i+"\n"); }
                catch (Exception e) { failure.set(e); }
            });
            writer.start(); log.snapshot(snapshot); writer.join(3000);
            check(!writer.isAlive() && failure.get()==null,"collection can continue while a snapshot is captured");
            String captured = new String(Files.readAllBytes(snapshot.toPath()),StandardCharsets.UTF_8);
            check(log.fullText(1024*1024).startsWith(captured) && captured.endsWith("\n"),"snapshot ends at a complete record in the append-only file");
            File exports = temp.resolve("exports").toFile(); check(exports.mkdir(),"private export directory created");
            File allowed = ExportFiles.resolve(exports,"rental-display-log-abc123.txt");
            check(allowed.getParentFile().equals(exports.getCanonicalFile()),"valid export stays in its private directory");
            rejects(() -> ExportFiles.resolve(exports,"../full.log"),"parent traversal rejected");
            rejects(() -> ExportFiles.resolve(exports,"rental-display-log-../full.txt"),"embedded separator rejected");
            rejects(() -> ExportFiles.resolve(exports,"latest-probe.log"),"live log name is not a shareable snapshot");
            rejects(() -> ExportFiles.resolve(exports,"rental-display-log-%2e%2e.txt"),"encoded path fragments rejected");
            try {
                Files.createSymbolicLink(allowed.toPath(),file.toPath());
                rejects(() -> ExportFiles.resolve(exports,allowed.getName()),"symlink escape rejected");
            } catch (UnsupportedOperationException | java.nio.file.FileSystemException unsupported) { /* Host may lack symlink support. */ }
            System.out.println("LogStoreTest: "+assertions+" assertions passed");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(temp)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
        }
    }
}

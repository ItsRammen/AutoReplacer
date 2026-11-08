package dev.rentaldisplay.probe;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** A blocked outbound transfer must never block the USB reader. */
final class SerialWriter implements AutoCloseable {
    interface Task { void run() throws Exception; }
    interface Failure { void failed(Exception error); }
    private final ThreadPoolExecutor executor;
    private final Failure failure;
    private volatile String active = "none";
    private volatile long started;
    SerialWriter(int capacity, Failure failure) {
        this.failure = failure;
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity), r -> new Thread(r, "usb-link-writer"));
    }
    boolean submit(String label, Task task) {
        try {
            executor.execute(() -> {
                active = label; started = System.nanoTime();
                try { task.run(); }
                catch (Exception e) { failure.failed(e); }
                finally { started = 0; active = "none"; }
            });
            return true;
        } catch (RejectedExecutionException e) {
            if (!executor.isShutdown()) failure.failed(new IllegalStateException("USB writer queue is full"));
            return false;
        }
    }
    String active() { return active; }
    long activeMillis() { long n = started; return n == 0 ? 0 : (System.nanoTime() - n) / 1000000L; }
    boolean terminated() { return executor.isTerminated(); }
    @Override public void close() { executor.shutdownNow(); }
}

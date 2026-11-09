package com.andrerinas.openheadunit.autoshow;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.io.ByteArrayInputStream;

public final class SerialWriterTest {
    private static int assertions;
    private static void check(boolean condition, String label) {
        assertions++; if (!condition) throw new AssertionError(label);
    }
    private static void await(CountDownLatch latch) throws Exception {
        if (!latch.await(2, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for test task");
    }
    public static void main(String[] args) throws Exception {
        CountDownLatch writing = new CountDownLatch(1), unblock = new CountDownLatch(1), finished = new CountDownLatch(1);
        AtomicInteger order = new AtomicInteger();
        AtomicReference<Exception> failure = new AtomicReference<>();
        try (SerialWriter writer = new SerialWriter(3, failure::set)) {
            writer.submit("blocked video", () -> { writing.countDown(); unblock.await(); order.incrementAndGet(); });
            await(writing);
            check("blocked video".equals(writer.active()), "diagnostic identifies blocked transfer");
            check(writer.submit("orientation", () -> {
                if (order.getAndIncrement() != 1) throw new Exception("Writer reordered messages");
                finished.countDown();
            }), "submit does not wait for blocked transfer");
            // The read side must remain usable while the write side is stalled.
            Wire.Message reply = Wire.read(new ByteArrayInputStream(Wire.header(2, 6, 0)));
            check(reply.type == 6 && order.get() == 0, "car reply can be consumed while outbound video blocks");
            unblock.countDown(); await(finished);
            check(order.get() == 2 && failure.get() == null, "serialized order preserved after write recovers");
        } finally { unblock.countDown(); }
        CountDownLatch active = new CountDownLatch(1), hold = new CountDownLatch(1), overflow = new CountDownLatch(1);
        AtomicInteger cancelled = new AtomicInteger();
        SerialWriter bounded = new SerialWriter(1, e -> overflow.countDown());
        try {
            bounded.submit("active", () -> { active.countDown(); try { hold.await(); } catch (InterruptedException ignored) {} });
            await(active);
            check(bounded.submit("queued", cancelled::incrementAndGet), "bounded queue accepts one pending task");
            check(!bounded.submit("overflow", () -> {}), "queue overflow is rejected instead of growing without limit");
            await(overflow); bounded.close(); hold.countDown();
            check(!bounded.submit("after close", () -> {}), "closed writer refuses further work");
            check(cancelled.get() == 0, "close cancels queued work");
        } finally { bounded.close(); hold.countDown(); }
        CountDownLatch failed = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        try (SerialWriter writer = new SerialWriter(1, e -> { error.set(e); failed.countDown(); })) {
            writer.submit("broken USB", () -> { throw new java.io.IOException("Simulated disconnect"); });
            await(failed);
            check(error.get() instanceof java.io.IOException, "I/O failure reaches session handler");
        }
        CountDownLatch nativeActive = new CountDownLatch(1), finishNative = new CountDownLatch(1);
        SerialWriter nativeWriter = new SerialWriter(1,e -> {});
        try {
            nativeWriter.submit("simulated native transfer ignoring interrupt", () -> {
                nativeActive.countDown();
                boolean finishedNative = false;
                while (!finishedNative) {
                    try { finishNative.await(); finishedNative = true; }
                    catch (InterruptedException ignored) { /* Native USB can ignore Java interrupts. */ }
                }
            });
            await(nativeActive); nativeWriter.close();
            check(!nativeWriter.terminated(),"close request is not proof old USB worker has exited");
            check(!nativeWriter.submit("overlapping session", () -> {}),"closing writer cannot accept reused-connection work");
            finishNative.countDown();
            long deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            while (!nativeWriter.terminated() && System.nanoTime()<deadline) Thread.yield();
            check(nativeWriter.terminated(),"completed native transfer permits retired worker to finish");
        } finally { finishNative.countDown(); nativeWriter.close(); }
        System.out.println("SerialWriterTest: " + assertions + " assertions passed");
    }
}

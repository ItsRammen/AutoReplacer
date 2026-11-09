package com.andrerinas.openheadunit.autoshow;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Runs CarTouchServer against a copy of the car helper's server loop (UsbLinkServerThread): a single
 * thread that accepts one client at a time and ends its process-wide loop when a line fails to parse.
 */
public final class CarTouchServerTest {
    private static int assertions;
    private static void check(boolean value, String label) { assertions++; if (!value) throw new AssertionError(label); }

    /** The helper's loop: accept, print the banner, read lines, Integer.valueOf() on x/y/b; any exception ends it. */
    static final class MockHelper extends Thread {
        final ServerSocket server; final AtomicBoolean exited = new AtomicBoolean(); final AtomicInteger clients = new AtomicInteger();
        MockHelper() throws IOException {
            server = new ServerSocket(); server.setReuseAddress(true);
            server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 8051));
        }
        @Override public void run() {
            try {
                while (true) {
                    Socket socket = server.accept();
                    clients.incrementAndGet();
                    try {
                        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        out.println("USBLINKTS v1.0");
                        String cmd;
                        while ((cmd = in.readLine()) != null) {
                            java.util.Map<String,String> map = new java.util.HashMap<>();
                            for (String entry : cmd.split(" ")) if (entry.contains("=")) { String[] kv = entry.split("="); map.put(kv[0], kv[1]); }
                            if ("ts".equals(map.get("a"))) {
                                Integer.valueOf(map.get("x")); Integer.valueOf(map.get("y")); Integer.valueOf(map.get("b"));
                            }
                        }
                    } catch (Exception e) { exited.set(true); try { server.close(); } catch (IOException ignored) {} return; }
                }
            } catch (IOException e) { exited.set(true); }
        }
    }

    static final class Recorder implements CarTouchServer.Listener {
        final java.util.List<String> lines = new CopyOnWriteArrayList<>();
        public void log(String text) { lines.add(text); }
        public void carSize(int a, int b, int c, int d) {}
        public void touch(int x, int y, int b, int cw, int ch) {}
        boolean has(String prefix) { for (String l : lines) if (l.startsWith(prefix)) return true; return false; }
    }

    static boolean waitFor(java.util.function.BooleanSupplier c, long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (c.getAsBoolean()) return true; Thread.sleep(50); }
        return c.getAsBoolean();
    }

    public static void main(String[] args) throws Exception {
        // 1. An idle stale helper: the server asks it to exit and takes the port.
        MockHelper idle = new MockHelper(); idle.start();
        Recorder r1 = new Recorder(); CarTouchServer s1 = new CarTouchServer(r1); s1.start();
        check(waitFor(() -> r1.has("TS8051_LISTENING"), 5000), "idle helper evicted and the port taken");
        check(r1.has("TS8051_BIND_FAILED") && r1.has("TS8051_EVICT_SENT"), "the failure and the eviction were logged");
        check(idle.exited.get(), "the helper loop ended");
        s1.stop(); Thread.sleep(300);

        // 2. A helper busy with the car's connection: our line is read after that connection closes.
        MockHelper busy = new MockHelper(); busy.start();
        Socket car = new Socket("127.0.0.1", 8051);
        car.getOutputStream().write("a=init sw=2340 sh=1080 cw=1280 ch=720\n".getBytes()); car.getOutputStream().flush();
        Recorder r2 = new Recorder(); CarTouchServer s2 = new CarTouchServer(r2); s2.start();
        final Recorder r3 = new Recorder();
        check(waitFor(() -> r2.has("TS8051_EVICT_SENT"), 5000), "eviction sent while the helper serves the car");
        check(!r2.has("TS8051_LISTENING"), "the port stays taken while the car's connection is open");
        car.close(); // the cable is pulled
        check(waitFor(() -> r2.has("TS8051_LISTENING"), 15000), "the port is ours once the car's connection ends");
        check(busy.exited.get(), "the busy helper ended afterwards");
        // 3. The port is ours: lines with \n, \r\n and \r endings, and raw bytes, are all seen.
        final java.util.List<String> touches = new CopyOnWriteArrayList<>();
        s2.stop(); Thread.sleep(300);
        CarTouchServer.Listener catcher = new CarTouchServer.Listener() {
            public void log(String text) { r3.lines.add(text); }
            public void carSize(int a, int b, int c, int d) { touches.add("init " + c + "x" + d); }
            public void touch(int x, int y, int b, int cw, int ch) { touches.add("touch " + x + "," + y + "," + b + " in " + cw + "x" + ch); }
        };
        CarTouchServer s3 = new CarTouchServer(catcher); s3.start();
        check(waitFor(() -> r3.has("TS8051_LISTENING"), 5000), "the server is listening for the framing test");
        Socket client = new Socket("127.0.0.1", 8051);
        client.getOutputStream().write("a=init sw=2340 sh=1080 cw=1280 ch=720\r\na=ts x=5 y=6 b=0\na=ts x=7 y=8 b=1\r".getBytes());
        client.getOutputStream().flush();
        check(waitFor(() -> touches.size() == 3, 5000), "init and both touches seen despite mixed line endings: " + touches);
        check(touches.get(0).equals("init 1280x720") && touches.get(1).equals("touch 5,6,0 in 1280x720") && touches.get(2).equals("touch 7,8,1 in 1280x720"), "values parsed: " + touches);
        check(waitFor(() -> r3.has("TS8051_FIRST_BYTES"), 2000), "the first raw bytes are logged");
        client.close();
        check(waitFor(() -> r3.has("TS8051_CLOSED lines=3 touches=2 bytes="), 3000), "the close line counts lines, touches and bytes: " + r3.lines);
        s3.stop();
        System.out.println("CarTouchServerTest OK (" + assertions + " assertions)");
    }
}

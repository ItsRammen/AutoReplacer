package com.andrerinas.openheadunit.autoshow;

import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class PortListeningTest {
    private static int assertions;
    private static void check(boolean value, String label) { assertions++; if (!value) throw new AssertionError(label); }
    public static void main(String[] args) throws Exception {
        int port;
        try (ServerSocket free = new ServerSocket(0)) { port = free.getLocalPort(); }
        check(!PortListening.isListening(port), "a free port is not listening");
        final AtomicInteger accepted = new AtomicInteger();
        ServerSocket server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
        Thread acceptor = new Thread(() -> { try { while (true) { server.accept().close(); accepted.incrementAndGet(); } } catch (Exception ignored) {} });
        acceptor.setDaemon(true); acceptor.start();
        check(PortListening.isListening(port), "a listening port is seen");
        check(PortListening.isListening(port), "and again");
        Thread.sleep(300);
        check(accepted.get() == 0, "the check never connects, so the server's single accept is not used");
        server.close(); Thread.sleep(100);
        check(!PortListening.isListening(port), "closed again, not listening");
        // After a real client came and went (TIME_WAIT), the port must read as free, not as taken.
        ServerSocket again = new ServerSocket();
        again.setReuseAddress(true);
        again.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
        Socket client = new Socket("127.0.0.1", port); Socket peer = again.accept();
        peer.close(); client.close(); again.close(); Thread.sleep(100);
        check(!PortListening.isListening(port), "TIME_WAIT leftovers do not count as listening");
        System.out.println("PortListeningTest OK (" + assertions + " assertions)");
    }
}

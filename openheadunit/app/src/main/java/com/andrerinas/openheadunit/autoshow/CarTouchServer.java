package com.andrerinas.openheadunit.autoshow;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Listens on 127.0.0.1:8051. The head unit pushes a touch helper over ADB that listens on this port and
 * injects into Android; that helper cannot inject on Android 14+. Holding the port first means the car's
 * connection reaches this server instead, and its touch coordinates are handed to the Android Auto session.
 */
public final class CarTouchServer {
    public interface Listener {
        void log(String text);
        void carSize(int phoneWidth, int phoneHeight, int carWidth, int carHeight);
        /** x,y in car pixels; b is the MotionEvent action: 0 down, 1 up, 2 move. */
        void touch(int x, int y, int b, int carWidth, int carHeight);
    }

    private final Listener listener;
    private volatile boolean started, stopped;
    private volatile boolean carConnected;

    /** True while the car's forwarded connection is open (the car's ADB path is alive). */
    public boolean isCarConnected() { return carConnected; }
    private volatile ServerSocket server;
    private int cw, ch;

    public CarTouchServer(Listener listener) { this.listener = listener; }

    public synchronized void start() {
        if (started) return;
        started = true;
        Thread thread = new Thread(this::run, "car-touch-8051");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        stopped = true;
        ServerSocket s = server;
        if (s != null) { try { s.close(); } catch (Exception ignored) {} }
    }

    /**
     * The car's own helper (UsbLinkTS) exits when it reads a line it cannot parse: its server loop
     * catches the NumberFormatException from Integer.valueOf, returns, and the process ends with its
     * only thread. A helper left running from an earlier plug-in therefore holds port 8051 until the
     * phone restarts, unless something tells it to stop. This sends it one such line. If it is serving
     * the car's connection it reads ours when that one closes (at the latest when the cable is pulled).
     */
    static final String HELPER_EXIT_LINE = "a=ts x=exit y=exit b=exit\n";

    private boolean evictHelper() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), TsProtocol.PORT), 500);
            s.getOutputStream().write(HELPER_EXIT_LINE.getBytes(StandardCharsets.US_ASCII));
            s.getOutputStream().flush();
            s.setSoTimeout(1200);
            try { while (s.getInputStream().read() >= 0) { /* the banner, then end of stream when the helper exits */ } }
            catch (java.net.SocketTimeoutException busy) { /* serving the car's connection; it reads our line afterwards */ }
            listener.log("TS8051_EVICT_SENT: asked the old touch helper to exit");
            return true;
        } catch (Exception e) {
            listener.log("TS8051_EVICT_FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }

    private void run() {
        int failures = 0;
        while (!stopped) {
            try (ServerSocket s = new ServerSocket()) {
                server = s;
                s.setReuseAddress(true);
                s.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), TsProtocol.PORT), 2);
                listener.log("TS8051_LISTENING 127.0.0.1:" + TsProtocol.PORT);
                failures = 0;
                while (!stopped) handle(s.accept());
            } catch (Exception e) {
                if (stopped) return;
                if (failures % 12 == 0)
                    listener.log("TS8051_BIND_FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage()
                            + " (the car's own helper may hold the port; trying to make it exit, retrying every 5 s)");
                // Try to clear an old helper right away, and again every minute while the port stays taken.
                boolean evicted = failures % 12 == 0 && evictHelper();
                failures++;
                try { Thread.sleep(evicted ? 800 : 5000); } catch (InterruptedException interrupted) { return; }
            }
        }
    }

    private void handle(Socket socket) {
        int lines = 0, touches = 0, idle = 0;
        long bytes = 0;
        boolean firstBytes = true;
        listener.log("TS8051_CONNECTED " + socket.getRemoteSocketAddress());
        carConnected = true;
        try (Socket sock = socket) {
            sock.setSoTimeout(30000);
            java.io.InputStream in = sock.getInputStream();
            OutputStream out = sock.getOutputStream();
            out.write((TsProtocol.BANNER + "\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            byte[] buffer = new byte[1024];
            StringBuilder line = new StringBuilder();
            while (true) {
                int n;
                try {
                    n = in.read(buffer);
                } catch (java.net.SocketTimeoutException quiet) {
                    // Connected but silent: say so (a limited number of times), so a car that never sends is visible.
                    if (idle++ < 20) listener.log("TS8051_IDLE: connected, nothing received for " + (idle * 30) + " s (bytes so far " + bytes + ")");
                    continue;
                }
                if (n < 0) break;
                bytes += n;
                if (firstBytes) {
                    firstBytes = false;
                    listener.log("TS8051_FIRST_BYTES " + n + " bytes: " + printable(buffer, Math.min(n, 120)));
                }
                for (int k = 0; k < n; k++) {
                    char c = (char) (buffer[k] & 0xff);
                    // Either line ending ends a command: the car's framing has not been seen to vary, but it is cheap to allow.
                    if (c == '\n' || c == '\r') {
                        if (line.length() > 0) {
                            lines++;
                            if (handleLine(line.toString())) touches++;
                            line.setLength(0);
                        }
                    } else if (line.length() < 512) line.append(c);
                }
            }
        } catch (Exception e) {
            listener.log("TS8051_ERROR " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        carConnected = false;
        listener.log("TS8051_CLOSED lines=" + lines + " touches=" + touches + " bytes=" + bytes);
    }

    /** Handles one command line; true if it was a touch. */
    private boolean handleLine(String line) {
        TsProtocol.Command c = TsProtocol.parse(line);
        if (c.kind == TsProtocol.Kind.INIT) {
            cw = c.cw; ch = c.ch;
            listener.log("TS8051_INIT phone=" + c.sw + "x" + c.sh + " car=" + c.cw + "x" + c.ch);
            listener.carSize(c.sw, c.sh, c.cw, c.ch);
            return false;
        }
        if (c.kind == TsProtocol.Kind.TOUCH) {
            listener.touch(c.x, c.y, c.b, cw, ch);
            return true;
        }
        listener.log("TS8051_RX_UNKNOWN \"" + (line.length() > 120 ? line.substring(0, 120) : line) + "\"");
        return false;
    }

    private static String printable(byte[] data, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int v = data[i] & 0xff;
            if (v == '\n') sb.append("\\n"); else if (v == '\r') sb.append("\\r");
            else if (v >= 32 && v < 127) sb.append((char) v); else sb.append(String.format("<%02x>", v));
        }
        return sb.toString();
    }
}

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
                            + " (the car's own helper may hold the port: as shell, pkill -f UsbLinkTS); retrying every 5 s");
                failures++;
                try { Thread.sleep(5000); } catch (InterruptedException interrupted) { return; }
            }
        }
    }

    private void handle(Socket socket) {
        int lines = 0, touches = 0;
        listener.log("TS8051_CONNECTED " + socket.getRemoteSocketAddress());
        try (Socket sock = socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.US_ASCII))) {
            OutputStream out = sock.getOutputStream();
            out.write((TsProtocol.BANNER + "\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String line;
            while ((line = in.readLine()) != null) {
                lines++;
                TsProtocol.Command c = TsProtocol.parse(line);
                if (c.kind == TsProtocol.Kind.INIT) {
                    cw = c.cw; ch = c.ch;
                    listener.log("TS8051_INIT phone=" + c.sw + "x" + c.sh + " car=" + c.cw + "x" + c.ch);
                    listener.carSize(c.sw, c.sh, c.cw, c.ch);
                } else if (c.kind == TsProtocol.Kind.TOUCH) {
                    touches++;
                    listener.touch(c.x, c.y, c.b, cw, ch);
                } else {
                    listener.log("TS8051_RX_UNKNOWN \"" + (line.length() > 120 ? line.substring(0, 120) : line) + "\"");
                }
            }
        } catch (Exception e) {
            listener.log("TS8051_ERROR " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        listener.log("TS8051_CLOSED lines=" + lines + " touches=" + touches);
    }
}

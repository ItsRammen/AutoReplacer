package dev.rentaldisplay.probe;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Listens on 127.0.0.1:8051 so the head unit's ADB-forwarded touch connection reaches this app
 * instead of its pushed helper, which cannot inject on Android 14+. Receives coordinates only;
 * nothing is injected into other apps.
 */
final class CarTouchBridge {
    private final ProbeLog log;
    private final GridState grid;
    private final Runnable changed;
    private volatile boolean started;
    private int cw, ch, sw, sh;
    private long lastMoveLog;

    CarTouchBridge(ProbeLog log, GridState grid, Runnable changed) {
        this.log = log; this.grid = grid; this.changed = changed;
    }

    synchronized void start() {
        if (started) return;
        started = true;
        Thread thread = new Thread(this::run, "car-touch-8051");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        int failures = 0;
        while (true) {
            try (ServerSocket server = new ServerSocket()) {
                server.setReuseAddress(true);
                server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), TsProtocol.PORT), 2);
                log.add("TS8051_LISTENING: 127.0.0.1:" + TsProtocol.PORT + "; open this app before plugging in so the car's touch connection reaches it");
                failures = 0;
                while (true) {
                    Socket socket = server.accept();
                    handle(socket);
                }
            } catch (Exception e) {
                if (failures % 12 == 0)
                    log.add("TS8051_BIND_FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage()
                            + "; port may be held by the car's helper (unplug, then as shell: pkill -f UsbLinkTS); retrying every 5 s");
                failures++;
                try { Thread.sleep(5000); } catch (InterruptedException interrupted) { return; }
            }
        }
    }

    private void handle(Socket socket) {
        int lines = 0, downs = 0, touches = 0;
        log.add("TS8051_CONNECTED: from " + socket.getRemoteSocketAddress());
        changed.run();
        try (Socket s = socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII))) {
            OutputStream out = s.getOutputStream();
            out.write((TsProtocol.BANNER + "\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            log.add("TS8051_BANNER_SENT: " + TsProtocol.BANNER);
            String line;
            while ((line = in.readLine()) != null) {
                lines++;
                String shown = line.length() > 200 ? line.substring(0, 200) + "…" : line;
                TsProtocol.Command c = TsProtocol.parse(line);
                if (c.kind == TsProtocol.Kind.INIT) {
                    sw = c.sw; sh = c.sh; cw = c.cw; ch = c.ch;
                    grid.carLive(true);
                    log.add("TS8051_INIT: phone(server)=" + sw + "x" + sh + " car(client)=" + cw + "x" + ch + " raw=\"" + shown + "\"");
                } else if (c.kind == TsProtocol.Kind.TOUCH) {
                    touches++;
                    if (c.b == 0) downs++;
                    touch(c, shown);
                } else {
                    log.add("TS8051_RX_UNKNOWN: \"" + shown + "\"");
                }
                changed.run();
            }
        } catch (Exception e) {
            log.add("TS8051_ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        grid.carLive(false);
        log.add("TS8051_CLOSED: lines=" + lines + " touches=" + touches + " downs=" + downs);
        changed.run();
    }

    private void touch(TsProtocol.Command c, String raw) {
        GridState.Snapshot before = grid.snapshot();
        int width = cw > 0 ? cw : before.videoWidth, height = ch > 0 ? ch : before.videoHeight;
        String basis = cw > 0 ? "init" : (width > 0 ? "video-size fallback" : "unknown");
        double nx = TsProtocol.normal(c.x, width), ny = TsProtocol.normal(c.y, height);
        String kind = TsProtocol.action(c.b);
        String summary = String.format(Locale.US, "CAR-8051 %s cell %d  x=%.3f y=%.3f", kind, GridState.cell(nx, ny), nx, ny);
        grid.carLive(true);
        GridState.Hit hit = grid.carTouch(nx, ny, 0, c.b == 0, true, summary);
        long now = android.os.SystemClock.uptimeMillis();
        boolean move = c.b == 2;
        if (move && now - lastMoveLog < 100) return;
        if (move) lastMoveLog = now;
        log.add(String.format(Locale.US,
                "CAR_TOUCH_8051 action=%s car=%d,%d basis=%s(%dx%d) norm=%.4f,%.4f cell=%d expected=%d hit=%s deltaNorm=%.4f,%.4f phoneGridVisible=%s raw=\"%s\"",
                kind, c.x, c.y, basis, width, height, nx, ny, hit.cell, hit.expected, hit.correct, hit.dx, hit.dy,
                before.gridVisible, raw));
    }
}

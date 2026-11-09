package dev.rentaldisplay.probe;

import android.content.Context;
import android.content.pm.PackageInfo;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * First Android Auto hook-up step: is Android Auto installed, is its developer head-unit server
 * listening on 127.0.0.1:5277, and does it answer an AAP version request? Stops before TLS.
 */
final class AndroidAutoCheck {
    static final String PACKAGE = "com.google.android.projection.gearhead";
    interface Done { void result(String summary); }

    static void run(Context context, ProbeLog log, Done done) {
        new Thread(() -> done.result(check(context.getApplicationContext(), log)), "android-auto-check").start();
    }

    private static String check(Context context, ProbeLog log) {
        String version;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(PACKAGE, 0);
            version = info.versionName;
            log.add("AA_PACKAGE: " + PACKAGE + " version=" + version);
        } catch (Exception e) {
            log.add("AA_PACKAGE: not installed or not visible (" + e.getClass().getSimpleName() + ")");
            return "Android Auto not found on this phone";
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", AapFrame.HEAD_UNIT_SERVER_PORT), 2000);
            socket.setSoTimeout(4000);
            log.add("AA_HU_SERVER: connected to 127.0.0.1:" + AapFrame.HEAD_UNIT_SERVER_PORT);
            OutputStream out = socket.getOutputStream();
            byte[] request = AapFrame.versionRequest(1, 2);
            out.write(request); out.flush();
            log.add("AA_VERSION_REQUEST_SENT: 1.2 raw=" + AapFrame.hex(request, request.length));
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[256];
            int total = 0;
            long deadline = System.currentTimeMillis() + 4000;
            while (total < 12 && System.currentTimeMillis() < deadline) {
                int n = in.read(buffer, total, buffer.length - total);
                if (n < 0) break;
                total += n;
            }
            log.add("AA_VERSION_RAW: bytes=" + total + " " + AapFrame.hex(buffer, total));
            AapFrame.VersionResponse r = AapFrame.parseVersionResponse(buffer, total);
            if (r == null) {
                log.add("AA_VERSION_RESPONSE: not recognised; see raw bytes");
                return "Android Auto " + version + ": head unit server answered, but not with a recognised version response";
            }
            log.add("AA_VERSION_RESPONSE: phone AAP " + r.major + "." + r.minor + " status=" + r.status
                    + (r.ok() ? " (match)" : " (mismatch)") + "; next step would be TLS, not attempted");
            return "Android Auto " + version + ": head unit server OK, AAP " + r.major + "." + r.minor
                    + (r.ok() ? " accepted" : " status " + r.status);
        } catch (java.net.ConnectException e) {
            log.add("AA_HU_SERVER: connection refused on 5277; start Android Auto's head unit server first");
            return "Android Auto " + version + ": head unit server not running (start it in Android Auto developer settings)";
        } catch (Exception e) {
            log.add("AA_HU_SERVER: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return "Android Auto " + version + ": check failed (" + e.getClass().getSimpleName() + ")";
        }
    }
}

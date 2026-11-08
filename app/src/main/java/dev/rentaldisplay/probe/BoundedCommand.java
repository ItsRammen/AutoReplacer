package dev.rentaldisplay.probe;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/** Fixed diagnostic commands only. Never runs a shell or asks for elevated privileges. */
final class BoundedCommand {
    static final class Result {
        final String output;
        final boolean timedOut, truncated;
        final int exit;
        Result(String output, boolean timedOut, boolean truncated, int exit) {
            this.output=output; this.timedOut=timedOut; this.truncated=truncated; this.exit=exit;
        }
    }
    static Result run(String[] command,long timeoutMs,int limit) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        StringBuilder text = new StringBuilder();
        boolean[] truncated = {false};
        Thread reader = new Thread(() -> {
            try (InputStream stream = process.getInputStream()) {
                byte[] buffer = new byte[1024]; int n;
                while ((n=stream.read(buffer))!=-1) {
                    synchronized (text) {
                        int keep=Math.min(n,Math.max(0,limit-text.length()));
                        text.append(new String(buffer,0,keep,java.nio.charset.StandardCharsets.UTF_8));
                        if (keep<n) truncated[0]=true;
                    }
                }
            } catch (java.io.IOException ignored) { }
        },"inspection-command-output");
        reader.setDaemon(true); reader.start();
        boolean completed=false;
        try {
            completed=process.waitFor(timeoutMs,TimeUnit.MILLISECONDS);
            if (!completed) process.destroyForcibly();
            reader.join(500);
            synchronized (text) {
                return new Result(text.toString(),!completed,truncated[0],completed ? process.exitValue() : -1);
            }
        } finally {
            process.destroyForcibly();
            try { process.getInputStream().close(); } catch (java.io.IOException ignored) { }
            try { process.getOutputStream().close(); } catch (java.io.IOException ignored) { }
            try { process.getErrorStream().close(); } catch (java.io.IOException ignored) { }
        }
    }
}

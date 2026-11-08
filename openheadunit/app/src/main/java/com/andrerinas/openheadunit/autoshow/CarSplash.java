package com.andrerinas.openheadunit.autoshow;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.Surface;
import java.io.IOException;

/**
 * A waiting picture for the car while Android Auto starts. It uses an encoder of its own, because a
 * Surface accepts only one kind of producer: once Android Auto's decoder renders into the main
 * encoder's Surface, nothing else can draw on it. The service stops this one when the first Android
 * Auto picture comes out of the main encoder.
 */
public final class CarSplash {
    public interface Text { String line(); }

    private final int width, height;
    private final CarEncoder.Sink sink;
    private final Text text;
    private CarEncoder encoder;
    private Surface surface;
    private Thread painter;
    private volatile boolean stopped;

    public CarSplash(int width, int height, CarEncoder.Sink sink, Text text) {
        this.width = width; this.height = height; this.sink = sink; this.text = text;
    }

    public synchronized void start() throws IOException {
        encoder = new CarEncoder(width, height, sink);
        surface = encoder.start();
        painter = new Thread(this::paintLoop, "car-splash");
        painter.start();
    }

    private void paintLoop() {
        Paint background = new Paint();
        background.setColor(Color.rgb(16, 20, 24));
        Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
        title.setColor(Color.WHITE);
        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        title.setTextAlign(Paint.Align.CENTER);
        title.setTextSize(height * 0.09f);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(Color.rgb(176, 190, 197));
        line.setTextAlign(Paint.Align.CENTER);
        line.setTextSize(height * 0.045f);
        Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(height * 0.012f);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(Color.rgb(48, 58, 66));
        Paint arc = new Paint(track);
        arc.setColor(Color.rgb(79, 195, 247));
        float radius = height * 0.07f;
        float cx = width / 2f, cy = height * 0.40f;
        RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        long started = SystemClock.elapsedRealtime();
        try {
            while (!stopped) {
                long elapsed = SystemClock.elapsedRealtime() - started;
                Canvas canvas = surface.lockCanvas(null);
                try {
                    canvas.drawRect(0, 0, width, height, background);
                    canvas.drawCircle(cx, cy, radius, track);
                    canvas.drawArc(oval, (elapsed * 0.36f) % 360f, 100f, false, arc);
                    canvas.drawText("AutoReplacer", cx, height * 0.60f, title);
                    String message = text.line();
                    float y = height * 0.68f;
                    if (message != null && !message.isEmpty()) {
                        for (String row : wrap(message, line, width * 0.8f)) {
                            canvas.drawText(row, cx, y, line);
                            y += height * 0.06f;
                        }
                    }
                    canvas.drawText(elapsed / 1000 + " s", cx, y + height * 0.02f, line);
                } finally {
                    surface.unlockCanvasAndPost(canvas);
                }
                Thread.sleep(100);
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            if (!stopped) sink.log("CAR_SPLASH_STOPPED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static java.util.List<String> wrap(String text, Paint paint, float maxWidth) {
        java.util.List<String> rows = new java.util.ArrayList<>();
        StringBuilder row = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = row.length() == 0 ? word : row + " " + word;
            if (row.length() > 0 && paint.measureText(candidate) > maxWidth) { rows.add(row.toString()); row = new StringBuilder(word); }
            else row = new StringBuilder(candidate);
        }
        if (row.length() > 0) rows.add(row.toString());
        return rows.size() > 3 ? rows.subList(0, 3) : rows;
    }

    public synchronized void stop() {
        if (stopped) return;
        stopped = true;
        if (painter != null) { painter.interrupt(); try { painter.join(500); } catch (InterruptedException ignored) {} }
        if (encoder != null) encoder.stop();
    }
}

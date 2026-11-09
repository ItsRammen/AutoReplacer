package com.andrerinas.openheadunit.autoshow;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.Surface;
import java.io.IOException;

/**
 * A touch calibration picture for the car: one cross at a time, in picture fractions, with a
 * progress line. It uses an encoder of its own, like {@link CarSplash}. The car shows it exactly as it
 * shows Android Auto's picture, so where a tap lands against the cross is the car's real touch mapping.
 */
public final class CarCalibrationScreen {
    public interface Progress {
        int index();
        int total();
        double[] target(int index);   // {x, y} as fractions of the picture
        String message();
        String title();                // "Touch calibration", in the app's language
        String step(int index, int total);   // "3 of 9: tap the middle of the cross", in the app's language
    }

    private final int width, height;
    private final CarEncoder.Sink sink;
    private final Progress progress;
    private CarEncoder encoder;
    private Surface surface;
    private Thread painter;
    private volatile boolean stopped;

    public CarCalibrationScreen(int width, int height, CarEncoder.Sink sink, Progress progress) {
        this.width = width; this.height = height; this.sink = sink; this.progress = progress;
    }

    public synchronized void start() throws IOException {
        encoder = new CarEncoder(width, height, sink);
        surface = encoder.start();
        painter = new Thread(this::paintLoop, "car-calibration");
        painter.start();
    }

    private void paintLoop() {
        Paint background = new Paint(); background.setColor(Color.rgb(16, 20, 24));
        Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG); grid.setColor(Color.rgb(34, 44, 52)); grid.setStrokeWidth(2);
        Paint done = new Paint(Paint.ANTI_ALIAS_FLAG); done.setColor(Color.rgb(46, 125, 50)); done.setStyle(Paint.Style.FILL);
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG); ring.setColor(Color.rgb(100, 230, 204)); ring.setStyle(Paint.Style.STROKE); ring.setStrokeWidth(height * 0.008f);
        Paint cross = new Paint(ring); cross.setStrokeCap(Paint.Cap.ROUND);
        Paint title = new Paint(Paint.ANTI_ALIAS_FLAG); title.setColor(Color.WHITE); title.setTextAlign(Paint.Align.CENTER);
        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); title.setTextSize(height * 0.06f);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG); line.setColor(Color.rgb(176, 190, 197)); line.setTextAlign(Paint.Align.CENTER); line.setTextSize(height * 0.04f);
        long started = SystemClock.elapsedRealtime();
        try {
            while (!stopped) {
                Canvas canvas = surface.lockCanvas(null);
                try {
                    canvas.drawRect(0, 0, width, height, background);
                    // A light frame so the edges of the picture are visible on the car.
                    canvas.drawRect(2, 2, width - 2, height - 2, grid);
                    int index = progress.index(), total = progress.total();
                    for (int i = 0; i < total; i++) {
                        double[] t = progress.target(i);
                        float x = (float) (t[0] * width), y = (float) (t[1] * height);
                        if (i < index) canvas.drawCircle(x, y, height * 0.02f, done);
                        else if (i == index) {
                            float pulse = 1f + 0.12f * (float) Math.sin((SystemClock.elapsedRealtime() - started) / 180.0);
                            float r = height * 0.05f * pulse;
                            canvas.drawCircle(x, y, r, ring);
                            canvas.drawLine(x - r * 1.5f, y, x + r * 1.5f, y, cross);
                            canvas.drawLine(x, y - r * 1.5f, x, y + r * 1.5f, cross);
                        }
                    }
                    float cx = width / 2f, cy = height * 0.30f;
                    canvas.drawText(progress.title(), cx, cy, title);
                    canvas.drawText(progress.step(Math.min(index + 1, total), total), cx, cy + height * 0.06f, line);
                    String message = progress.message();
                    if (message != null && !message.isEmpty()) canvas.drawText(message, cx, cy + height * 0.12f, line);
                } finally {
                    surface.unlockCanvasAndPost(canvas);
                }
                Thread.sleep(80);
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            if (!stopped) sink.log("CAR_CALIBRATION_SCREEN_STOPPED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public synchronized void stop() {
        if (stopped) return;
        stopped = true;
        if (painter != null) { painter.interrupt(); try { painter.join(500); } catch (InterruptedException ignored) {} }
        if (encoder != null) encoder.stop();
    }
}

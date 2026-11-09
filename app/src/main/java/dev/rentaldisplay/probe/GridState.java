package dev.rentaldisplay.probe;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Normalised geometry shared by the phone view and the generated video. */
final class GridState {
    static final int COLS = 4, ROWS = 3;
    static final double LEFT = .036, RIGHT = .964, TOP = .22, BOTTOM = .90;
    private static final int[] TARGETS = {1, 4, 9, 12, 6};
    static final class Point {
        final double x, y;
        final int pointer;
        final long time;
        Point(double x, double y, int pointer, long time) { this.x = x; this.y = y; this.pointer = pointer; this.time = time; }
    }
    static final class Hit {
        final int cell, expected;
        final boolean correct;
        final double dx, dy;
        Hit(int cell, int expected, boolean correct, double dx, double dy) {
            this.cell = cell; this.expected = expected; this.correct = correct; this.dx = dx; this.dy = dy;
        }
    }
    static final class Snapshot {
        final String phase, status, last;
        final int target, complete, downs, events, videoWidth, videoHeight;
        final List<Point> points;
        final boolean gridVisible, carLive, carTest;
        Snapshot(String phase, String status, String last, int target, int complete, int downs, int events,
                int videoWidth, int videoHeight, List<Point> points,boolean gridVisible, boolean carLive, boolean carTest) {
            this.phase = phase; this.status = status; this.last = last; this.target = target;
            this.complete = complete; this.downs = downs; this.events = events;
            this.videoWidth = videoWidth; this.videoHeight = videoHeight;
            this.points = Collections.unmodifiableList(points);
            this.gridVisible = gridVisible; this.carLive = carLive; this.carTest = carTest;
        }
    }
    private String phase = "PHONE BASELINE", status = "Tap the glowing target", last = "No input events observed";
    private int next, downs, events, videoWidth, videoHeight;
    private final ArrayDeque<Point> points = new ArrayDeque<>();
    private boolean gridVisible = true, carLive, carTest;

    synchronized void reset(boolean car) {
        phase = car ? "CAR TEST - touch ONLY the car screen" : "PHONE BASELINE - touch the phone";
        status = car ? "Connecting to car..." : "Local preview - no USB video";
        last = "No input events observed"; next = downs = events = videoWidth = videoHeight = 0; points.clear(); gridVisible = true;
    }
    synchronized void status(String text) { status = text; }
    synchronized void videoSize(int width, int height) { videoWidth = width; videoHeight = height; }
    synchronized void gridVisible(boolean visible) { gridVisible = visible; }
    /** True while the car's port-8051 touch connection is open; car touch does not depend on the phone UI. */
    synchronized void carLive(boolean live) { carLive = live; }
    /** Car-screen test: the grid is shown only on the car while the phone shows results and logs. */
    synchronized void carTest(boolean active) { carTest = active; }
    /** Phone-window touch: ignored while the phone grid is hidden. */
    synchronized Hit touch(double x, double y, int pointer, boolean primaryDown, boolean countEvent, String summary) {
        return record(x, y, pointer, primaryDown, countEvent, summary, gridVisible);
    }
    /** Car touch received over the network bridge: always recorded, whatever the phone UI is doing. */
    synchronized Hit carTouch(double x, double y, int pointer, boolean primaryDown, boolean countEvent, String summary) {
        return record(x, y, pointer, primaryDown, countEvent, summary, true);
    }
    private Hit record(double x, double y, int pointer, boolean primaryDown, boolean countEvent, String summary, boolean accept) {
        int expected = next < TARGETS.length ? TARGETS[next] : 0;
        int cell = cell(x, y);
        if (!accept) return new Hit(cell,expected,false,expected == 0 ? 0 : x-centerX(expected),
                expected == 0 ? 0 : y-centerY(expected));
        boolean correct = primaryDown && cell == expected && expected != 0;
        if (countEvent) events++;
        if (primaryDown) { downs++; if (correct) next++; }
        last = summary;
        points.addLast(new Point(x, y, pointer, System.currentTimeMillis()));
        while (points.size() > 80) points.removeFirst();
        return new Hit(cell, expected, correct, expected == 0 ? 0 : x - centerX(expected),
                expected == 0 ? 0 : y - centerY(expected));
    }
    synchronized Snapshot snapshot() {
        return new Snapshot(phase, status, last, next < TARGETS.length ? TARGETS[next] : 0,
                next, downs, events, videoWidth, videoHeight, new ArrayList<>(points),gridVisible,carLive,carTest);
    }
    static int cell(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || x < LEFT || x >= RIGHT || y < TOP || y >= BOTTOM) return 0;
        int col = (int) ((x - LEFT) / (RIGHT - LEFT) * COLS);
        int row = (int) ((y - TOP) / (BOTTOM - TOP) * ROWS);
        return row * COLS + col + 1;
    }
    static double centerX(int cell) {
        if (cell < 1 || cell > 12) throw new IllegalArgumentException("Invalid grid cell");
        return LEFT + ((cell - 1) % COLS + .5) * (RIGHT - LEFT) / COLS;
    }
    static double centerY(int cell) {
        if (cell < 1 || cell > 12) throw new IllegalArgumentException("Invalid grid cell");
        return TOP + ((cell - 1) / COLS + .5) * (BOTTOM - TOP) / ROWS;
    }
}

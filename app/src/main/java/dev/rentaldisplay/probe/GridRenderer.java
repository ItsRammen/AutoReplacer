package dev.rentaldisplay.probe;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

final class GridRenderer {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    void draw(Canvas c, int width, int height, GridState.Snapshot state, long frame) {
        c.save();
        // Deliberately fill both screens, without letterboxing: positions use identical normalised coordinates.
        c.scale(width / 1000f, height / 600f);
        c.drawColor(Color.rgb(12, 22, 35));
        text(c, "RENTAL DISPLAY - TOUCH GRID", 28, 26, 36, Color.WHITE);
        text(c, state.gridVisible ? state.phase : "BACKGROUND DISPLAY TEST - phone grid hidden", 20, 26, 66, Color.rgb(105, 234, 204));
        text(c, shortText(state.status, 100), 17, 26, 91, Color.LTGRAY);
        String guide = !state.gridVisible ? "Stream continues. Open the phone grid to collect touch input."
                : state.target == 0 ? "Sequence complete. Try a drag and two fingers."
                : "Tap glowing target " + state.target + "   |   sequence 1, 4, 9, 12, 6";
        text(c, guide, 19, 26, 117, Color.YELLOW);
        for (int cell = 1; cell <= 12; cell++) {
            int col = (cell - 1) % 4, row = (cell - 1) / 4;
            float left = (float) (GridState.LEFT + col * (GridState.RIGHT - GridState.LEFT) / 4) * 1000;
            float top = (float) (GridState.TOP + row * (GridState.BOTTOM - GridState.TOP) / 3) * 600;
            float w = (float) (GridState.RIGHT - GridState.LEFT) * 1000 / 4;
            float h = (float) (GridState.BOTTOM - GridState.TOP) * 600 / 3;
            boolean target = state.gridVisible && cell == state.target;
            p.setStyle(Paint.Style.FILL); p.setColor(target ? Color.rgb(96, 76, 8) : Color.rgb(26, 44, 61));
            c.drawRect(left + 2, top + 2, left + w - 2, top + h - 2, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(target ? 4 : 1);
            p.setColor(target ? Color.YELLOW : Color.rgb(100, 133, 155));
            c.drawRect(left, top, left + w, top + h, p);
            float x = (float) GridState.centerX(cell) * 1000, y = (float) GridState.centerY(cell) * 600;
            c.drawCircle(x, y, 24, p); c.drawLine(x - 34, y, x + 34, y, p); c.drawLine(x, y - 34, x, y + 34, p);
            text(c, Integer.toString(cell), 24, left + 12, top + 30, Color.WHITE);
        }
        long now = System.currentTimeMillis();
        for (GridState.Point point : state.points) {
            if (now - point.time > 4000) continue;
            float x = (float) point.x * 1000, y = (float) point.y * 600;
            p.setStyle(Paint.Style.FILL); p.setColor(point.pointer % 2 == 0 ? Color.MAGENTA : Color.CYAN);
            c.drawCircle(x, y, 9, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); c.drawCircle(x, y, 15, p);
        }
        text(c, "Targets " + state.complete + "/5   downs " + state.downs + "   events " + state.events
                + "   video " + state.videoWidth + "x" + state.videoHeight + "   frame " + frame, 17, 26, 562, Color.WHITE);
        text(c, shortText(state.last, 104), 16, 26, 586, Color.rgb(243, 170, 243));
        c.restore();
    }
    private void text(Canvas c, String text, float size, float x, float y, int color) {
        p.setStyle(Paint.Style.FILL); p.setTextSize(size); p.setColor(color); c.drawText(text, x, y, p);
    }
    private static String shortText(String text, int limit) { return text.length() > limit ? text.substring(0, limit - 3) + "..." : text; }
}

package dev.rentaldisplay.probe;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

final class TouchGridView extends View {
    interface Input { void received(MotionEvent event, int width, int height, boolean generic); }
    private final GridState state;
    private final Input input;
    private final GridRenderer renderer = new GridRenderer();
    TouchGridView(Context context, GridState state, Input input) {
        super(context); this.state = state; this.input = input;
        setFocusable(true); setFocusableInTouchMode(true); setContentDescription("Touch calibration grid");
    }
    @Override protected void onDraw(Canvas canvas) {
        renderer.draw(canvas, getWidth(), getHeight(), state.snapshot(), SystemClock.uptimeMillis() / 200);
        postInvalidateDelayed(100);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        input.received(event, getWidth(), getHeight(), false);
        if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
        invalidate(); return true;
    }
    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        input.received(event, getWidth(), getHeight(), true); invalidate(); return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}

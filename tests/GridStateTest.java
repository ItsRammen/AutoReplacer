package dev.rentaldisplay.probe;

import java.io.IOException;

public final class GridStateTest {
    private static int assertions;
    private static void check(boolean condition, String label) {
        assertions++; if (!condition) throw new AssertionError(label);
    }
    private interface Throwing { void run() throws Exception; }
    private static void rejects(Throwing action, String label) throws Exception {
        try { action.run(); } catch (IOException | IllegalArgumentException expected) { assertions++; return; }
        throw new AssertionError(label);
    }
    public static void main(String[] args) throws Exception {
        // Independent target centres from the documented 4x3 layout.
        double[] xs = {.152,.384,.616,.848}, ys = {1.0/3,.56,.7866666666666666};
        int[][] screens = {{1280,720},{2400,1080},{1920,1080},{800,480}};
        for (int[] screen : screens) for (int r=0;r<3;r++) for (int c=0;c<4;c++) {
            double x = xs[c] * screen[0], y = ys[r] * screen[1];
            check(GridState.cell(x/screen[0],y/screen[1]) == r*4+c+1, "same cell across phone and video aspect ratios");
        }
        check(GridState.cell(0,0)==0 && GridState.cell(1,1)==0, "header and footer are outside targets");
        check(GridState.cell(Double.NaN,.5)==0 && GridState.cell(.5,Double.POSITIVE_INFINITY)==0, "nonfinite input cannot hit target");
        check(GridState.cell(GridState.RIGHT,.5)==0 && GridState.cell(.5,GridState.BOTTOM)==0, "outer right and bottom boundaries excluded");
        check(GridState.cell(GridState.LEFT,GridState.TOP)==1, "top left target boundary included");
        rejects(() -> GridState.centerX(0), "invalid cell zero");
        rejects(() -> GridState.centerY(13), "invalid cell thirteen");
        GridState model = new GridState(); model.reset(true); model.videoSize(1280,720);
        GridState.Hit miss = model.touch(xs[1],ys[0],0,true,true,"wrong cell");
        check(miss.expected==1 && miss.cell==2 && !miss.correct && model.snapshot().complete==0, "misaligned hit retains intended target");
        check(Math.abs(miss.dx-.232)<.000001 && Math.abs(miss.dy)<.000001, "calibration offset is recorded relative to intended target");
        model.touch(xs[0],ys[0],0,false,true,"move over target");
        check(model.snapshot().complete==0, "move cannot complete target");
        int[] sequence = {1,4,9,12,6};
        for (int cell : sequence) {
            GridState.Hit hit = model.touch(xs[(cell-1)%4],ys[(cell-1)/4],0,true,true,"down");
            check(hit.expected==cell && hit.correct, "guided down hits next intended target");
        }
        GridState.Snapshot complete = model.snapshot();
        check(complete.complete==5 && complete.target==0 && complete.downs==6, "complete sequence counts correct and missed downs");
        model.touch(.5,.5,1,false,false,"second pointer");
        check(model.snapshot().downs==6 && model.snapshot().events==complete.events, "extra pointer does not count a new gesture or event");
        check(complete.points.size()+1==model.snapshot().points.size(), "snapshot is isolated from later changes");
        try { complete.points.clear(); throw new AssertionError("mutable snapshot"); }
        catch (UnsupportedOperationException expected) { assertions++; }
        for (int i=0;i<200;i++) model.touch(.3,.4,0,false,true,"drag");
        check(model.snapshot().points.size()==80, "drag history is bounded");
        check(model.snapshot().videoWidth==1280 && model.snapshot().videoHeight==720, "negotiated video geometry retained");
        model.reset(false);
        check(model.snapshot().phase.startsWith("PHONE") && model.snapshot().downs==0 && model.snapshot().points.isEmpty(), "phone baseline reset cannot reuse car evidence");
        DiagnosticPolicy.requireStationary(0,0); assertions++;
        DiagnosticPolicy.requireStationary(1,0); assertions++;
        rejects(() -> DiagnosticPolicy.requireStationary(0,1), "motion stops test");
        rejects(() -> DiagnosticPolicy.requireStationary(1,10), "restriction flag does not hide motion");
        rejects(() -> DiagnosticPolicy.requireStationary(2,0), "unknown restriction flag stops test");
        rejects(() -> DiagnosticPolicy.requireStationary(1,-1), "unknown speed stops test");
        // Car touch keeps working while the phone grid is hidden (app in background or screen off).
        GridState bg = new GridState(); bg.reset(true); bg.gridVisible(false);
        check(!bg.snapshot().carLive, "car touch not live until the bridge reports it");
        bg.touch(xs[0],ys[0],0,true,true,"phone touch while hidden");
        check(bg.snapshot().downs == 0 && bg.snapshot().complete == 0, "phone touch ignored while grid hidden");
        bg.carLive(true);
        GridState.Hit carHit = bg.carTouch(xs[0],ys[0],0,true,true,"car touch while hidden");
        check(carHit.correct && bg.snapshot().complete == 1 && bg.snapshot().downs == 1, "car touch counted while grid hidden");
        check(bg.snapshot().carLive && !bg.snapshot().gridVisible, "snapshot reports car-live and hidden phone grid");
        bg.carLive(false);
        check(!bg.snapshot().carLive, "car-live cleared when the connection closes");
        System.out.println("GridStateTest: " + assertions + " assertions passed");
    }
}

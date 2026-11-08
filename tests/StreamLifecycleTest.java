package dev.rentaldisplay.probe;

public final class StreamLifecycleTest {
    private static int assertions;
    private static void check(boolean condition,String label) {
        assertions++; if (!condition) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        StreamLifecycle stream = new StreamLifecycle();
        check(!stream.active() && stream.phase()==StreamLifecycle.Phase.IDLE,"no stream before user start");
        stream.requestStop(); check(!stream.active(),"stopping idle does not create a session");
        check(stream.requestStart() && stream.phase()==StreamLifecycle.Phase.STARTING,"explicit start reserves the connection");
        check(!stream.requestStart(),"duplicate start cannot replace pending connection");
        check(stream.markRunning() && stream.active(),"service claims reserved connection");
        check(!stream.markRunning(),"duplicate service start cannot create second running transition");
        GridState grid = new GridState(); grid.reset(true); grid.videoSize(1280,720);
        grid.touch(GridState.centerX(1),GridState.centerY(1),0,true,true,"visible input");
        GridState.Snapshot before = grid.snapshot(); grid.gridVisible(false);
        check(stream.phase()==StreamLifecycle.Phase.RUNNING,"hiding grid does not stop service lifecycle");
        GridState.Snapshot hidden = grid.snapshot();
        check(!hidden.gridVisible && before.gridVisible,"visibility snapshot is immutable");
        check(hidden.complete==before.complete && hidden.videoWidth==1280 && hidden.videoHeight==720,"background retains calibration and video dimensions");
        grid.touch(GridState.centerX(4),GridState.centerY(4),0,true,true,"hidden event");
        check(grid.snapshot().complete==hidden.complete && grid.snapshot().downs==hidden.downs
                && grid.snapshot().events==hidden.events,"hidden grid cannot claim touch collection");
        grid.gridVisible(true);
        check(grid.snapshot().target==4 && grid.snapshot().complete==1,"reopening resumes existing target rather than resetting");
        check(grid.touch(GridState.centerX(4),GridState.centerY(4),0,true,true,"visible again").correct,"touch collection resumes with grid visible");
        stream.requestStop();
        check(stream.phase()==StreamLifecycle.Phase.STOPPING && stream.active(),"explicit stop retains reservation until teardown");
        check(!stream.requestStart(),"cannot reconnect over a closing session");
        stream.finished(); check(!stream.active(),"teardown releases reservation");
        check(stream.requestStart(),"fresh connection allowed after teardown");
        stream.requestStop(); check(!stream.markRunning(),"stop during startup cancels late service activation");
        stream.finished(); stream.finished(); check(!stream.active(),"repeated cleanup is idempotent");
        System.out.println("StreamLifecycleTest: "+assertions+" assertions passed");
    }
}

package dev.rentaldisplay.probe;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.atomic.AtomicBoolean;

/** Application-owned diagnostic; does not acquire shell, root, or global-input permission. */
final class PhoneInspection {
    private final AtomicBoolean running = new AtomicBoolean();
    private final Context context;
    private final ProbeLog log;
    PhoneInspection(Context context,ProbeLog log) { this.context=context; this.log=log; }
    boolean start(Runnable changed) {
        if (!running.compareAndSet(false,true)) return false;
        log.beginInspection();
        log.add("INSPECTION_BEGIN: 20-second car-only tap window; keep phone unlocked/grid visible. Local commands run as this app, NOT adb shell. Permission denial or empty output is inconclusive.");
        InputDiagnostics.capture(context,log,"INSPECTION_BEGIN");
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            log.endInspection();
            InputDiagnostics.capture(context,log,"INSPECTION_END");
            log.add("INSPECTION_END: capture window finished; share full log. No raw HID/ADB interception established.");
            running.set(false); changed.run();
        },20000);
        new Thread(() -> {
            // Run the live attempt first so it overlaps the car-only touch window.
            command("KERNEL_EVENTS",new String[]{"/system/bin/getevent","-lt"},15000);
            command("INPUT_DEVICE_NODES",new String[]{"/system/bin/getevent","-lp"},1000);
            command("FRAMEWORK_INPUT",new String[]{"/system/bin/dumpsys","input"},1000);
            changed.run();
        },"phone-input-inspection").start();
        return true;
    }
    private void command(String label,String[] args,long timeout) {
        log.add("LOCAL_INSPECTION_BEGIN kind="+label+" appUid="+android.os.Process.myUid());
        try {
            BoundedCommand.Result result=BoundedCommand.run(args,timeout,24*1024);
            log.add("LOCAL_INSPECTION_RESULT kind="+label+" exit="+result.exit+" deadlineReached="+result.timedOut
                    +" truncated="+result.truncated+" output follows (permission denied/empty is NOT proof of no touch):\n"+result.output);
        } catch (Exception e) {
            log.add("LOCAL_INSPECTION_UNAVAILABLE kind="+label+" error="+e.getClass().getSimpleName()+": "+e.getMessage());
        }
    }
}

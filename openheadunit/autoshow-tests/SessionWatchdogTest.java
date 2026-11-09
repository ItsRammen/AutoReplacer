package com.andrerinas.openheadunit.autoshow;

public final class SessionWatchdogTest {
    private static int assertions;
    private static void check(boolean value, String label) {
        assertions++; if (!value) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        check(SessionWatchdog.failure(15000,0,false,0,0,0)==null,"initial negotiation gets full deadline");
        check(SessionWatchdog.failure(15001,0,false,0,0,0)!=null,"missing VIDEO_READY still stops");
        check(SessionWatchdog.failure(65000,0,true,5001,0,0)==null,"encoder startup has its own deadline");
        check(SessionWatchdog.failure(65002,0,true,5001,0,0)!=null,"encoder never producing a buffer still stops");
        check(SessionWatchdog.failure(3600000,0,true,3000,18000,3599999)==null,"hour of event-driven receive silence must not end advancing video");
        check(SessionWatchdog.failure(100000,0,true,3000,80,84999)!=null,"stalled video output still stops");
        // Reproduce the user's final run: opened 14:23:40, ready at +3s,
        // last incoming safety report at +4.7s, video still advancing at +20s.
        check(SessionWatchdog.failure(20002,0,true,3131,84,20000)==null,"regression: healthy background run survives old receive timeout");
        for (int restriction : new int[]{0,1}) {
            try { DiagnosticPolicy.requireStationary(restriction,1); throw new AssertionError("motion accepted"); }
            catch (java.io.IOException expected) { assertions++; }
        }
        check(DiagnosticPolicy.safetyMaskReplyAllowed(0,0),"unrestricted stationary state gets the Auto+ Show reply");
        check(!DiagnosticPolicy.safetyMaskReplyAllowed(1,0) && !DiagnosticPolicy.safetyMaskReplyAllowed(0,5) && !DiagnosticPolicy.safetyMaskReplyAllowed(2,0),"no reply when restricted, moving or unknown");
        System.out.println("SessionWatchdogTest: "+assertions+" assertions passed");
    }
}

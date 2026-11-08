package dev.rentaldisplay.probe;

public final class InputPolicyTest {
    private static int assertions;
    private static void check(boolean value, String label) {
        assertions++; if (!value) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        check(InputOrigin.classify(false,false,false).equals(InputOrigin.UNKNOWN_DEVICE),"null device is labelled, not dropped");
        check(InputOrigin.classify(false,true,true).equals(InputOrigin.UNKNOWN_DEVICE),"unknown device wins over flags");
        check(InputOrigin.classify(true,true,false).equals(InputOrigin.EXTERNAL),"external device");
        check(InputOrigin.classify(true,false,false).equals(InputOrigin.BUILT_IN),"built-in panel");
        check(InputOrigin.classify(true,false,true).equals(InputOrigin.VIRTUAL),"virtual device");
        check(InputOrigin.classify(true,true,true).equals(InputOrigin.VIRTUAL),"virtual device is not reported as plain external");
        for (String o : new String[]{InputOrigin.EXTERNAL,InputOrigin.UNKNOWN_DEVICE,InputOrigin.VIRTUAL,InputOrigin.BUILT_IN})
            check(!InputOrigin.meaning(o).isEmpty(),"every label has an explanation");
        check(InputOrigin.meaning(InputOrigin.BUILT_IN).contains("not distinguishable"),"built-in label admits ambiguity");

        long t = 100000;
        check(!AdbRequestPolicy.due(false,true,false,1,t+5000,t),"never repeats unless user selected it");
        check(!AdbRequestPolicy.due(true,false,false,1,t+5000,t),"does not repeat before first video buffer");
        check(!AdbRequestPolicy.due(true,true,true,1,t+5000,t),"stops once car reports ADB available");
        check(!AdbRequestPolicy.due(true,true,false,0,t+5000,t),"initial request belongs to the handshake");
        check(!AdbRequestPolicy.due(true,true,false,1,t+1999,t),"waits the two-second interval");
        check(AdbRequestPolicy.due(true,true,false,1,t+2000,t),"repeats after two seconds while car reports no ADB");
        check(AdbRequestPolicy.due(true,true,false,4,t+2000,t),"fifth request is allowed");
        check(!AdbRequestPolicy.due(true,true,false,AdbRequestPolicy.MAX_REQUESTS,t+60000,t),"bounded: no endless repetition");

        check(AdbRequestPolicy.afterVideoReady(1) && !AdbRequestPolicy.afterVideoReady(0) && !AdbRequestPolicy.afterVideoReady(2),"VIDEO_READY flag is exactly 1");
        check(AdbRequestPolicy.afterStatus(false,1),"status 1 sets flag");
        check(!AdbRequestPolicy.afterStatus(false,0) && !AdbRequestPolicy.afterStatus(false,2),"other status does not set flag");
        check(AdbRequestPolicy.afterStatus(true,0),"non-1 status does not clear an established flag (as in inspected client)");
        System.out.println("InputPolicyTest: "+assertions+" assertions passed");
    }
}

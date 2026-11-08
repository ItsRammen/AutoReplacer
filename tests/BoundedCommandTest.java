package dev.rentaldisplay.probe;

public final class BoundedCommandTest {
    private static int assertions;
    private static void check(boolean value,String label) {
        assertions++; if (!value) throw new AssertionError(label);
    }
    private static String[] child(String mode) {
        return new String[]{System.getProperty("java.home")+"/bin/java","-cp",System.getProperty("java.class.path"),BoundedCommandTest.class.getName(),mode};
    }
    public static void main(String[] args) throws Exception {
        if (args.length>0) {
            if (args[0].equals("sleep")) { Thread.sleep(10000); return; }
            if (args[0].equals("flood")) { for(int i=0;i<10000;i++) System.out.print("0123456789"); return; }
            System.err.println("Permission denied (test fixture)"); System.exit(7); return;
        }
        BoundedCommand.Result denied=BoundedCommand.run(child("denied"),2000,1024);
        check(denied.exit==7 && !denied.timedOut,"nonzero command preserved");
        check(denied.output.contains("Permission denied"),"stderr retained for diagnosis");
        BoundedCommand.Result flood=BoundedCommand.run(child("flood"),2000,512);
        check(flood.exit==0 && !flood.timedOut,"large output drained without pipe deadlock");
        check(flood.truncated && flood.output.length()==512,"capture bounded");
        long began=System.nanoTime();
        BoundedCommand.Result timeout=BoundedCommand.run(child("sleep"),150,512);
        check(timeout.timedOut && timeout.exit==-1,"live command deadline distinguished from normal exit");
        check((System.nanoTime()-began)/1000000<2500,"blocked process terminated promptly");
        System.out.println("BoundedCommandTest: "+assertions+" assertions passed");
    }
}

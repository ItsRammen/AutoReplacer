package dev.rentaldisplay.probe;

public final class TsProtocolTest {
    private static int assertions;
    private static void check(boolean value, String label) {
        assertions++; if (!value) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        TsProtocol.Command init = TsProtocol.parse("a=init sw=1080 sh=2340 cw=1280 ch=720");
        check(init.kind == TsProtocol.Kind.INIT && init.sw == 1080 && init.sh == 2340 && init.cw == 1280 && init.ch == 720, "init parsed");
        TsProtocol.Command ts = TsProtocol.parse("a=ts x=640 y=360 b=0");
        check(ts.kind == TsProtocol.Kind.TOUCH && ts.x == 640 && ts.y == 360 && ts.b == 0, "a=ts touch parsed");
        TsProtocol.Command bare = TsProtocol.parse("x=10 y=20 b=1");
        check(bare.kind == TsProtocol.Kind.TOUCH && bare.x == 10 && bare.y == 20 && bare.b == 1, "bare touch parsed like the helper");
        check(TsProtocol.parse("  a=ts   x=1 y=2 b=2  ").kind == TsProtocol.Kind.TOUCH, "extra spaces tolerated");
        check(TsProtocol.parse("a=init sw=1 sh=2 cw=3").kind == TsProtocol.Kind.UNKNOWN, "incomplete init rejected");
        check(TsProtocol.parse("a=ts x=1 y=abc b=0").kind == TsProtocol.Kind.UNKNOWN, "non-numeric rejected without exception");
        check(TsProtocol.parse("a=other x=1 y=2 b=0").kind == TsProtocol.Kind.UNKNOWN, "unknown command rejected");
        check(TsProtocol.parse("").kind == TsProtocol.Kind.UNKNOWN && TsProtocol.parse(null).kind == TsProtocol.Kind.UNKNOWN, "empty and null rejected");
        check(TsProtocol.parse("x= y=2 b=0").kind == TsProtocol.Kind.UNKNOWN, "empty value rejected");
        check(Math.abs(TsProtocol.normal(640,1280) - 0.5) < 1e-9 && Double.isNaN(TsProtocol.normal(5,0)), "normalisation");
        check(TsProtocol.action(0).equals("DOWN") && TsProtocol.action(1).equals("UP") && TsProtocol.action(2).equals("MOVE") && TsProtocol.action(9).equals("ACTION_9"), "action names");
        check(TsProtocol.PORT == 8051 && TsProtocol.BANNER.equals("USBLINKTS v1.0"), "constants match helper");
        System.out.println("TsProtocolTest: "+assertions+" assertions passed");
    }
}

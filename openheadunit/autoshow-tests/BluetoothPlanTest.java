package com.andrerinas.openheadunit.autoshow;

public final class BluetoothPlanTest {
    private static int assertions;
    private static void check(boolean value, String label) {
        assertions++; if (!value) throw new AssertionError(label);
    }
    private static byte[] payload(String address) {
        byte[] p = new byte[61];
        byte[] a = address.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(a, 0, p, 44, Math.min(a.length, 17));
        return p;
    }
    public static void main(String[] args) {
        check(BluetoothPlan.isValidAddress("A4:C1:38:0F:2B:9D") && BluetoothPlan.isValidAddress("a4:c1:38:0f:2b:9d"), "valid addresses");
        check(!BluetoothPlan.isValidAddress(null) && !BluetoothPlan.isValidAddress("")
                && !BluetoothPlan.isValidAddress("0011001100110011") && !BluetoothPlan.isValidAddress("A4:C1:38:0F:2B")
                && !BluetoothPlan.isValidAddress("A4-C1-38-0F-2B-9D") && !BluetoothPlan.isValidAddress("G4:C1:38:0F:2B:9D"), "invalid addresses rejected");
        check("A4:C1:38:0F:2B:9D".equals(Wire.carBluetoothAddress(payload("a4:c1:38:0f:2b:9d"))), "address read from byte 44 and upper-cased");
        check(Wire.carBluetoothAddress(payload("0011001100110011")) == null, "the client's placeholder is not an address");
        check(Wire.carBluetoothAddress(new byte[40]) == null && Wire.carBluetoothAddress(null) == null, "short or missing payload");
        check(Wire.carBluetoothAddress(new byte[61]) == null, "all-zero field is not an address");
        check("**:**:**:**:2B:9D".equals(BluetoothPlan.mask("a4:c1:38:0f:2b:9d")) && "(none)".equals(BluetoothPlan.mask("nope")), "log masking keeps only the last two bytes");
        check(BluetoothPlan.decide(false, true, true, true, true) == BluetoothPlan.Action.NO_ADDRESS, "no address");
        check(BluetoothPlan.decide(true, true, false, false, false) == BluetoothPlan.Action.NO_BLUETOOTH, "no adapter");
        check(BluetoothPlan.decide(true, false, true, true, true) == BluetoothPlan.Action.NEEDS_PERMISSION, "permission missing");
        check(BluetoothPlan.decide(true, true, true, false, true) == BluetoothPlan.Action.TURN_ON_BLUETOOTH, "bluetooth off");
        check(BluetoothPlan.decide(true, true, true, true, false) == BluetoothPlan.Action.PAIR, "not paired yet");
        check(BluetoothPlan.decide(true, true, true, true, true) == BluetoothPlan.Action.CHECK_AUDIO, "paired");
        check(BluetoothPlan.audio(true, true) == BluetoothPlan.Audio.MEDIA_AND_CALLS && BluetoothPlan.audio(true, false) == BluetoothPlan.Audio.MEDIA_ONLY
                && BluetoothPlan.audio(false, true) == BluetoothPlan.Audio.CALLS_ONLY && BluetoothPlan.audio(false, false) == BluetoothPlan.Audio.NONE, "audio profiles");
        System.out.println("BluetoothPlanTest: " + assertions + " assertions passed");
    }
}

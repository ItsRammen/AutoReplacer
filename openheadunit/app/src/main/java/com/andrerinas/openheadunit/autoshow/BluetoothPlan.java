package com.andrerinas.openheadunit.autoshow;

import java.util.Locale;

/** Decisions about the car's Bluetooth pairing, kept free of Android classes so they can be tested. */
public final class BluetoothPlan {
    public enum Action { NO_ADDRESS, NEEDS_PERMISSION, NO_BLUETOOTH, TURN_ON_BLUETOOTH, PAIR, CHECK_AUDIO }

    private BluetoothPlan() {}

    public static boolean isValidAddress(String text) {
        return text != null && text.matches("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$");
    }

    /** First problem that needs fixing, or what to do next when the phone is ready. */
    public static Action decide(boolean addressValid, boolean hasPermission, boolean hasAdapter,
            boolean enabled, boolean bonded) {
        if (!addressValid) return Action.NO_ADDRESS;
        if (!hasAdapter) return Action.NO_BLUETOOTH;
        if (!hasPermission) return Action.NEEDS_PERMISSION;
        if (!enabled) return Action.TURN_ON_BLUETOOTH;
        return bonded ? Action.CHECK_AUDIO : Action.PAIR;
    }

    /** Hides all but the last two bytes, so logs do not carry the car's full identifier. */
    public static String mask(String address) {
        if (!isValidAddress(address)) return "(none)";
        String a = address.toUpperCase(Locale.US);
        return "**:**:**:**:" + a.substring(12);
    }

    public enum Audio { MEDIA_AND_CALLS, MEDIA_ONLY, CALLS_ONLY, NONE }

    /** Which audio profiles of the paired car are connected. The words for each live in the string resources. */
    public static Audio audio(boolean mediaConnected, boolean callsConnected) {
        if (mediaConnected && callsConnected) return Audio.MEDIA_AND_CALLS;
        if (mediaConnected) return Audio.MEDIA_ONLY;
        if (callsConnected) return Audio.CALLS_ONLY;
        return Audio.NONE;
    }
}

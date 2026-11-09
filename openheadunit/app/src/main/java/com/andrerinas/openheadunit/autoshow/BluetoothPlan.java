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

    public static String describe(Action action) {
        switch (action) {
            case NO_ADDRESS: return "The car did not give a Bluetooth address";
            case NO_BLUETOOTH: return "This phone has no Bluetooth";
            case NEEDS_PERMISSION: return "Allow the Nearby devices permission for car Bluetooth";
            case TURN_ON_BLUETOOTH: return "Turn on Bluetooth for car sound";
            case PAIR: return "Pairing with the car: confirm on the phone and the car";
            default: return "Paired with the car; waiting for car audio";
        }
    }

    /** Text for the audio check once the car is paired: which profiles are connected. */
    public static String audioStatus(boolean mediaConnected, boolean callsConnected) {
        if (mediaConnected && callsConnected) return "Car audio connected (media and calls)";
        if (mediaConnected) return "Car audio connected (media)";
        if (callsConnected) return "Car connected for calls only; media audio not connected";
        return "Paired, but the car has not connected audio. Choose the car in the phone's Bluetooth settings";
    }
}

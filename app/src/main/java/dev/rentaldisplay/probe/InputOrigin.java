package dev.rentaldisplay.probe;

/**
 * Labels the Android device attributed to a MotionEvent. This is attribution, not proof of physical origin:
 * events injected through shell/API paths are commonly attributed to an existing touchscreen or to no device
 * at all, so BUILT_IN cannot separate a phone-panel tap from a car tap injected over ADB.
 */
final class InputOrigin {
    static final String UNKNOWN_DEVICE = "UNKNOWN_DEVICE";
    static final String EXTERNAL = "EXTERNAL";
    static final String VIRTUAL = "VIRTUAL";
    static final String BUILT_IN = "BUILT_IN";

    static String classify(boolean deviceKnown, boolean external, boolean virtual) {
        if (!deviceKnown) return UNKNOWN_DEVICE;
        if (virtual) return VIRTUAL;
        return external ? EXTERNAL : BUILT_IN;
    }

    /** Explains what the label can and cannot establish; included in logs so exports are self-describing. */
    static String meaning(String origin) {
        switch (origin) {
            case EXTERNAL: return "attached input device; HID-style delivery";
            case UNKNOWN_DEVICE: return "no device attribution; consistent with API/shell injection, not proof";
            case VIRTUAL: return "virtual device; injected or software input";
            default: return "phone panel OR injection attributed to it; origin not distinguishable";
        }
    }
}

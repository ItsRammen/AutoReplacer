package dev.rentaldisplay.probe;

/**
 * Weak hint about whether a contact carries real panel data. Shell/API injection (for example `input tap`)
 * typically reports pressure 1.0, size 1.0 and no touch-major, and simple HID touchscreens can look the same.
 * A SYNTHETIC_LIKE label therefore means "no panel-like contact data", not "injected".
 */
final class ContactProfile {
    static final String PANEL_LIKE = "PANEL_LIKE";
    static final String SYNTHETIC_LIKE = "SYNTHETIC_LIKE";
    static final String INCONCLUSIVE = "INCONCLUSIVE";
    static final String NOT_APPLICABLE = "N/A";
    private static final float ONE = 0.999f, ZERO = 0.001f;

    static String classify(boolean contactActive, float pressure, float size, float touchMajor) {
        if (!contactActive || Float.isNaN(pressure) || Float.isNaN(size) || Float.isNaN(touchMajor)) return NOT_APPLICABLE;
        if (touchMajor > 0 || (size > ZERO && size < ONE) || (pressure > ZERO && pressure < ONE)) return PANEL_LIKE;
        if (pressure >= ONE && touchMajor == 0 && (size <= ZERO || size >= ONE)) return SYNTHETIC_LIKE;
        return INCONCLUSIVE;
    }
}

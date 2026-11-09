package com.andrerinas.openheadunit.autoshow;

public final class TouchAreaTest {
    private static int assertions;
    private static void check(boolean value, String label) { assertions++; if (!value) throw new AssertionError(label); }
    public static void main(String[] args) {
        check(TouchArea.extent(720, 682, 0, true) == 720, "touches inside the picture keep the declared size");
        check(TouchArea.extent(720, 727, 0, true) == 720, "a few pixels past is rounding noise");
        check(TouchArea.extent(720, 749, 0, true) == 800, "749 on a 720 picture is a 800-tall panel");
        check(TouchArea.extent(720, 795, 0, true) == 800, "795 rounds up to 800");
        check(TouchArea.extent(720, 800, 0, true) == 800, "800 is 800");
        check(TouchArea.extent(720, 850, 0, true) == 900, "850 rounds to 900");
        check(TouchArea.extent(720, 1500, 0, true) == 1501, "beyond every known size uses the observed maximum");
        check(TouchArea.extent(1280, 1242, 0, false) == 1280, "width inside the picture");
        check(TouchArea.extent(1280, 1350, 0, false) == 1366, "a wider panel");
        check(TouchArea.extent(720, 795, 720, true) == 720, "the user's choice wins");
        check(TouchArea.extent(0, 99, 0, true) == 100, "no declared size falls back to what was seen");
        // The bottom row of the car, as in the log: icons at y 740-795 must land inside a 720 picture.
        check(TouchArea.map(749, 800, 720) == 674, "749 maps to 674 (icon centre)");
        check(TouchArea.map(795, 800, 720) == 715, "795 maps to 715");
        check(TouchArea.map(800, 800, 720) == 719, "the last row is the last row");
        check(TouchArea.map(0, 800, 720) == 0, "the first row");
        check(TouchArea.map(-5, 800, 720) == 0 && TouchArea.map(5000, 800, 720) == 719, "out-of-range values are clamped");
        check(TouchArea.map(100, 0, 720) == 0, "unknown extent does not divide by zero");
        System.out.println("TouchAreaTest OK (" + assertions + " assertions)");
    }
}

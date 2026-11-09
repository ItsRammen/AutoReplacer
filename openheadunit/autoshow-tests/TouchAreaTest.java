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
        // Calibration: measured touches as fractions of the raw extent against the picture fractions drawn.
        double[] t = {0.08, 0.5, 0.92, 0.08, 0.92, 0.5, 0.5};
        double[] stretched = new double[t.length];
        for (int i = 0; i < t.length; i++) stretched[i] = t[i] * 1.111111; // the picture fills a panel 11% taller than the declared size
        double[] f1 = TouchArea.fit(t, stretched);
        check(f1 != null && Math.abs(f1[0] - 1.1111) < 0.001 && Math.abs(f1[1]) < 0.002, "a stretched panel fits a=1.11 b=0");
        double[] shifted = new double[t.length];
        for (int i = 0; i < t.length; i++) shifted[i] = t[i] * 0.9 + 0.05; // letterboxed: picture occupies 5%..95%
        double[] f2 = TouchArea.fit(t, shifted);
        check(f2 != null && Math.abs(f2[0] - 0.9) < 0.001 && Math.abs(f2[1] - 0.05) < 0.001, "a letterboxed panel fits a=0.9 b=0.05");
        check(TouchArea.fit(new double[] {0.5, 0.5, 0.5, 0.5}, new double[] {0.5, 0.5, 0.5, 0.5}) == null, "no spread, no fit");
        check(TouchArea.fit(new double[] {0.1, 0.9, 0.1, 0.9}, new double[] {0.9, 0.1, 0.9, 0.1}) == null, "an inverted axis is refused");
        check(TouchArea.fit(new double[] {0.1, 0.5}, new double[] {0.1, 0.5}) == null, "too few points");
        // Unmapping the fitted touches lands on the targets.
        int bottom = TouchArea.unmap(736, f1[0], f1[1], 720, 720); // raw 736 on a declared 720: the bottom row of a 800-tall panel at 0.92
        check(bottom >= 659 && bottom <= 665, "0.92 of the picture maps near 662");
        check(TouchArea.unmap(0, 1.0, 0.0, 720, 720) == 0 && TouchArea.unmap(99999, 1.0, 0.0, 720, 720) == 719, "unmap clamps");
        System.out.println("TouchAreaTest OK (" + assertions + " assertions)");
    }
}

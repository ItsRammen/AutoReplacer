package com.andrerinas.openheadunit.autoshow;

/**
 * The size of the car's touch coordinate space along one axis. The head unit's "a=init" line gives
 * the size of the picture (1280x720), but the touches can run past it: on one Auto+ Show car the
 * panel reports y up to about 795 for a 720-pixel-tall picture, so everything near the bottom was
 * squashed onto the last row. The extent is taken from the largest coordinate seen, rounded up to
 * a common panel size, unless the user chose one.
 */
public final class TouchArea {
    private TouchArea() {}

    private static final int[] HEIGHTS = {800, 900, 960, 1024, 1080};
    private static final int[] WIDTHS = {1366, 1440, 1600, 1920};
    /** A touch this far past the declared size is a bigger panel, not rounding noise. */
    private static final int TOLERANCE = 8;

    public static int extent(int declared, int observedMax, int override, boolean vertical) {
        if (override > 0) return override;
        if (declared <= 0) return Math.max(observedMax + 1, 1);
        if (observedMax <= declared + TOLERANCE) return declared;
        for (int candidate : vertical ? HEIGHTS : WIDTHS) {
            if (candidate >= observedMax && candidate > declared) return candidate;
        }
        return observedMax + 1;
    }

    /**
     * Least-squares fit of measured = a * target + b, both in 0..1 picture fractions. Returns {a, b},
     * or null when the points do not describe a plausible screen (too few, too close together,
     * a scale far from 1, or a large offset).
     */
    public static double[] fit(double[] target, double[] measured) {
        int n = target.length;
        if (n < 4 || measured.length != n) return null;
        double st = 0, sm = 0;
        for (int i = 0; i < n; i++) { st += target[i]; sm += measured[i]; }
        double mt = st / n, mm = sm / n, num = 0, den = 0;
        for (int i = 0; i < n; i++) { num += (target[i] - mt) * (measured[i] - mm); den += (target[i] - mt) * (target[i] - mt); }
        if (den < 0.05) return null;
        double a = num / den, b = mm - a * mt;
        if (a < 0.6 || a > 1.8 || Math.abs(b) > 0.35) return null;
        return new double[] {a, b};
    }

    /** Picture pixel for a raw coordinate, given the fit; the picture is {@code pictureSize} pixels along the axis. */
    public static int unmap(int raw, double a, double b, int rawExtent, int pictureSize) {
        if (a <= 0 || rawExtent <= 0 || pictureSize <= 0) return 0;
        double fraction = (raw / (double) rawExtent - b) / a;
        long pixel = Math.round(fraction * pictureSize);
        return (int) Math.max(0, Math.min(pictureSize - 1, pixel));
    }

    /** Scales a car coordinate into a picture of the given size. */
    public static int map(int value, int extent, int pictureSize) {
        if (extent <= 0 || pictureSize <= 0) return 0;
        long scaled = (long) value * pictureSize / extent;
        return (int) Math.max(0, Math.min(pictureSize - 1, scaled));
    }
}

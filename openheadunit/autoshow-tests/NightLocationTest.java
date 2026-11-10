package com.andrerinas.openheadunit.autoshow;

public final class NightLocationTest {
    private static int assertions;
    private static void check(boolean value, String label) { assertions++; if (!value) throw new AssertionError(label); }
    public static void main(String[] args) {
        check(NightLocation.longitudeForOffsetMillis(8 * 3600000L) == 120.0, "Taiwan standard time is 120 degrees east");
        check(NightLocation.longitudeForOffsetMillis(0) == 0.0, "UTC");
        check(NightLocation.longitudeForOffsetMillis(-5 * 3600000L) == -75.0, "US Eastern standard time");
        check(NightLocation.longitudeForOffsetMillis(5 * 3600000L + 1800000L) == 82.5, "India, a half-hour zone");
        check(NightLocation.longitudeForOffsetMillis(14 * 3600000L) <= 180.0, "stays on the globe");
        check(NightLocation.longitudeForOffsetMillis(20 * 3600000L) == 180.0, "clamped");
        check(NightLocation.plausible(25.03, 121.56), "Taipei");
        check(!NightLocation.plausible(0.0, 0.0), "null island is a missing fix");
        check(!NightLocation.plausible(91.0, 10.0) && !NightLocation.plausible(10.0, 181.0), "off the globe");
        check(!NightLocation.plausible(Double.NaN, 10.0), "NaN");
        check("5 min".equals(NightLocation.age(5 * 60000L)), "minutes");
        check("3 h".equals(NightLocation.age(3 * 3600000L)), "hours");
        check("12 days".equals(NightLocation.age(12 * 24 * 3600000L)), "days");
        check("0 min".equals(NightLocation.age(-5)), "negative age");
        System.out.println("NightLocationTest OK (" + assertions + " assertions)");
    }
}

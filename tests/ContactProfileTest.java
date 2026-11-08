package dev.rentaldisplay.probe;

public final class ContactProfileTest {
    private static int assertions;
    private static void check(boolean value, String label) {
        assertions++; if (!value) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        check(ContactProfile.classify(true,1f,1f,0f).equals(ContactProfile.SYNTHETIC_LIKE),"pressure 1, size 1, no major looks synthetic");
        check(ContactProfile.classify(true,1f,0f,0f).equals(ContactProfile.SYNTHETIC_LIKE),"simple HID tip switch with no size also lacks panel data");
        check(ContactProfile.classify(true,.42f,.07f,12f).equals(ContactProfile.PANEL_LIKE),"varying pressure, size and major looks like a panel");
        check(ContactProfile.classify(true,1f,1f,9f).equals(ContactProfile.PANEL_LIKE),"any touch-major value is panel-like");
        check(ContactProfile.classify(true,.5f,1f,0f).equals(ContactProfile.PANEL_LIKE),"fractional pressure is panel-like");
        check(ContactProfile.classify(true,0f,0f,0f).equals(ContactProfile.INCONCLUSIVE),"zero pressure with no data is inconclusive");
        check(ContactProfile.classify(false,1f,1f,0f).equals(ContactProfile.NOT_APPLICABLE),"release and hover events are not classified");
        check(ContactProfile.classify(true,Float.NaN,1f,0f).equals(ContactProfile.NOT_APPLICABLE),"NaN input is not classified");

        ContactTally tally = new ContactTally();
        check(tally.summary().equals("none") && tally.total()==0,"empty tally");
        tally.add("BUILT_IN","PANEL_LIKE"); tally.add("BUILT_IN","PANEL_LIKE"); tally.add("UNKNOWN_DEVICE","SYNTHETIC_LIKE");
        check(tally.total()==3,"total counts every down");
        check(tally.summary().equals("BUILT_IN/PANEL_LIKE=2, UNKNOWN_DEVICE/SYNTHETIC_LIKE=1"),"summary is stable and sorted");
        tally.reset();
        check(tally.total()==0 && tally.summary().equals("none"),"reset clears the tally");
        System.out.println("ContactProfileTest: "+assertions+" assertions passed");
    }
}

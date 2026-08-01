package CampusConnect.domain;

/**
 * Top-level grouping for interests.
 * <p>
 * Colours are stored as plain RGB ints rather than {@code java.awt.Color} so that
 * the domain layer stays free of any UI dependency — the UI converts on the way out.
 */
public enum Category {
    SPORTS       ("Sports",        0xE74C3C),
    MUSIC        ("Music",         0x9B59B6),
    GAMING       ("Gaming",        0x3498DB),
    ACADEMICS    ("Academics",     0xF1C40F),
    TECH         ("Tech",          0x1ABC9C),
    ARTS         ("Arts",          0xE67E22),
    FOOD         ("Food",          0xD35400),
    FILM_TV      ("Film & TV",     0x8E44AD),
    FITNESS      ("Fitness",       0x27AE60),
    VOLUNTEERING ("Volunteering",  0x16A085),
    OUTDOORS     ("Outdoors",      0x2ECC71),
    OTHER        ("Other",         0x7F8C8D);

    private final String label;
    private final int rgb;

    Category(String label, int rgb) {
        this.label = label;
        this.rgb = rgb;
    }

    public String getLabel() { return label; }

    /** Packed 0xRRGGBB. The UI wraps this in a Color. */
    public int getRgb() { return rgb; }

    @Override
    public String toString() { return label; }
}

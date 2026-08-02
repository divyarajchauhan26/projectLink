package CampusConnect.ui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Font;

/**
 * One place that decides what the app looks like.
 * <p>
 * Colours were previously written inline at roughly forty call sites — {@code new
 * Color(43,43,43)} in one panel, {@code new Color(45,45,45)} in the next — so nothing
 * quite lined up and changing the look meant hunting through every file. Everything now
 * comes from here.
 * <p>
 * The palette is a cool near-black with a single blue-violet accent. The accent is
 * reserved for one thing: <b>anything the matching engine produced</b>. Suggestions,
 * ghost edges, similarity, explanations. That consistency is what makes the intelligence
 * read as a feature rather than as more menu items — the eye learns that violet means
 * "the app worked this out for you".
 */
public final class Theme {

    private Theme() {}

    // ================= surfaces =================

    /** The canvas and window background. */
    public static final Color BG = new Color(0x14161A);
    /** Side panels and the stats rail. */
    public static final Color PANEL = new Color(0x1B1E24);
    /** Cards and inputs that sit on top of a panel. */
    public static final Color ELEVATED = new Color(0x23272F);
    /** Hover / pressed state for elevated things. */
    public static final Color ELEVATED_HOVER = new Color(0x2C313B);
    /** Hairlines and dividers. */
    public static final Color BORDER = new Color(0x2E3440);

    // ================= text =================

    public static final Color TEXT = new Color(0xE6E9EF);
    public static final Color TEXT_DIM = new Color(0x9AA3B2);
    public static final Color TEXT_FAINT = new Color(0x6B7280);

    // ================= accent =================

    /** Reserved for output of the matching engine. */
    public static final Color ACCENT = new Color(0x8B9EF7);
    public static final Color ACCENT_DEEP = new Color(0x6172D6);
    /** Accent at low opacity, for glows and fills behind text. */
    public static final Color ACCENT_SOFT = new Color(0x8B, 0x9E, 0xF7, 40);

    /** "You". Warm, so the reference point never competes with the accent. */
    public static final Color YOU = new Color(0xF2C14E);

    public static final Color SUCCESS = new Color(0x4ADE80);
    public static final Color WARNING = new Color(0xFBBF24);
    public static final Color DANGER = new Color(0xF87171);

    // ================= graph =================

    /** Ordinary connection. Deliberately dim so nodes carry the picture. */
    public static final Color EDGE = new Color(0x39404E);
    /** A connection the app is proposing. */
    public static final Color EDGE_GHOST = new Color(0x8B9EF7);
    /** A connection whose loss would split the network. */
    public static final Color EDGE_FRAGILE = new Color(0xF87171);
    /** A highlighted route. */
    public static final Color PATH = new Color(0x4ADE80);
    /** A node with no other state. */
    public static final Color NODE = new Color(0x4C5668);

    // ================= type =================

    public static final String FAMILY = "Segoe UI";

    public static Font title(int size)  { return new Font(FAMILY, Font.BOLD, size); }
    public static Font body(int size)   { return new Font(FAMILY, Font.PLAIN, size); }
    public static Font mono(int size)   { return new Font("Consolas", Font.PLAIN, size); }

    // ================= helpers =================

    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    /** Blend two colours; {@code t} of 0 gives {@code a}, 1 gives {@code b}. */
    public static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
                (int) (a.getRed()   * (1 - t) + b.getRed()   * t),
                (int) (a.getGreen() * (1 - t) + b.getGreen() * t),
                (int) (a.getBlue()  * (1 - t) + b.getBlue()  * t));
    }

    public static Border padding(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    /** A hairline on one edge only — cleaner than boxing every card. */
    public static Border divider(int top, int left, int bottom, int right) {
        return BorderFactory.createMatteBorder(top, left, bottom, right, BORDER);
    }

    public static void style(JComponent c) {
        c.setBackground(PANEL);
        c.setForeground(TEXT);
        c.setFont(body(12));
    }
}

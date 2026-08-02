package CampusConnect.ui;

import CampusConnect.domain.Intent;
import CampusConnect.domain.InterestTag;
import CampusConnect.domain.Person;
import CampusConnect.service.NetworkService;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.Set;

/**
 * A person's profile, rendered as a card.
 * <p>
 * Replaces the plain-text dump the side panel used to show ("Name: … Degree: 4 …"), which
 * read like a debugger watch window rather than a person.
 * <p>
 * Built as HTML in a {@link JEditorPane} rather than nested Swing components on purpose:
 * interest chips need to wrap across lines and be tinted per category, and Swing has no
 * wrapping flow layout without writing one. Swing's HTML renderer does it in a few lines
 * of markup, and the card is read-only so nothing is lost by not using real components.
 */
public class ProfileCard extends JPanel {

    private final JEditorPane pane = new JEditorPane();
    private final NetworkService service;

    public ProfileCard(NetworkService service) {
        this.service = service;
        setLayout(new BorderLayout());
        setBackground(Theme.PANEL);

        pane.setContentType("text/html");
        pane.setEditable(false);
        pane.setBackground(Theme.PANEL);
        pane.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        showEmpty("Click a student to see their profile.");
    }

    public void showEmpty(String message) {
        pane.setText(wrap("<div class='muted' style='padding:16px'>" + escape(message) + "</div>"));
        pane.setCaretPosition(0);
    }

    /**
     * @param person   whose profile to show
     * @param relation optional note about how they relate to the current user — the
     *                 recommendation explanation, typically. Null to omit.
     */
    public void showPerson(Person person, String relation) {
        if (person == null) { showEmpty("Nobody selected."); return; }

        StringBuilder html = new StringBuilder();

        // --- header ---
        html.append("<div style='padding:4px 6px'>");
        html.append("<div style='font-size:26pt'>")
            .append(escape(person.getAvatarEmoji().isBlank() ? "🙂" : person.getAvatarEmoji()))
            .append("</div>");
        html.append("<div style='font-size:16pt;color:" + hex(Theme.TEXT) + "'><b>")
            .append(escape(person.getName())).append("</b></div>");

        String subtitle = subtitle(person);
        if (!subtitle.isBlank()) {
            html.append("<div class='muted'>").append(escape(subtitle)).append("</div>");
        }

        // --- relation to the current user ---
        if (relation != null && !relation.isBlank()) {
            html.append("<div style='margin-top:10px;padding:8px;background:"
                    + hex(Theme.ELEVATED) + ";color:" + hex(Theme.ACCENT) + "'>")
                .append(escape(relation)).append("</div>");
        }

        // --- bio ---
        if (!person.getBio().isBlank()) {
            html.append("<div style='margin-top:12px;color:" + hex(Theme.TEXT_DIM) + "'><i>“")
                .append(escape(person.getBio())).append("”</i></div>");
        }

        // --- interests ---
        Map<InterestTag, Integer> interests = person.getInterestIntensities();
        if (!interests.isEmpty()) {
            html.append(section("Interests"));
            html.append("<div>");
            interests.forEach((tag, intensity) ->
                    html.append(chip(tag.label() + " " + dots(intensity),
                            new Color(tag.category().getRgb()))));
            html.append("</div>");
        }

        // --- looking for ---
        Set<Intent> intents = person.getLookingFor();
        if (!intents.isEmpty()) {
            html.append(section("Looking for"));
            html.append("<div>");
            for (Intent i : intents) html.append(chip(i.getLabel(), new Color(0x4A90D9)));
            html.append("</div>");
        }

        // --- skill exchange ---
        if (!person.getCanTeach().isEmpty()) {
            html.append(section("Can teach"));
            html.append("<div class='body'>").append(escape(String.join(", ", person.getCanTeach())))
                .append("</div>");
        }
        if (!person.getWantsToLearn().isEmpty()) {
            html.append(section("Wants to learn"));
            html.append("<div class='body'>")
                .append(escape(String.join(", ", person.getWantsToLearn()))).append("</div>");
        }
        if (!person.getClubs().isEmpty()) {
            html.append(section("Clubs"));
            html.append("<div class='body'>").append(escape(String.join(", ", person.getClubs())))
                .append("</div>");
        }
        if (!person.getLanguages().isEmpty()) {
            html.append(section("Speaks"));
            html.append("<div class='body'>").append(escape(String.join(", ", person.getLanguages())))
                .append("</div>");
        }

        // --- network ---
        html.append(section("On campus"));
        int degree = service.getConnections(person).size();
        html.append("<div class='body'>").append(degree)
            .append(degree == 1 ? " connection" : " connections").append("</div>");

        var metrics = person.getMetrics();
        if (metrics.getCommunityId() >= 0) {
            html.append("<div class='body'>Circle #").append(metrics.getCommunityId()).append("</div>");
        }
        if (metrics.getPageRank() > 0) {
            html.append("<div class='body'>Influence ")
                .append(String.format("%.2f", metrics.getPageRank())).append("</div>");
        }

        if (!service.getConnections(person).isEmpty()) {
            html.append(section("Connected to"));
            html.append("<div class='body'>");
            boolean first = true;
            for (Person friend : service.getConnections(person)) {
                if (!first) html.append(", ");
                html.append(escape(friend.getName()));
                first = false;
            }
            html.append("</div>");
        }

        // --- completeness ---
        int pct = (int) Math.round(person.profileCompleteness() * 100);
        html.append("<div style='margin-top:14px' class='muted'>Profile ")
            .append(pct).append("% complete</div>");

        html.append("</div>");

        pane.setText(wrap(html.toString()));
        pane.setCaretPosition(0);
    }

    // ================= html helpers =================

    private static String wrap(String bodyHtml) {
        return "<html><head><style>"
                + "body { font-family: '" + Theme.FAMILY + "', sans-serif; font-size: 10pt;"
                + "       background: " + hex(Theme.PANEL) + "; color: " + hex(Theme.TEXT) + "; margin: 0; }"
                + ".muted { color: " + hex(Theme.TEXT_FAINT) + "; font-size: 9pt; }"
                + ".body { color: " + hex(Theme.TEXT_DIM) + "; }"
                + ".head { color: " + hex(Theme.ACCENT) + "; font-size: 9pt; margin-top: 14px;"
                + "        border-bottom: 1px solid " + hex(Theme.BORDER) + "; }"
                + "</style></head><body>" + bodyHtml + "</body></html>";
    }

    /** Swing's HTML renderer needs colours as strings, so Theme values are converted here. */
    private static String hex(java.awt.Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String section(String title) {
        return "<div class='head'><b>" + escape(title.toUpperCase()) + "</b></div>";
    }

    /**
     * Swing's HTML renderer supports only a subset of CSS — no border-radius, and padding
     * on inline elements is unreliable — so chips are drawn with a solid background and
     * non-breaking spaces rather than real padding.
     */
    private static String chip(String text, Color color) {
        String swatch = hex(color);
        return "<span style='background:" + swatch + ";color:#ffffff'>&nbsp;"
                + escape(text) + "&nbsp;</span>&nbsp; ";
    }

    private static String dots(int intensity) {
        return "•".repeat(Math.max(1, Math.min(5, intensity)));
    }

    private static String subtitle(Person p) {
        StringBuilder sb = new StringBuilder();
        if (p.getYear() > 0) sb.append("Year ").append(p.getYear());
        if (!p.getMajor().isBlank()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(p.getMajor());
        }
        if (!p.getHometown().isBlank()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(p.getHometown());
        }
        if (!p.getHostel().isBlank()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(p.getHostel());
        }
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

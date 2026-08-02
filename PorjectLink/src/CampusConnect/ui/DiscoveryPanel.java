package CampusConnect.ui;

import CampusConnect.app.AppSession;
import CampusConnect.domain.InterestTag;
import CampusConnect.domain.Person;
import CampusConnect.persist.EventLog;
import CampusConnect.service.NetworkService;
import CampusConnect.service.RecommendationService;
import CampusConnect.service.RecommendationService.Suggestion;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * "People you should meet" — the core product loop.
 * <p>
 * A scrollable stack of cards, each one a person with the reason they were suggested and
 * two buttons. Everything else in the app describes the network; this is the only screen
 * that asks the user to <em>do</em> something, so it is deliberately the plainest: a face,
 * a sentence, connect or dismiss.
 * <p>
 * Both answers are recorded. Accepting is the positive label for a future ranker and
 * dismissing is the negative one, which is why "not interested" asks a one-click question
 * rather than silently vanishing — the reason is worth more than the rejection.
 */
public class DiscoveryPanel extends JPanel {

    /** How long a dismissed person stays out of the feed. */
    private static final Duration DISMISS_COOLDOWN = Duration.ofDays(30);
    private static final int FEED_SIZE = 6;

    private static final String[] DISMISS_REASONS = {
            "Don't know them", "Not my thing", "I already know them", "Just not now"
    };

    private final NetworkService service;
    private final AppSession session;
    private final EventLog eventLog;
    private final Supplier<RecommendationService> recommender;
    private final Consumer<List<Person>> onSuggestionsChanged;
    private final Runnable onGraphChanged;

    private final JPanel cardStack = new JPanel();
    private final JLabel headerLabel = new JLabel();
    private final JSlider serendipitySlider = new JSlider(0, 100, 15);
    private final JLabel statsLabel = new JLabel();

    public DiscoveryPanel(NetworkService service,
                          AppSession session,
                          EventLog eventLog,
                          Supplier<RecommendationService> recommender,
                          Consumer<List<Person>> onSuggestionsChanged,
                          Runnable onGraphChanged) {
        this.service = service;
        this.session = session;
        this.eventLog = eventLog;
        this.recommender = recommender;
        this.onSuggestionsChanged = onSuggestionsChanged;
        this.onGraphChanged = onGraphChanged;

        setLayout(new BorderLayout(0, 8));
        setBackground(Theme.PANEL);

        add(buildHeader(), BorderLayout.NORTH);

        cardStack.setLayout(new BoxLayout(cardStack, BoxLayout.Y_AXIS));
        cardStack.setBackground(Theme.PANEL);

        JScrollPane scroll = new JScrollPane(cardStack);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getViewport().setBackground(Theme.PANEL);
        add(scroll, BorderLayout.CENTER);

        add(statsLabel, BorderLayout.SOUTH);
        statsLabel.setForeground(Theme.TEXT_FAINT);
        statsLabel.setFont(statsLabel.getFont().deriveFont(Font.PLAIN, 10f));

        session.addListener(p -> refresh());
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);

        headerLabel.setForeground(Theme.TEXT);
        headerLabel.setFont(Theme.title(13));
        header.add(headerLabel, BorderLayout.NORTH);

        JPanel sliderRow = new JPanel(new BorderLayout(6, 0));
        sliderRow.setOpaque(false);

        JLabel caption = new JLabel("Similar ← → Surprising");
        caption.setForeground(Theme.TEXT_FAINT);
        caption.setFont(caption.getFont().deriveFont(Font.PLAIN, 10f));

        serendipitySlider.setOpaque(false);
        serendipitySlider.setToolTipText(
                "Left: people much like you. Right: people you would otherwise never meet.");
        serendipitySlider.addChangeListener(e -> {
            if (!serendipitySlider.getValueIsAdjusting()) refresh();
        });

        sliderRow.add(caption, BorderLayout.NORTH);
        sliderRow.add(serendipitySlider, BorderLayout.CENTER);
        header.add(sliderRow, BorderLayout.CENTER);
        return header;
    }

    // ================= feed =================

    public void refresh() {
        cardStack.removeAll();
        Person me = session.getCurrentUser();

        if (me == null) {
            headerLabel.setText("Who should I meet?");
            cardStack.add(message("Sign in first — Me ▸ Create My Profile, or pick an "
                    + "existing student."));
            finish(List.of());
            return;
        }

        headerLabel.setText("People " + me.getName() + " should meet");

        RecommendationService rec = recommender.get();
        Set<String> skip = eventLog.recentlyDismissedBy(me.getId(), DISMISS_COOLDOWN);
        double serendipity = serendipitySlider.getValue() / 100.0;

        List<Suggestion> suggestions = rec.recommend(me, FEED_SIZE, serendipity, skip);

        if (suggestions.isEmpty()) {
            cardStack.add(message(rec.isColdStart(me)
                    ? "Nothing yet. Add a few interests to your profile and try again."
                    : "You have met everyone we would suggest. Try nudging the slider right."));
        } else {
            if (rec.isColdStart(me)) {
                cardStack.add(note("You are new here, so these are based entirely on your "
                        + "profile rather than who you already know."));
            }
            for (Suggestion s : suggestions) cardStack.add(card(me, s));
        }

        List<Person> people = new ArrayList<>();
        for (Suggestion s : suggestions) {
            people.add(s.person());
            // Logging what was shown, not just what was clicked, is what makes the
            // acceptance rate meaningful later — without it there is no denominator.
            eventLog.record(me.getId(), s.person().getId(), EventLog.Action.SHOWN, null, s.score());
        }
        finish(people);
    }

    private void finish(List<Person> people) {
        double rate = eventLog.acceptanceRate();
        statsLabel.setText(rate < 0
                ? "  " + eventLog.size() + " events logged"
                : String.format("  %d events · %.0f%% of decisions were accepts",
                        eventLog.size(), rate * 100));
        onSuggestionsChanged.accept(people);
        cardStack.revalidate();
        cardStack.repaint();
    }

    // ================= one card =================

    private JPanel card(Person me, Suggestion suggestion) {
        Person them = suggestion.person();

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Theme.ELEVATED);
        card.setBorder(BorderFactory.createCompoundBorder(
                Theme.divider(0, 0, 1, 0),
                new EmptyBorder(10, 12, 12, 12)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 250));

        // name row
        String emoji = them.getAvatarEmoji().isBlank() ? "🙂" : them.getAvatarEmoji();
        JLabel name = new JLabel(emoji + "  " + them.getName());
        name.setForeground(Theme.TEXT);
        name.setFont(Theme.title(14));
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(name);

        String sub = subtitle(them);
        if (!sub.isBlank()) {
            JLabel subtitle = new JLabel(sub);
            subtitle.setForeground(Theme.TEXT_DIM);
            subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(subtitle);
        }

        card.add(Box.createVerticalStrut(6));

        // the reason — the most important line on the card
        JLabel why = new JLabel("<html><body style='width:230px'>"
                + escape(suggestion.explanation()) + "</body></html>");
        why.setForeground(Theme.ACCENT);
        why.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        why.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(why);

        String shared = sharedInterests(me, them);
        if (!shared.isBlank()) {
            JLabel tags = new JLabel("<html><body style='width:230px'>" + escape(shared) + "</body></html>");
            tags.setForeground(Theme.TEXT_DIM);
            tags.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            tags.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(Box.createVerticalStrut(4));
            card.add(tags);
        }

        card.add(Box.createVerticalStrut(8));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        JButton connect = new JButton("Connect");
        connect.setBackground(Theme.SUCCESS);
        connect.setForeground(Theme.BG);
        connect.addActionListener(e -> connect(me, suggestion));

        JButton dismiss = new JButton("Not interested");
        dismiss.addActionListener(e -> dismiss(me, suggestion));

        JLabel score = new JLabel(String.format("%.0f%% match", suggestion.score() * 100));
        score.setForeground(Theme.TEXT_FAINT);
        score.setFont(new Font("Segoe UI", Font.PLAIN, 10));

        buttons.add(connect);
        buttons.add(dismiss);
        buttons.add(score);
        card.add(buttons);

        return card;
    }

    // ================= actions =================

    private void connect(Person me, Suggestion suggestion) {
        try {
            service.addConnection(me, suggestion.person());
            eventLog.record(me.getId(), suggestion.person().getId(),
                    EventLog.Action.CONNECTED, null, suggestion.score());
            onGraphChanged.run();
            refresh();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage());
        }
    }

    private void dismiss(Person me, Suggestion suggestion) {
        String reason = (String) JOptionPane.showInputDialog(this,
                "Why not " + suggestion.person().getName() + "?",
                "Not interested", JOptionPane.QUESTION_MESSAGE, null,
                DISMISS_REASONS, DISMISS_REASONS[0]);
        // A cancelled dialog is not a dismissal — recording one would poison the labels.
        if (reason == null) return;

        eventLog.record(me.getId(), suggestion.person().getId(),
                EventLog.Action.DISMISSED, reason, suggestion.score());

        // "I already know them" is a statement about the graph, not the suggestion:
        // the edge is genuinely missing, so offer to add it rather than just hiding them.
        if (reason.equals("I already know them")) {
            int add = JOptionPane.showConfirmDialog(this,
                    "Add " + suggestion.person().getName() + " as a connection?",
                    "Already know them", JOptionPane.YES_NO_OPTION);
            if (add == JOptionPane.YES_OPTION) {
                try {
                    service.addConnection(me, suggestion.person());
                    onGraphChanged.run();
                } catch (Exception ignored) { }
            }
        }
        refresh();
    }

    // ================= helpers =================

    private String sharedInterests(Person me, Person them) {
        List<InterestTag> shared =
                recommender.get().similarityEngine().sharedInterests(me, them, 4);
        if (shared.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("Shared: ");
        for (int i = 0; i < shared.size(); i++) {
            if (i > 0) sb.append(" · ");
            sb.append(shared.get(i).label());
        }
        return sb.toString();
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
        return sb.toString();
    }

    private JLabel message(String text) {
        JLabel l = new JLabel("<html><body style='width:230px'>" + escape(text) + "</body></html>");
        l.setForeground(Theme.TEXT_DIM);
        l.setBorder(new EmptyBorder(16, 12, 16, 12));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JLabel note(String text) {
        JLabel l = message(text);
        l.setForeground(Theme.YOU);
        return l;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

package CampusConnect.ui;

import CampusConnect.domain.InterestTag;
import CampusConnect.domain.Person;
import CampusConnect.service.NetworkService;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Find anybody on campus.
 * <p>
 * There was no way to locate a specific person at all — on a graph of any size the only
 * option was to read every label until you spotted the one you wanted, and once zooming
 * existed they might not even be on screen.
 * <p>
 * Searches names <em>and</em> interests, because "who plays guitar" is at least as common
 * a question as "where is Kabir", and the interest vocabulary is already normalised so
 * "bball" finds the basketball players.
 */
public class SearchBox extends JPanel {

    private static final int MAX_RESULTS = 8;

    private final NetworkService service;
    private final Consumer<Person> onPick;

    private final JTextField field = new JTextField();
    private final JWindow popup;
    private final DefaultListModel<Hit> model = new DefaultListModel<>();
    private final JList<Hit> list = new JList<>(model);

    /** A match, plus why it matched — shown as the second line of the row. */
    private record Hit(Person person, String reason) {}

    public SearchBox(NetworkService service, Window owner, Consumer<Person> onPick) {
        this.service = service;
        this.onPick = onPick;

        setLayout(new BorderLayout());
        setOpaque(false);
        setMaximumSize(new Dimension(260, 30));
        setPreferredSize(new Dimension(260, 28));

        field.putClientProperty("JTextField.placeholderText", "Search people or interests…");
        field.setToolTipText("Find someone by name, or everyone who shares an interest (Ctrl+F)");
        add(field, BorderLayout.CENTER);

        list.setCellRenderer(new HitRenderer());
        list.setBackground(Theme.ELEVATED);
        list.setSelectionBackground(Theme.ACCENT_DEEP);
        list.setFocusable(false);

        popup = new JWindow(owner);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        popup.add(scroll);

        wire();
    }

    private void wire() {
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { update(); }
            public void removeUpdate(DocumentEvent e) { update(); }
            public void changedUpdate(DocumentEvent e) { update(); }
        });

        // Arrow keys move through results without leaving the text field, so the whole
        // interaction works from the keyboard.
        field.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DOWN -> move(1);
                    case KeyEvent.VK_UP -> move(-1);
                    case KeyEvent.VK_ENTER -> commit();
                    case KeyEvent.VK_ESCAPE -> { hidePopup(); field.setText(""); }
                    default -> { }
                }
            }
        });

        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int i = list.locationToIndex(e.getPoint());
                if (i >= 0) { list.setSelectedIndex(i); commit(); }
            }
        });

        // Hiding on focus loss stops the popup floating over the app after a click
        // elsewhere — a detached JWindow does not go away on its own.
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) {
                if (!e.isTemporary()) SwingUtilities.invokeLater(SearchBox.this::hidePopup);
            }
        });
    }

    private void move(int delta) {
        if (model.isEmpty()) return;
        int next = Math.max(0, Math.min(model.size() - 1, list.getSelectedIndex() + delta));
        list.setSelectedIndex(next);
        list.ensureIndexIsVisible(next);
    }

    private void commit() {
        Hit hit = list.getSelectedValue();
        if (hit == null && !model.isEmpty()) hit = model.get(0);
        if (hit == null) return;
        hidePopup();
        field.setText("");
        onPick.accept(hit.person());
    }

    /** Focus the box — wired to Ctrl+F. */
    public void focusSearch() {
        field.requestFocusInWindow();
        field.selectAll();
    }

    // ================= matching =================

    private void update() {
        String query = field.getText().trim();
        model.clear();
        if (query.isEmpty()) { hidePopup(); return; }

        String q = query.toLowerCase(Locale.ROOT);
        Set<Person> seen = new LinkedHashSet<>();
        List<Hit> hits = new ArrayList<>();

        // Name matches first: an exact prefix is almost always what was meant.
        for (Person p : service.getAllUsers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(q) && seen.add(p)) {
                hits.add(new Hit(p, subtitle(p)));
            }
        }
        for (Person p : service.getAllUsers()) {
            if (p.getName().toLowerCase(Locale.ROOT).contains(q) && seen.add(p)) {
                hits.add(new Hit(p, subtitle(p)));
            }
        }
        // Then people who hold a matching interest, labelled with which one.
        for (Person p : service.getAllUsers()) {
            if (seen.contains(p)) continue;
            for (InterestTag t : p.getInterests()) {
                if (t.label().toLowerCase(Locale.ROOT).contains(q)
                        || t.id().contains(q)) {
                    if (seen.add(p)) hits.add(new Hit(p, "into " + t.label()));
                    break;
                }
            }
        }
        // Finally major and home town, which is how you find "everyone from Kochi".
        for (Person p : service.getAllUsers()) {
            if (seen.contains(p)) continue;
            if (p.getMajor().toLowerCase(Locale.ROOT).contains(q)) {
                if (seen.add(p)) hits.add(new Hit(p, p.getMajor()));
            } else if (p.getHometown().toLowerCase(Locale.ROOT).contains(q)) {
                if (seen.add(p)) hits.add(new Hit(p, "from " + p.getHometown()));
            }
        }

        for (int i = 0; i < Math.min(MAX_RESULTS, hits.size()); i++) model.addElement(hits.get(i));

        if (model.isEmpty()) hidePopup();
        else { list.setSelectedIndex(0); showPopup(); }
    }

    private String subtitle(Person p) {
        StringBuilder sb = new StringBuilder();
        if (p.getYear() > 0) sb.append("Year ").append(p.getYear());
        if (!p.getMajor().isBlank()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(p.getMajor());
        }
        int degree = service.getConnections(p).size();
        if (sb.length() > 0) sb.append(" · ");
        sb.append(degree).append(degree == 1 ? " connection" : " connections");
        return sb.toString();
    }

    // ================= popup =================

    private void showPopup() {
        if (!field.isShowing()) return;
        Point where = field.getLocationOnScreen();
        popup.setBounds(where.x, where.y + field.getHeight() + 2,
                Math.max(300, field.getWidth()), Math.min(model.size(), MAX_RESULTS) * 44 + 6);
        popup.setVisible(true);
    }

    private void hidePopup() { popup.setVisible(false); }

    // ================= rendering =================

    private static class HitRenderer extends JPanel implements ListCellRenderer<Hit> {
        private final JLabel name = new JLabel();
        private final JLabel reason = new JLabel();

        HitRenderer() {
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(5, 9, 5, 9));
            name.setFont(Theme.title(12));
            reason.setFont(Theme.body(10));
            add(name, BorderLayout.NORTH);
            add(reason, BorderLayout.SOUTH);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Hit> list, Hit value,
                                                      int index, boolean selected, boolean focused) {
            String emoji = value.person().getAvatarEmoji();
            name.setText((emoji == null || emoji.isBlank() ? "" : emoji + "  ") + value.person().getName());
            reason.setText(value.reason());
            setBackground(selected ? Theme.ACCENT_DEEP : Theme.ELEVATED);
            name.setForeground(Theme.TEXT);
            reason.setForeground(selected ? Theme.TEXT : Theme.TEXT_FAINT);
            setOpaque(true);
            return this;
        }
    }
}

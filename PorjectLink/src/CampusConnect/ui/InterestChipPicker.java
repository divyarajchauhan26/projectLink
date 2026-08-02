package CampusConnect.ui;

import CampusConnect.domain.InterestCatalog;
import CampusConnect.domain.InterestTag;
import CampusConnect.domain.Person;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Search-and-pick control for interests, with a 1–5 intensity on each choice.
 * <p>
 * This is the whole reason {@link InterestCatalog} exists. Users must never type interests
 * into a free-text box — "bball" and "Basketball" would become two different interests and
 * every similarity score downstream would be computed over noise. Here they can only pick
 * things that already resolve to a canonical tag, so the data is clean by construction
 * rather than by cleanup.
 * <p>
 * The search box is deliberately forgiving: it runs through the catalog's resolver, so a
 * typo or a nickname still finds the right tag.
 */
public class InterestChipPicker extends JPanel {

    private final InterestCatalog catalog = InterestCatalog.getDefault();

    private final JTextField searchField = new JTextField();
    private final DefaultListModel<InterestTag> resultsModel = new DefaultListModel<>();
    private final DefaultListModel<InterestTag> chosenModel = new DefaultListModel<>();
    private final JList<InterestTag> resultsList = new JList<>(resultsModel);
    private final JList<InterestTag> chosenList = new JList<>(chosenModel);
    private final JSpinner intensitySpinner =
            new JSpinner(new SpinnerNumberModel(Person.DEFAULT_INTENSITY,
                    Person.MIN_INTENSITY, Person.MAX_INTENSITY, 1));
    private final JLabel countLabel = new JLabel();

    /** Staging area — flushed onto the Person only when the wizard is accepted. */
    private final Map<InterestTag, Integer> chosen = new LinkedHashMap<>();

    public InterestChipPicker() {
        setLayout(new BorderLayout(10, 10));
        setOpaque(false);

        // --- search ---
        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.setOpaque(false);
        JLabel searchLabel = new JLabel("Search:");
        top.add(searchLabel, BorderLayout.WEST);
        top.add(searchField, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        // --- the two lists ---
        resultsList.setCellRenderer(new TagRenderer(false));
        chosenList.setCellRenderer(new TagRenderer(true));
        resultsList.setVisibleRowCount(10);
        chosenList.setVisibleRowCount(10);

        JPanel lists = new JPanel(new GridLayout(1, 2, 10, 0));
        lists.setOpaque(false);
        lists.add(titled("Available  (double-click to add)", new JScrollPane(resultsList)));
        lists.add(titled("Your interests", new JScrollPane(chosenList)));
        add(lists, BorderLayout.CENTER);

        // --- intensity + actions ---
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bottom.setOpaque(false);
        bottom.add(new JLabel("How much do you care?"));
        bottom.add(intensitySpinner);
        JButton remove = new JButton("Remove");
        bottom.add(remove);
        bottom.add(Box.createHorizontalStrut(16));
        bottom.add(countLabel);
        add(bottom, BorderLayout.SOUTH);

        // --- behaviour ---
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refreshResults(); }
            public void removeUpdate(DocumentEvent e) { refreshResults(); }
            public void changedUpdate(DocumentEvent e) { refreshResults(); }
        });

        // Enter adds the top hit, so the whole flow works without touching the mouse.
        searchField.addActionListener(e -> {
            if (!resultsModel.isEmpty()) add(resultsModel.get(0));
        });

        resultsList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && resultsList.getSelectedValue() != null) {
                    add(resultsList.getSelectedValue());
                }
            }
        });

        chosenList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && chosenList.getSelectedValue() != null) {
                    remove(chosenList.getSelectedValue());
                }
            }
        });

        chosenList.addListSelectionListener(e -> {
            InterestTag tag = chosenList.getSelectedValue();
            if (tag != null) intensitySpinner.setValue(chosen.getOrDefault(tag, Person.DEFAULT_INTENSITY));
        });

        intensitySpinner.addChangeListener(e -> {
            InterestTag tag = chosenList.getSelectedValue();
            if (tag != null) {
                chosen.put(tag, (Integer) intensitySpinner.getValue());
                chosenList.repaint();
            }
        });

        remove.addActionListener(e -> {
            InterestTag tag = chosenList.getSelectedValue();
            if (tag != null) remove(tag);
        });

        refreshResults();
        updateCount();
    }

    private static JPanel titled(String title, JComponent body) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        JLabel l = new JLabel(title);
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 11f));
        p.add(l, BorderLayout.NORTH);
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    // ================= state =================

    private void refreshResults() {
        String query = searchField.getText();
        List<InterestTag> hits = catalog.search(query, 60);
        resultsModel.clear();
        for (InterestTag t : hits) {
            if (!chosen.containsKey(t)) resultsModel.addElement(t);
        }
        // An exact nickname or typo should still land — "bball" finds Basketball even
        // though no label starts with those letters.
        if (resultsModel.isEmpty() && query != null && !query.isBlank()) {
            InterestCatalog.Resolution r = catalog.resolve(query);
            if (r.found() && !chosen.containsKey(r.tag())) resultsModel.addElement(r.tag());
        }
    }

    private void add(InterestTag tag) {
        if (tag == null || chosen.containsKey(tag)) return;
        chosen.put(tag, Person.DEFAULT_INTENSITY);
        chosenModel.addElement(tag);
        resultsModel.removeElement(tag);
        chosenList.setSelectedValue(tag, true);
        updateCount();
    }

    private void remove(InterestTag tag) {
        if (tag == null) return;
        chosen.remove(tag);
        chosenModel.removeElement(tag);
        refreshResults();
        updateCount();
    }

    private void updateCount() {
        int n = chosen.size();
        countLabel.setText(n == 0
                ? "Pick at least 3 — the more you add, the better the matches."
                : n + " selected" + (n < 3 ? "  (3+ recommended)" : ""));
        countLabel.setForeground(n < 3 ? new Color(230, 160, 60) : new Color(120, 200, 130));
    }

    // ================= external API =================

    public Map<InterestTag, Integer> getSelected() { return new LinkedHashMap<>(chosen); }

    public int getSelectedCount() { return chosen.size(); }

    /** Preload from an existing profile, for the edit flow. */
    public void setSelected(Map<InterestTag, Integer> initial) {
        chosen.clear();
        chosenModel.clear();
        if (initial != null) {
            initial.forEach((tag, intensity) -> {
                chosen.put(tag, intensity);
                chosenModel.addElement(tag);
            });
        }
        refreshResults();
        updateCount();
    }

    /** Write the picked interests onto a person, replacing whatever was there. */
    public void applyTo(Person person) {
        person.clearInterests();
        chosen.forEach(person::addInterest);
    }

    // ================= rendering =================

    private class TagRenderer extends DefaultListCellRenderer {
        private final boolean showIntensity;

        TagRenderer(boolean showIntensity) { this.showIntensity = showIntensity; }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            if (value instanceof InterestTag tag) {
                String text = tag.label();
                if (showIntensity) {
                    text += "   " + stars(chosen.getOrDefault(tag, Person.DEFAULT_INTENSITY));
                } else {
                    text += "   · " + tag.category().getLabel();
                }
                setText(text);
                if (!selected) setForeground(new Color(tag.category().getRgb()).brighter());
            }
            return this;
        }

        private String stars(int n) {
            return "★".repeat(Math.max(0, n)) + "☆".repeat(Math.max(0, 5 - n));
        }
    }
}

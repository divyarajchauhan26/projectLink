package CampusConnect.ui;

import CampusConnect.domain.Intent;
import CampusConnect.domain.Person;
import CampusConnect.service.NetworkService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

/**
 * Four-step "tell us about yourself" flow.
 * <p>
 * This is the screen the whole product turns on: a first-year opens the app knowing
 * nobody, and ninety seconds later should be looking at five people worth meeting. Every
 * field except a name is skippable — a half-filled profile still matches well, whereas a
 * form that demands twelve answers gets abandoned at question three and matches nothing.
 * <p>
 * Interests go through {@link InterestChipPicker} rather than a text box, so what comes
 * out is already canonical tags.
 */
public class OnboardingWizard extends JDialog {

    private static final String[] STEP_TITLES = {
            "Who are you?", "What are you into?", "Tell us about yourself", "What are you looking for?"
    };

    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final JLabel heading = new JLabel();
    private final JLabel stepLabel = new JLabel();
    private final JButton backButton = new JButton("Back");
    private final JButton nextButton = new JButton("Next");

    // step 1
    private final JTextField nameField = new JTextField();
    private final JTextField emojiField = new JTextField("🙂");
    private final JTextField majorField = new JTextField();
    private final JSpinner yearSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 6, 1));
    private final JTextField hometownField = new JTextField();
    private final JTextField hostelField = new JTextField();
    private final JTextField languagesField = new JTextField();

    // step 2
    private final InterestChipPicker interestPicker = new InterestChipPicker();

    // step 3
    private final JTextArea bioArea = new JTextArea(5, 30);
    private final JTextField teachField = new JTextField();
    private final JTextField learnField = new JTextField();
    private final JTextField clubsField = new JTextField();

    // step 4
    private final Map<Intent, JCheckBox> intentBoxes = new EnumMap<>(Intent.class);

    private final NetworkService service;
    private final Person editing;
    private int step = 0;
    private Person result;

    /**
     * @param editing an existing profile to edit, or null to create a new person
     */
    public OnboardingWizard(Window owner, NetworkService service, Person editing) {
        super(owner, editing == null ? "Join Campus Connect" : "Edit Profile",
                ModalityType.APPLICATION_MODAL);
        this.service = service;
        this.editing = editing;

        setLayout(new BorderLayout());
        setSize(760, 560);
        setLocationRelativeTo(owner);

        add(buildHeader(), BorderLayout.NORTH);

        body.setOpaque(false);
        body.setBorder(new EmptyBorder(16, 20, 16, 20));
        body.add(buildBasicsStep(), "0");
        body.add(buildInterestsStep(), "1");
        body.add(buildAboutStep(), "2");
        body.add(buildIntentStep(), "3");
        add(body, BorderLayout.CENTER);

        add(buildFooter(), BorderLayout.SOUTH);

        if (editing != null) prefill(editing);
        showStep(0);
    }

    // ================= chrome =================

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(16, 20, 12, 20));
        header.setBackground(new Color(35, 35, 35));

        heading.setFont(new Font("Segoe UI", Font.BOLD, 20));
        heading.setForeground(Color.WHITE);
        header.add(heading, BorderLayout.WEST);

        stepLabel.setForeground(new Color(150, 150, 150));
        header.add(stepLabel, BorderLayout.EAST);
        return header;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        footer.setBackground(new Color(35, 35, 35));

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> { result = null; dispose(); });

        backButton.addActionListener(e -> showStep(step - 1));
        nextButton.addActionListener(e -> {
            if (step < STEP_TITLES.length - 1) showStep(step + 1);
            else finish();
        });

        footer.add(cancel);
        footer.add(backButton);
        footer.add(nextButton);
        return footer;
    }

    private void showStep(int index) {
        step = Math.max(0, Math.min(STEP_TITLES.length - 1, index));
        cards.show(body, String.valueOf(step));
        heading.setText(STEP_TITLES[step]);
        stepLabel.setText("Step " + (step + 1) + " of " + STEP_TITLES.length);
        backButton.setEnabled(step > 0);
        nextButton.setText(step == STEP_TITLES.length - 1
                ? (editing == null ? "Finish & see matches" : "Save") : "Next");
    }

    // ================= steps =================

    private JPanel buildBasicsStep() {
        JPanel form = form();
        addRow(form, "Name *", nameField);
        addRow(form, "Avatar emoji", emojiField);
        addRow(form, "Course / major", majorField);
        addRow(form, "Year", yearSpinner);
        addRow(form, "Home town", hometownField);
        addRow(form, "Hostel / block", hostelField);
        addRow(form, "Languages", languagesField);
        form.add(hint("Only your name is required. Home town and languages are surprisingly "
                + "strong signals — far more people share a course than share a home town."));
        return form;
    }

    private JPanel buildInterestsStep() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        panel.add(interestPicker, BorderLayout.CENTER);
        panel.add(hint("Type to search — nicknames and typos are fine, \"bball\" finds Basketball. "
                + "Rare interests match far more strongly than common ones."), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildAboutStep() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);

        bioArea.setLineWrap(true);
        bioArea.setWrapStyleWord(true);
        bioArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 80, 80)),
                new EmptyBorder(8, 8, 8, 8)));

        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setOpaque(false);
        top.add(new JLabel("A few lines about you"), BorderLayout.NORTH);
        top.add(new JScrollPane(bioArea), BorderLayout.CENTER);
        panel.add(top, BorderLayout.CENTER);

        JPanel form = form();
        addRow(form, "You can teach", teachField);
        addRow(form, "You want to learn", learnField);
        addRow(form, "Clubs / societies", clubsField);
        form.add(hint("Comma-separated. Teach and learn are matched against each other, so "
                + "\"guitar\" here finds someone offering to teach it."));
        panel.add(form, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildIntentStep() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);

        for (Intent intent : Intent.values()) {
            JCheckBox box = new JCheckBox(intent.getLabel() + " — " + intent.getDescription());
            box.setOpaque(false);
            box.setAlignmentX(Component.LEFT_ALIGNMENT);
            intentBoxes.put(intent, box);
            panel.add(box);
            panel.add(Box.createVerticalStrut(4));
        }
        panel.add(Box.createVerticalStrut(10));
        JLabel h = hint("Pick as many as apply. \"A mentor\" pairs you with people offering "
                + "to guide someone — it is matched in the opposite direction, not the same one.");
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(h);
        return panel;
    }

    // ================= form helpers =================

    private static JPanel form() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        return p;
    }

    private static void addRow(JPanel form, String label, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        JLabel l = new JLabel(label);
        l.setPreferredSize(new Dimension(140, 24));
        row.add(l, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        form.add(row);
        form.add(Box.createVerticalStrut(8));
    }

    private static JLabel hint(String text) {
        JLabel l = new JLabel("<html><body style='width:600px'>" + text + "</body></html>");
        l.setForeground(new Color(150, 150, 150));
        l.setFont(l.getFont().deriveFont(Font.ITALIC, 11f));
        l.setBorder(new EmptyBorder(10, 0, 0, 0));
        return l;
    }

    // ================= load / save =================

    private void prefill(Person p) {
        nameField.setText(p.getName());
        emojiField.setText(p.getAvatarEmoji());
        majorField.setText(p.getMajor());
        yearSpinner.setValue(Math.max(1, p.getYear()));
        hometownField.setText(p.getHometown());
        hostelField.setText(p.getHostel());
        languagesField.setText(String.join(", ", p.getLanguages()));
        interestPicker.setSelected(p.getInterestIntensities());
        bioArea.setText(p.getBio());
        teachField.setText(String.join(", ", p.getCanTeach()));
        learnField.setText(String.join(", ", p.getWantsToLearn()));
        clubsField.setText(String.join(", ", p.getClubs()));
        for (Intent i : p.getLookingFor()) {
            JCheckBox box = intentBoxes.get(i);
            if (box != null) box.setSelected(true);
        }
    }

    private void finish() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "A name is required.", "Almost there",
                    JOptionPane.WARNING_MESSAGE);
            showStep(0);
            nameField.requestFocus();
            return;
        }

        Person person = editing;
        if (person == null) {
            // Place near the middle; the physics loop will find it a proper home.
            service.addUserAtPosition(name, 500, 350);
            person = service.findUserByName(name);
            if (person == null) {
                JOptionPane.showMessageDialog(this, "Could not create the profile.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else {
            person.setName(name);
        }

        person.setAvatarEmoji(emojiField.getText().trim());
        person.setMajor(majorField.getText().trim());
        person.setYear((Integer) yearSpinner.getValue());
        person.setHometown(hometownField.getText().trim());
        person.setHostel(hostelField.getText().trim());
        person.setBio(bioArea.getText().trim());

        person.setLanguages(splitCsv(languagesField.getText()));
        person.setCanTeach(splitCsv(teachField.getText()));
        person.setWantsToLearn(splitCsv(learnField.getText()));
        person.setClubs(splitCsv(clubsField.getText()));

        interestPicker.applyTo(person);

        java.util.List<Intent> intents = new java.util.ArrayList<>();
        intentBoxes.forEach((intent, box) -> { if (box.isSelected()) intents.add(intent); });
        person.setLookingFor(intents);

        result = person;
        dispose();
    }

    private static java.util.List<String> splitCsv(String raw) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (raw == null) return out;
        for (String s : raw.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** The created or edited person, or null if the user cancelled. */
    public Person getResult() { return result; }
}

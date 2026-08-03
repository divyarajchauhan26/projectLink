package CampusConnect.ui;

import CampusConnect.algorithm.*;
import CampusConnect.app.AppSession;
import CampusConnect.domain.NodeMetrics;
import CampusConnect.domain.Person;
import CampusConnect.persist.CampusSeed;
import CampusConnect.persist.EventLog;
import CampusConnect.persist.GraphIO;
import CampusConnect.domain.InterestTag;
import CampusConnect.domain.Group;
import CampusConnect.service.ConnectionService;
import CampusConnect.service.GroupService;
import CampusConnect.service.InsightService;
import CampusConnect.service.NetworkService;
import CampusConnect.service.RecommendationService;
import CampusConnect.service.RecommendationService.Suggestion;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainFrame extends JFrame {

    private static final String SIDE_PROFILE = "profile";
    private static final String SIDE_TEXT = "text";
    private static final String SIDE_DISCOVER = "discover";

    private NetworkService service;
    private NetworkCanvas canvas;
    private StatsPanel statsPanel;
    private JLabel statusLabel;
    private JLabel userLabel;
    private JTextArea pathDisplay; // Algorithm output
    private ProfileCard profileCard;
    private JPanel sideStack;
    private CardLayout sideCards;
    private JLabel sideTitle;

    private final AppSession session = new AppSession();
    private final EventLog eventLog = new EventLog();
    private DiscoveryPanel discoveryPanel;
    /**
     * Rebuilt lazily whenever the graph or a profile changes — every similarity weight
     * depends on corpus-wide frequencies, so a stale engine scores against the old campus.
     */
    private RecommendationService recommender;
    private InsightService insightService;
    private GroupService groupService;
    private ConnectionService connections;
    private SearchBox searchBox;
    private Toast toast;

    // MODES for clicking on canvas
    private enum Mode { CONNECT, DISCONNECT, PATH, DELETE, VIEW, SET_WEIGHT }
    private Mode currentMode = Mode.VIEW;

    // STATE
    private Person selection = null;

    // PHYSICS ENGINE STATE
    private Timer physicsTimer;
    private boolean physicsEnabled = false;

    public MainFrame() {
        this.service = new NetworkService();
        this.canvas = new NetworkCanvas(service);
        this.connections = new ConnectionService(service);

        setTitle("Campus Connect");
        setSize(1360, 880);
        // The two side panels take a fixed 520px. Below this the graph gets squeezed to
        // nothing, so refuse to go smaller rather than degrade silently.
        setMinimumSize(new Dimension(1040, 640));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        setupMenuBar();

        // --- Toolbar ---
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBackground(Theme.PANEL);
        toolBar.setBorder(Theme.divider(0, 0, 1, 0));

        JButton btnAdd = new JButton("Add User");
        btnAdd.setBackground(Theme.SUCCESS);
        btnAdd.setForeground(Theme.BG);

        ButtonGroup group = new ButtonGroup();
        // Inspect is the default mode, so it needs a button of its own — a ButtonGroup
        // can never be deselected, so without this the mode becomes unreachable
        // as soon as any other toggle is pressed.
        JToggleButton btnInspect = createToggle(group, "Inspect", Mode.VIEW);
        btnInspect.setSelected(true);
        JToggleButton btnConnect = createToggle(group, "Connect", Mode.CONNECT);
        JToggleButton btnDisconnect = createToggle(group, "Disconnect", Mode.DISCONNECT);
        JToggleButton btnWeight = createToggle(group, "Set Weight", Mode.SET_WEIGHT);
        JToggleButton btnPath = createToggle(group, "Warm Intro", Mode.PATH);
        JToggleButton btnDelete = createToggle(group, "Delete", Mode.DELETE);

        JToggleButton btnPhysics = new JToggleButton("Physics");
        btnPhysics.addActionListener(e -> {
            physicsEnabled = btnPhysics.isSelected();
            statusLabel.setText("Physics: " + (physicsEnabled ? "ON" : "OFF"));
        });

        // Destructive: this discards the current graph. Label it honestly.
        JButton btnReset = new JButton("Reset Demo");
        btnReset.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "This will discard the current graph and reload the demo network.\nContinue?",
                    "Reset to Demo Graph", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) resetView();
        });

        toolBar.add(btnAdd);
        toolBar.addSeparator();
        toolBar.add(btnInspect);
        toolBar.add(btnConnect);
        toolBar.add(btnDisconnect);
        toolBar.add(btnWeight);
        toolBar.add(btnDelete);
        toolBar.addSeparator();
        toolBar.add(btnPath);
        toolBar.addSeparator();
        toolBar.add(btnPhysics);

        JButton btnFit = new JButton("Fit");
        btnFit.setToolTipText("Frame the whole campus  (F)");
        btnFit.addActionListener(e -> canvas.fitToView());
        toolBar.addSeparator();
        toolBar.add(btnFit);

        toolBar.add(Box.createHorizontalGlue());

        // Search sits in the toolbar rather than a menu: finding a person is the most
        // frequent thing anyone does here and it had no entry point at all.
        searchBox = new SearchBox(service, this, this::revealPerson);
        toolBar.add(searchBox);
        toolBar.addSeparator();
        toolBar.add(btnReset);

        add(toolBar, BorderLayout.NORTH);

        toast = new Toast(getLayeredPane());
        installShortcuts(btnInspect, btnConnect, btnPhysics);

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("control F"), "focusSearch");
        getRootPane().getActionMap().put("focusSearch", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                searchBox.focusSearch();
            }
        });

        // --- Center Canvas ---
        add(canvas, BorderLayout.CENTER);

        // --- Left Stats Panel ---
        statsPanel = new StatsPanel(service);
        add(statsPanel, BorderLayout.WEST);

        // --- Right Panel: a profile card OR algorithm output, swapped by CardLayout ---
        JPanel sidePanel = new JPanel(new BorderLayout());
        sidePanel.setPreferredSize(new Dimension(300, 0));
        sidePanel.setBackground(Theme.PANEL);
        sidePanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        sideTitle = new JLabel("Profile");
        sideTitle.setForeground(Theme.TEXT);
        sideTitle.setFont(Theme.title(13));
        sidePanel.add(sideTitle, BorderLayout.NORTH);

        pathDisplay = new JTextArea();
        pathDisplay.setEditable(false);
        pathDisplay.setBackground(Theme.PANEL);
        pathDisplay.setForeground(Theme.TEXT);
        pathDisplay.setFont(Theme.mono(12));
        pathDisplay.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scroll = new JScrollPane(pathDisplay);
        scroll.setBorder(null);

        profileCard = new ProfileCard(service);

        sideCards = new CardLayout();
        sideStack = new JPanel(sideCards);
        discoveryPanel = new DiscoveryPanel(
                service, session, eventLog, this::recommender,
                people -> canvas.setSuggested(people),
                () -> onGraphChanged("Connected."));

        sideStack.add(profileCard, SIDE_PROFILE);
        sideStack.add(scroll, SIDE_TEXT);
        sideStack.add(discoveryPanel, SIDE_DISCOVER);
        sidePanel.add(sideStack, BorderLayout.CENTER);

        add(sidePanel, BorderLayout.EAST);

        // --- Bottom Status ---
        statusLabel = new JLabel("Welcome to Campus Connect!");
        statusLabel.setFont(Theme.body(12));
        statusLabel.setForeground(Theme.TEXT_DIM);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        userLabel = new JLabel();
        userLabel.setFont(Theme.title(12));
        userLabel.setForeground(Theme.YOU);
        userLabel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBackground(Theme.BG);
        statusPanel.setBorder(Theme.divider(1, 0, 0, 0));
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        statusPanel.add(userLabel, BorderLayout.EAST);
        add(statusPanel, BorderLayout.SOUTH);

        // Anything that depends on "who am I" subscribes rather than being poked by hand
        // at each call site — switching user has to refresh several things at once.
        session.addListener(person -> {
            userLabel.setText(person == null
                    ? "Viewing as: nobody — Me ▸ Create My Profile"
                    : "  ●  Viewing as " + person.getAvatarEmoji() + " " + person.getName());
            canvas.setCurrentUser(person);
        });

        // --- Logic ---
        btnAdd.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Enter User Name:");
            if (name != null && !name.trim().isEmpty()) {
                try {
                    service.addRandomUser(name, NetworkService.WORLD_WIDTH, NetworkService.WORLD_HEIGHT);
                    onGraphChanged("Added " + name);
                } catch (Exception ex) {
                    toast.error(ex.getMessage());
                }
            }
        });

        canvas.addPropertyChangeListener("nodeClicked", evt -> {
            Person clicked = (Person) evt.getNewValue();
            if (clicked != null) {
                try {
                    handleNodeClick(clicked);
                } catch (Exception ex) {
                    toast.error(ex.getMessage());
                }
            }
        });
        
        // Escape backs out of a half-finished two-click action. Previously the only way
        // out was to complete it or switch modes.
        canvas.addPropertyChangeListener("cancelPending", evt -> {
            if (selection != null) {
                resetSelection();
                statusLabel.setText("Cancelled.");
            }
        });

        canvas.addPropertyChangeListener("canvasClicked", evt -> {
            // Deselect on empty click
            resetSelection();
        });

        physicsTimer = new Timer(30, e -> {
            if (physicsEnabled) {
                service.updatePhysics(NetworkService.WORLD_WIDTH, NetworkService.WORLD_HEIGHT);
                canvas.repaint();
            }
        });
        physicsTimer.start();

        // Load the campus after the window is visible
        SwingUtilities.invokeLater(() -> {
            loadDefaultGraph();
            onGraphChanged("Campus loaded.");

            // Sign in as the one student with no connections at all.
            // Opening as nobody meant every intelligent feature — suggestions, the
            // similarity map, warm intros — sat behind a menu the user had no reason to
            // open, so the app looked like the graph editor it used to be. Starting as
            // Aarav puts the recommender on screen immediately, and he is the honest
            // demonstration: no friends, so nothing on his feed can come from the graph.
            canvas.fitToView();

            Person demo = service.findUserByName("Aarav Jain");
            if (demo != null && !session.hasCurrentUser()) {
                session.setCurrentUser(demo);
                showMyMatches();
                statusLabel.setText("Viewing as Aarav, a first-year who knows nobody yet — "
                        + "every suggestion below comes from his profile alone.");
            }
        });
    }

    private void setupMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // --- FILE ---
        JMenu menuFile = new JMenu("File");
        JMenuItem itemSave = new JMenuItem("Save to JSON...");
        JMenuItem itemLoad = new JMenuItem("Load from JSON...");
        JMenuItem itemExport = new JMenuItem("Export Adjacency CSV...");
        
        itemSave.addActionListener(e -> saveGraph());
        itemLoad.addActionListener(e -> loadGraph());
        itemExport.addActionListener(e -> exportCsv());
        
        JMenuItem itemClear = new JMenuItem("Clear Graph");
        itemClear.addActionListener(e -> {
            service.clear();
            resetVisualState();
            onGraphChanged("Graph cleared.");
        });
        
        menuFile.add(itemSave); menuFile.add(itemLoad); menuFile.addSeparator();
        menuFile.add(itemExport); menuFile.addSeparator(); menuFile.add(itemClear);

        // --- INSIGHTS ---
        // Rewritten from the old Algorithms menu. The algorithms are unchanged; what
        // changed is that each item now answers a question a student would actually ask.
        // Items whose output could not be phrased as a sentence about a person -- custom
        // routing, cycle detection, diameter as a bare number -- were removed rather than
        // reworded.
        JMenu menuInsights = new JMenu("Insights");
        JMenuItem itemCircles = new JMenuItem("Your Circles");
        JMenuItem itemSquads = new JMenuItem("Squads (everyone knows everyone)");
        JMenuItem itemIsolated = new JMenuItem("Who the network is failing");
        JMenuItem itemAboutMe = new JMenuItem("What am I in this network?");
        JMenuItem itemFragile = new JMenuItem("Who holds campus together");

        itemCircles.addActionListener(e -> showCircles());
        itemSquads.addActionListener(e -> showSquads());
        itemIsolated.addActionListener(e -> showIsolated());
        itemAboutMe.addActionListener(e -> showMyRole());
        itemFragile.addActionListener(e -> showFragility());

        menuInsights.add(itemCircles);
        menuInsights.add(itemSquads);
        menuInsights.addSeparator();
        menuInsights.add(itemAboutMe);
        menuInsights.add(itemFragile);
        menuInsights.addSeparator();
        menuInsights.add(itemIsolated);

        
        // --- VIEW ---
        JMenu menuView = new JMenu("View");
        JMenuItem viewNormal = new JMenuItem("Normal View");

        JMenu menuHeat = new JMenu("Heatmap");
        JMenuItem heatSimilarity = new JMenuItem("★ Similarity to Me");
        JMenuItem heatInfluence = new JMenuItem("Influence (PageRank)");
        JMenuItem heatBridge = new JMenuItem("Bridge Score (Betweenness)");
        JMenuItem heatReach = new JMenuItem("Reach (Closeness)");
        JMenuItem heatDegree = new JMenuItem("Connections (Degree)");

        JMenuItem viewCommunity = new JMenuItem("Toggle Communities");

        viewNormal.addActionListener(e -> {
            canvas.setShowCommunities(false);
            canvas.setShowHeatmap(false);
            canvas.setBridges(null);
            canvas.clearSuggested();
        });

        heatSimilarity.addActionListener(e -> showSimilarityHeatmap());
        heatInfluence.addActionListener(e -> {
            PageRank.compute(service.getAllUsers(), service.getAdjacencyList());
            canvas.setHeatmapMetric(NodeMetrics.Metric.PAGE_RANK);
            onGraphChanged("Heatmap: influence.");
        });
        heatBridge.addActionListener(e -> {
            CentralityMetrics.betweennessCentrality(service.getAllUsers(), service.getAdjacencyList())
                    .forEach((u, v) -> u.getMetrics().setBetweenness(v));
            canvas.setHeatmapMetric(NodeMetrics.Metric.BETWEENNESS);
            onGraphChanged("Heatmap: bridge score.");
        });
        heatReach.addActionListener(e -> {
            CentralityMetrics.closenessCentrality(service.getAllUsers(), service.getAdjacencyList())
                    .forEach((u, v) -> u.getMetrics().setCloseness(v));
            canvas.setHeatmapMetric(NodeMetrics.Metric.CLOSENESS);
            onGraphChanged("Heatmap: reach.");
        });
        heatDegree.addActionListener(e -> {
            CentralityMetrics.degreeCentrality(service.getAllUsers(), service.getAdjacencyList())
                    .forEach((u, v) -> u.getMetrics().setDegree(v));
            canvas.setHeatmapMetric(NodeMetrics.Metric.DEGREE);
            onGraphChanged("Heatmap: connection count.");
        });

        viewCommunity.addActionListener(e -> canvas.setShowCommunities(true));

        menuHeat.add(heatSimilarity);
        menuHeat.addSeparator();
        menuHeat.add(heatInfluence);
        menuHeat.add(heatBridge);
        menuHeat.add(heatReach);
        menuHeat.add(heatDegree);

        menuView.add(viewNormal); menuView.add(menuHeat); menuView.add(viewCommunity);

        // --- ME ---
        JMenu menuMe = new JMenu("Me");
        JMenuItem itemCreate = new JMenuItem("Create My Profile...");
        JMenuItem itemSwitch = new JMenuItem("Sign in as an existing student...");
        JMenuItem itemEdit = new JMenuItem("Edit My Profile...");
        JMenuItem itemMatches = new JMenuItem("Who should I meet?");

        itemCreate.addActionListener(e -> createProfile());
        itemSwitch.addActionListener(e -> switchUser());
        itemEdit.addActionListener(e -> editProfile());
        itemMatches.addActionListener(e -> showMyMatches());

        menuMe.add(itemCreate);
        menuMe.add(itemSwitch);
        menuMe.add(itemEdit);
        menuMe.addSeparator();
        menuMe.add(itemMatches);
        menuMe.addSeparator();
        JMenuItem itemRequests = new JMenuItem("Connection requests");
        itemRequests.addActionListener(e -> showRequests());
        menuMe.add(itemRequests);

        menuBar.add(menuFile);
        menuBar.add(menuMe);
        // --- GROUPS ---
        JMenu menuGroups = new JMenu("Groups");
        JMenuItem itemBrowse = new JMenuItem("Browse interest groups");
        JMenuItem itemFit = new JMenuItem("Groups I'd fit into");
        JMenuItem itemSquadGroups = new JMenuItem("Name a squad as a group...");
        JMenuItem itemMyGroups = new JMenuItem("My groups");

        itemBrowse.addActionListener(e -> showInterestGroups());
        itemFit.addActionListener(e -> showGroupsIdFitInto());
        itemSquadGroups.addActionListener(e -> nameASquad());
        itemMyGroups.addActionListener(e -> showMyGroups());

        menuGroups.add(itemBrowse);
        menuGroups.add(itemFit);
        menuGroups.addSeparator();
        menuGroups.add(itemSquadGroups);
        menuGroups.add(itemMyGroups);

        menuBar.add(menuInsights);
        menuBar.add(menuGroups);
        menuBar.add(menuView);
        setJMenuBar(menuBar);
    }

    private void saveGraph() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                GraphIO.save(service, groups().all(), chooser.getSelectedFile());
                toast.success("Saved " + service.getAllUsers().size() + " profiles.");
            } catch (Exception ex) {
                toast.error("Could not save: " + ex.getMessage());
            }
        }
    }
    
    private void loadGraph() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = chooser.getSelectedFile();
                GraphIO.LoadReport report = GraphIO.load(service, file);
                // Clear overlays only — resetView() would wipe the graph we just loaded.
                resetVisualState();
                onGraphChanged("Loaded " + report.summary() + " from " + file.getName());

                // A file that half-loaded is worse than one that failed: say what was lost.
                if (!report.clean()) {
                    StringBuilder sb = new StringBuilder("Loaded with warnings:\n\n");
                    for (String warn : report.warnings()) sb.append(" - ").append(warn).append('\n');
                    pathDisplay.setText(sb.toString());
                }
            } catch (Exception ex) {
                toast.error("Could not load: " + ex.getMessage());
            }
        }
    }
    
    private void exportCsv() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                GraphIO.exportCsv(service, chooser.getSelectedFile());
                toast.success("CSV exported.");
            } catch (Exception ex) {
                toast.error("Could not export: " + ex.getMessage());
            }
        }
    }

    private JToggleButton createToggle(ButtonGroup group, String name, Mode mode) {
        JToggleButton btn = new JToggleButton(name);
        btn.addActionListener(e -> {
            currentMode = mode;
            resetVisualState();
            statusLabel.setText("Mode: " + mode);
        });
        group.add(btn);
        return btn;
    }

    private void handleNodeClick(Person clicked) throws Exception {
        switch (currentMode) {
            case CONNECT:
                if (selection == null) {
                    selection = clicked;
                    canvas.setActiveSelection(selection);
                    canvas.setPendingLinkFrom(selection);
                    statusLabel.setText("Connecting: Selected " + clicked.getName());
                } else {
                    service.addConnection(selection, clicked);
                    onGraphChanged("Connected " + selection.getName() + " & " + clicked.getName());
                    resetSelection();
                }
                break;

            case DISCONNECT:
                if (selection == null) {
                    selection = clicked;
                    canvas.setActiveSelection(selection);
                    canvas.setPendingLinkFrom(selection);
                    statusLabel.setText("Disconnecting: Selected " + clicked.getName());
                } else {
                    service.removeConnection(selection, clicked);
                    onGraphChanged("Disconnected " + selection.getName() + " & " + clicked.getName());
                    resetSelection();
                }
                break;
                
            case SET_WEIGHT:
                if (selection == null) {
                    selection = clicked;
                    canvas.setActiveSelection(selection);
                    canvas.setPendingLinkFrom(selection);
                    statusLabel.setText("Set Weight: Selected " + clicked.getName());
                } else {
                    String input = JOptionPane.showInputDialog(this, "Enter weight for edge (default 1.0):", "1.0");
                    if (input != null) {
                        try {
                            double w = Double.parseDouble(input);
                            // Ensure they are connected first
                            if (!service.getConnections(selection).contains(clicked)) {
                                service.addConnection(selection, clicked);
                            }
                            service.setEdgeWeight(selection, clicked, w);
                            onGraphChanged("Weight set between " + selection.getName() + " & " + clicked.getName());
                        } catch (NumberFormatException e) {
                            toast.error("That is not a number.");
                        }
                    }
                    resetSelection();
                }
                break;

            case PATH: // warmest chain of introductions
                if (selection == null) {
                    selection = clicked;
                    canvas.setActiveSelection(selection);
                    canvas.setPendingLinkFrom(selection);
                    statusLabel.setText("From " + clicked.getName() + " — now click who you want to meet.");
                    pathDisplay.setText("Click the person you want an introduction to...");
                    showText("Warm intro");
                } else {
                    List<Person> chain = insights().warmestIntroduction(selection, clicked);
                    canvas.setHighlightedPath(chain, null);
                    showText("Warm intro");

                    StringBuilder sb = new StringBuilder("=== Warm introduction ===\n\n");
                    if (chain.isEmpty()) {
                        sb.append("No route. ").append(selection.getName())
                          .append(" has no path to ").append(clicked.getName()).append(".\n\n")
                          .append("Try the discovery feed instead — it works on\n")
                          .append("profiles rather than who you already know.\n");
                        statusLabel.setText("No route between them.");
                    } else {
                        sb.append(insights().describeIntroduction(chain)).append("\n\n");
                        sb.append("The chain\n");
                        for (int i = 0; i < chain.size(); i++) {
                            sb.append("  ").append(i + 1).append(". ")
                              .append(chain.get(i).getName()).append('\n');
                            if (i < chain.size() - 1) {
                                sb.append("       closeness ")
                                  .append(String.format("%.1f",
                                          service.getEdgeWeight(chain.get(i), chain.get(i + 1))))
                                  .append('\n');
                            }
                        }
                        sb.append("\nThis is the warmest route, not the shortest —\n")
                          .append("a longer chain through close friends beats a\n")
                          .append("short one through people who barely talk.\n");
                        statusLabel.setText((chain.size() - 1) + " steps to reach " + clicked.getName());
                    }
                    pathDisplay.setText(sb.toString());
                    pathDisplay.setCaretPosition(0);
                    resetSelection();
                }
                break;


            case DELETE:
                session.forget(clicked);
                service.removeUser(clicked);
                onGraphChanged("Deleted " + clicked.getName());
                break;

            case VIEW:
                selection = clicked;
                canvas.setActiveSelection(selection);
                showProfile(clicked);
                break;
        }
    }

    private void onGraphChanged(String statusMessage) {
        statusLabel.setText(statusMessage);
        statsPanel.updateStats();
        // Every similarity weight is derived from corpus-wide frequencies, so a changed
        // graph or profile invalidates the whole engine. Dropped here and rebuilt on
        // next use rather than eagerly — most graph edits are never followed by a query.
        recommender = null;
        insightService = null;
        // groupService holds user-created groups, so it is refreshed rather than dropped.
        canvas.repaint();
    }

    // ================= session & profiles =================

    private RecommendationService recommender() {
        if (recommender == null) recommender = new RecommendationService(service);
        return recommender;
    }

    private void createProfile() {
        OnboardingWizard wizard = new OnboardingWizard(this, service, null);
        wizard.setVisible(true);
        Person created = wizard.getResult();
        if (created == null) return;

        onGraphChanged("Welcome, " + created.getName() + "!");
        session.setCurrentUser(created);
        // Never leave a new user staring at an empty canvas — the payoff for filling in
        // the form has to be immediate, so go straight to their matches.
        showMyMatches();
    }

    private void switchUser() {
        List<Person> people = new ArrayList<>(service.getAllUsers());
        if (people.isEmpty()) {
            toast.warn("There is nobody on campus yet.");
            return;
        }
        people.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        Person chosen = (Person) JOptionPane.showInputDialog(this,
                "Sign in as:", "Switch student", JOptionPane.PLAIN_MESSAGE, null,
                people.toArray(), session.getCurrentUser());
        if (chosen == null) return;

        session.setCurrentUser(chosen);
        statusLabel.setText("Signed in as " + chosen.getName());
        showProfile(chosen);
    }

    private void editProfile() {
        Person me = session.getCurrentUser();
        if (me == null) {
            toast.warn("No profile selected. Me ▸ Create My Profile.");
            return;
        }
        OnboardingWizard wizard = new OnboardingWizard(this, service, me);
        wizard.setVisible(true);
        if (wizard.getResult() != null) {
            onGraphChanged("Profile updated.");
            showProfile(me);
        }
    }

    private void showProfile(Person person) {
        Person me = session.getCurrentUser();
        String relation = null;

        if (me != null && person != me) {
            if (service.getConnections(me).contains(person)) {
                relation = "You two are already connected.";
            } else {
                // Reuse the recommender's own reasoning rather than inventing a second
                // explanation that could disagree with the ranking.
                for (Suggestion s : recommender().recommend(me, 40)) {
                    if (s.person() == person) { relation = s.explanation(); break; }
                }
            }
        }

        profileCard.showPerson(person, relation);
        sideTitle.setText(person == me && me != null ? "Your Profile" : "Profile");
        sideCards.show(sideStack, SIDE_PROFILE);
    }

    private void showMyMatches() {
        Person me = session.getCurrentUser();
        if (me == null) {
            toast.warn("Sign in first — Me ▸ Create My Profile.");
            return;
        }

        sideTitle.setText("Who to meet");
        sideCards.show(sideStack, SIDE_DISCOVER);
        discoveryPanel.refresh();
        statusLabel.setText("Suggestions for " + me.getName()
                + " — ghost lines on the canvas show who.");
    }

    /**
     * Colour the whole campus by how well each person matches the signed-in user.
     * <p>
     * The other heatmaps describe the network — who is influential, who bridges groups.
     * This one describes it <em>from where you are standing</em>, which is the only view
     * that answers the question a student actually has.
     */
    private void showSimilarityHeatmap() {
        Person me = session.getCurrentUser();
        if (me == null) {
            JOptionPane.showMessageDialog(this,
                    "This map is relative to you, so we need to know who you are first.\n"
                            + "Use Me ▸ Create My Profile, or sign in as an existing student.");
            return;
        }

        Map<Person, Double> affinity = recommender().affinityTo(me);
        for (Person p : service.getAllUsers()) {
            p.getMetrics().setSimilarityToMe(affinity.getOrDefault(p, 0.0));
        }
        // You are trivially a perfect match for yourself; showing that as the hottest
        // node would waste the top of the scale on information nobody needs.
        me.getMetrics().setSimilarityToMe(0.0);

        canvas.setHeatmapMetric(NodeMetrics.Metric.SIMILARITY_TO_ME);
        statusLabel.setText("Campus coloured by how well each person matches " + me.getName());
    }

    // ================= insights =================

    private InsightService insights() {
        if (insightService == null) insightService = new InsightService(service);
        return insightService;
    }

    private void showCircles() {
        List<InsightService.Circle> circles = insights().circles();
        canvas.setShowCommunities(true);

        StringBuilder sb = new StringBuilder("=== Your Circles ===\n\n");
        sb.append("Groups the network found on its own, named\n")
          .append("after what their members share.\n\n");

        for (InsightService.Circle c : circles) {
            sb.append(c.name()).append('\n');
            sb.append("  ").append(c.size()).append(" people · ")
              .append(String.format("%.0f%% ", c.density() * 100))
              .append(c.density() > 0.6 ? "tight-knit" : c.density() > 0.3 ? "well connected" : "loose")
              .append('\n');
            sb.append("  ");
            for (int i = 0; i < c.members().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(c.members().get(i).getName());
            }
            sb.append("\n\n");
        }
        pathDisplay.setText(sb.toString());
        pathDisplay.setCaretPosition(0);
        showText("Your Circles");
        onGraphChanged(circles.size() + " circles found.");
    }

    private void showSquads() {
        List<InsightService.Squad> squads = insights().squads(3);

        StringBuilder sb = new StringBuilder("=== Squads ===\n\n");
        sb.append("Groups where everyone knows everyone else.\n\n");
        if (squads.isEmpty()) sb.append("No squads of 3 or more yet.\n");

        for (int i = 0; i < Math.min(12, squads.size()); i++) {
            InsightService.Squad s = squads.get(i);
            sb.append(s.size()).append(" people");
            if (s.sharedInterest() != null) sb.append(" · all into ").append(s.sharedInterest());
            sb.append('\n').append("  ");
            for (int j = 0; j < s.members().size(); j++) {
                if (j > 0) sb.append(", ");
                sb.append(s.members().get(j).getName());
            }
            sb.append("\n\n");
        }
        pathDisplay.setText(sb.toString());
        pathDisplay.setCaretPosition(0);
        showText("Squads");
        statusLabel.setText(squads.size() + " squads of 3 or more.");
    }

    private void showIsolated() {
        List<InsightService.Isolated> isolated = insights().isolated();

        StringBuilder sb = new StringBuilder("=== Who the network is failing ===\n\n");
        if (isolated.isEmpty()) {
            sb.append("Nobody is stranded. Everyone has a way in.\n");
        } else {
            sb.append("These students are hard to reach. The kindest\n")
              .append("thing the app can do is surface them to others.\n\n");
            for (InsightService.Isolated i : isolated) {
                sb.append(i.person().getName()).append('\n')
                  .append("  ").append(i.reason()).append('\n');
                if (!i.person().getInterests().isEmpty()) {
                    sb.append("  into: ");
                    int n = 0;
                    for (InterestTag t : i.person().getInterests()) {
                        if (n++ > 0) sb.append(", ");
                        if (n > 3) { sb.append("…"); break; }
                        sb.append(t.label());
                    }
                    sb.append('\n');
                }
                sb.append('\n');
            }
        }
        pathDisplay.setText(sb.toString());
        pathDisplay.setCaretPosition(0);
        showText("Needs a hand");
        statusLabel.setText(isolated.size() + " students are barely connected.");
    }

    private void showMyRole() {
        Person me = session.getCurrentUser();
        if (me == null) {
            toast.warn("Sign in first — this is about you.");
            return;
        }

        List<InsightService.Circle> circles = insights().circles();
        String archetype = insights().archetype(me);
        int oneHop = insights().reachWithin(me, 1);
        int twoHop = insights().reachWithin(me, 2);
        int threeHop = insights().reachWithin(me, 3);

        StringBuilder sb = new StringBuilder("=== " + me.getName() + " ===\n\n");
        sb.append("You are: ").append(archetype).append("\n\n");
        sb.append("Reach\n");
        sb.append("  ").append(oneHop).append(" friends\n");
        sb.append("  ").append(twoHop).append(" people within 2 handshakes\n");
        sb.append("  ").append(threeHop).append(" within 3\n\n");

        for (InsightService.Circle c : circles) {
            if (c.members().contains(me)) {
                sb.append("Your circle\n  ").append(c.name())
                  .append(" (").append(c.size()).append(" people)\n\n");
                break;
            }
        }

        List<String> bridges = insights().bridgesBetween(me, circles);
        if (!bridges.isEmpty()) {
            sb.append("You connect your circle to\n");
            for (String b : bridges) sb.append("  · ").append(b).append('\n');
            sb.append('\n');
        }

        pathDisplay.setText(sb.toString());
        pathDisplay.setCaretPosition(0);
        showText("About you");
        statusLabel.setText(me.getName() + " is a " + archetype + ".");
    }

    private void showFragility() {
        List<Person[]> bridges =
                GraphAnalyzer.findBridges(service.getAllUsers(), service.getAdjacencyList());
        Set<Person> articulation =
                GraphAnalyzer.findArticulationPoints(service.getAllUsers(), service.getAdjacencyList());
        canvas.setBridges(bridges);

        StringBuilder sb = new StringBuilder("=== Who holds campus together ===\n\n");
        if (articulation.isEmpty()) {
            sb.append("Nobody is a single point of failure —\n")
              .append("the network would survive anyone leaving.\n\n");
        } else {
            sb.append("If these people left, others would lose\n")
              .append("touch with the rest of campus.\n\n");
            for (Person p : articulation) {
                sb.append("  ").append(p.getName()).append('\n');
            }
            sb.append('\n');
        }

        sb.append("Fragile friendships (highlighted red)\n");
        if (bridges.isEmpty()) {
            sb.append("  None — every link has a backup route.\n");
        } else {
            for (Person[] b : bridges) {
                sb.append("  ").append(b[0].getName()).append(" — ").append(b[1].getName())
                  .append('\n');
            }
        }

        pathDisplay.setText(sb.toString());
        pathDisplay.setCaretPosition(0);
        showText("Fragile links");
        onGraphChanged(articulation.size() + " people hold campus together.");
    }

    // ================= groups =================

    private GroupService groups() {
        // Unlike the recommender, this is created once and kept: it owns user-created
        // groups, which are real state rather than a derived cache.
        if (groupService == null) groupService = new GroupService(service, insights());
        return groupService;
    }

    private void showInterestGroups() {
        List<Group> interestGroups = groups().interestGroups();

        StringBuilder sb = new StringBuilder("=== Interest groups ===\n\n");
        sb.append("Everyone who shares an interest is already a\n")
          .append("group — these are found, not created.\n\n");

        for (Group g : interestGroups) {
            sb.append(g.getName()).append("  (").append(g.size()).append(" people)\n");
            sb.append("  ").append(groups().cohesionLabel(g)).append('\n');
            List<Person> members = groups().membersOf(g);
            sb.append("  ");
            for (int i = 0; i < members.size(); i++) {
                if (i > 0) sb.append(", ");
                if (i == 6) { sb.append("+").append(members.size() - 6).append(" more"); break; }
                sb.append(members.get(i).getName());
            }
            sb.append("\n\n");
        }
        pathDisplay.setText(sb.toString());
        pathDisplay.setCaretPosition(0);
        showText("Interest groups");
        statusLabel.setText(interestGroups.size() + " interest groups on campus.");
    }

    private void showGroupsIdFitInto() {
        Person me = session.getCurrentUser();
        if (me == null) {
            toast.warn("Sign in first — Me ▸ Create My Profile.");
            return;
        }

        List<GroupService.Fit> fits = groups().groupsYoudFitInto(me, 8);
        StringBuilder sb = new StringBuilder("=== Groups " + me.getName() + " would fit ===\n\n");
        if (fits.isEmpty()) {
            sb.append("Nothing obvious yet — add a few interests\n")
              .append("to your profile and try again.\n");
        }
        for (GroupService.Fit f : fits) {
            sb.append(f.group().getName()).append('\n');
            sb.append(String.format("  %.0f%% fit · %d people",
                    f.score() * 100, f.group().size()));
            if (f.membersKnown() > 0) {
                sb.append(" · you know ").append(f.membersKnown());
            }
            sb.append('\n');
            sb.append("  ").append(groups().cohesionLabel(f.group())).append("\n\n");
        }
        pathDisplay.setText(sb.toString());
        pathDisplay.setCaretPosition(0);
        showText("Groups for you");
        statusLabel.setText(fits.size() + " groups suit " + me.getName() + ".");
    }

    private void nameASquad() {
        List<Group> squads = groups().suggestedSquads(3);
        if (squads.isEmpty()) {
            toast.info("No squads yet — a squad is 3+ people who all know each other.");
            return;
        }

        Group chosen = (Group) JOptionPane.showInputDialog(this,
                "These people all already know each other.\nName one as a group:",
                "Name a squad", JOptionPane.PLAIN_MESSAGE, null,
                squads.toArray(), squads.get(0));
        if (chosen == null) return;

        String name = JOptionPane.showInputDialog(this, "Group name:", chosen.getName());
        if (name == null || name.isBlank()) return;

        chosen.setName(name.trim());
        groups().add(chosen);
        showMyGroups();
        statusLabel.setText("Created group: " + name.trim());
    }

    private void showMyGroups() {
        List<Group> created = groups().all();
        StringBuilder sb = new StringBuilder("=== Groups ===\n\n");
        if (created.isEmpty()) {
            sb.append("No groups created yet.\n\n")
              .append("Try Groups ▸ Name a squad as a group — the app\n")
              .append("finds sets of people who all already know each\n")
              .append("other and offers to name them.\n");
        }
        for (Group g : created) {
            sb.append(g.getName()).append("  (").append(g.size()).append(" people)\n");
            if (!g.getDescription().isBlank()) sb.append("  ").append(g.getDescription()).append('\n');
            sb.append("  ").append(groups().cohesionLabel(g)).append('\n');
            for (Person p : groups().membersOf(g)) sb.append("   · ").append(p.getName()).append('\n');
            sb.append('\n');
        }
        pathDisplay.setText(sb.toString());
        pathDisplay.setCaretPosition(0);
        showText("Groups");
    }

    // ================= connection requests =================

    private void showRequests() {
        Person me = session.getCurrentUser();
        if (me == null) {
            toast.warn("Sign in first — Me ▸ Create My Profile.");
            return;
        }

        List<ConnectionService.Request> incoming = connections.incoming(me);
        List<ConnectionService.Request> outgoing = connections.outgoing(me);

        StringBuilder sb = new StringBuilder("=== Connection requests ===\n\n");

        sb.append("Waiting on you (").append(incoming.size()).append(")\n");
        if (incoming.isEmpty()) sb.append("  nothing right now\n");
        for (ConnectionService.Request r : incoming) {
            sb.append("  ").append(r.from().getName()).append('\n');
            if (!r.message().isBlank()) sb.append("    \"").append(r.message()).append("\"\n");
        }

        sb.append("\nSent by you (").append(outgoing.size()).append(")\n");
        if (outgoing.isEmpty()) sb.append("  nothing pending\n");
        for (ConnectionService.Request r : outgoing) {
            sb.append("  ").append(r.to().getName()).append(" — waiting\n");
        }

        double share = connections.suggestedShare();
        if (share >= 0) {
            sb.append(String.format("%n%.0f%% of your connections came from a suggestion.%n",
                    share * 100));
        }

        pathDisplay.setText(sb.toString());
        pathDisplay.setCaretPosition(0);
        showText("Requests");

        if (!incoming.isEmpty()) answerNext(me, incoming.get(0));
    }

    private void answerNext(Person me, ConnectionService.Request request) {
        String icebreaker = connections.icebreaker(
                me, request.from(), recommender().similarityEngine());

        int choice = JOptionPane.showConfirmDialog(this,
                request.from().getName() + " wants to connect.\n\n"
                        + (request.message().isBlank() ? "" : "\"" + request.message() + "\"\n\n")
                        + "Accept?\n\nIf you do, try opening with:\n\"" + icebreaker + "\"",
                "Connection request", JOptionPane.YES_NO_CANCEL_OPTION);

        try {
            if (choice == JOptionPane.YES_OPTION) {
                connections.accept(request, ConnectionService.Kind.FRIEND,
                        ConnectionService.Origin.SUGGESTED);
                onGraphChanged("Connected with " + request.from().getName() + ".");
                showRequests();
            } else if (choice == JOptionPane.NO_OPTION) {
                connections.decline(request);
                showRequests();
            }
        } catch (Exception ex) {
            toast.error(ex.getMessage());
        }
    }

    /** Switches the side panel back to algorithm output. */
    private void showText(String title) {
        sideTitle.setText(title);
        sideCards.show(sideStack, SIDE_TEXT);
    }

    /**
     * Single-key shortcuts for the things done most often.
     * <p>
     * Registered WHEN_IN_FOCUSED_WINDOW so they work wherever focus happens to be, except
     * that plain letters would then steal keystrokes from the search box — so the guard
     * checks whether a text field currently has focus before acting.
     */
    private void installShortcuts(JToggleButton inspect, JToggleButton connect,
                                  JToggleButton physics) {
        key("I", "modeInspect", () -> inspect.doClick());
        key("C", "modeConnect", () -> connect.doClick());
        key("P", "togglePhysics", () -> physics.doClick());
        key("M", "myMatches", this::showMyMatches);
        key("H", "similarityMap", this::showSimilarityHeatmap);
        key("shift SLASH", "help", this::showShortcuts);
    }

    private void key(String stroke, String name, Runnable action) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(stroke), name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                // Typing "c" into the search box must not flip the app into Connect mode.
                Component focused = FocusManager.getCurrentManager().getFocusOwner();
                if (focused instanceof JTextComponent || focused instanceof JTextField) return;
                action.run();
            }
        });
    }

    private void showShortcuts() {
        String[][] rows = {
            {"Ctrl+F", "Search people and interests"},
            {"F", "Fit the whole campus on screen"},
            {"0", "Reset zoom"},
            {"+ / -", "Zoom in and out"},
            {"Scroll", "Zoom toward the pointer"},
            {"Drag background", "Pan"},
            {"Ctrl+Drag node", "Move a person"},
            {"I", "Inspect mode"},
            {"C", "Connect mode"},
            {"Esc", "Cancel a half-finished action"},
            {"M", "Who should I meet?"},
            {"H", "Similarity map"},
            {"P", "Toggle physics"},
            {"?", "This list"},
        };
        StringBuilder sb = new StringBuilder("=== Keyboard ===\n\n");
        for (String[] r : rows) sb.append(String.format("  %-16s %s%n", r[0], r[1]));
        sb.append("\n\nTip: hover any person to highlight just their\n")
          .append("corner of the network.\n");
        pathDisplay.setText(sb.toString());
        pathDisplay.setCaretPosition(0);
        showText("Shortcuts");
    }

    private void resetSelection() {
        selection = null;
        canvas.setActiveSelection(null);
        canvas.setPendingLinkFrom(null);
    }

    /**
     * Jump to somebody and show them — the target of a search hit.
     * <p>
     * Centres and zooms rather than just selecting, because on a graph you can pan and
     * zoom around, "selected" is useless if they are off screen.
     */
    private void revealPerson(Person person) {
        if (person == null) return;
        selection = person;
        canvas.setActiveSelection(person);
        canvas.focusOn(person);
        showProfile(person);
        statusLabel.setText(person.getName() + " — " + insights().archetype(person)
                + ", " + service.getConnections(person).size() + " connections.");
    }


    /**
     * Resets visual overlays (path highlights, bridges, heatmaps) without clearing graph data.
     */
    private void resetVisualState() {
        resetSelection();
        canvas.setHighlightedPath(Collections.emptyList(), null);
        canvas.setBridges(null);
        canvas.setShowCommunities(false);
        canvas.setShowHeatmap(false);
        canvas.setVisualizationStep(null);
        canvas.clearSuggested();
        pathDisplay.setText("");
    }

    /**
     * Full reset: clears the entire graph AND all visual state, then loads the default map.
     */
    private void resetView() {
        service.clear();
        resetVisualState();
        loadDefaultGraph();
        onGraphChanged("Graph reset to default campus network.");
    }

    /**
     * Loads the seeded campus: 40 students with full profiles, six interest clusters,
     * and three barely-connected first-years who exist to exercise cold-start matching.
     */
    private void loadDefaultGraph() {
        try {
            CampusSeed.load(service, NetworkService.WORLD_WIDTH, NetworkService.WORLD_HEIGHT);
        } catch (Exception e) {
            // A partial graph silently corrupts every metric downstream — say so loudly.
            statusLabel.setText("Demo campus incomplete: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Could not build the demo campus:\n" + e.getMessage()
                            + "\n\nLoaded " + service.getAllUsers().size() + " students.",
                    "Incomplete Demo Campus", JOptionPane.WARNING_MESSAGE);
        }
    }
}

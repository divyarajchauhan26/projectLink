package CampusConnect.ui;

import CampusConnect.algorithm.*;
import CampusConnect.app.AppSession;
import CampusConnect.domain.NodeMetrics;
import CampusConnect.domain.Person;
import CampusConnect.persist.CampusSeed;
import CampusConnect.persist.GraphIO;
import CampusConnect.service.NetworkService;
import CampusConnect.service.RecommendationService;
import CampusConnect.service.RecommendationService.Suggestion;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
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
    /**
     * Rebuilt lazily whenever the graph or a profile changes — every similarity weight
     * depends on corpus-wide frequencies, so a stale engine scores against the old campus.
     */
    private RecommendationService recommender;

    // MODES for clicking on canvas
    private enum Mode { CONNECT, DISCONNECT, WAYPOINT, PATH, DELETE, VIEW, SET_WEIGHT }
    private Mode currentMode = Mode.VIEW;

    // STATE
    private Person selection = null;
    private List<Person> waypointSequence = new ArrayList<>();

    // PHYSICS ENGINE STATE
    private Timer physicsTimer;
    private boolean physicsEnabled = false;

    public MainFrame() {
        this.service = new NetworkService();
        this.canvas = new NetworkCanvas(service);

        setTitle("Campus Connect - Social Graph Analytics");
        setSize(1300, 850);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        setupMenuBar();

        // --- Toolbar ---
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBackground(new Color(60, 63, 65));

        JButton btnAdd = new JButton("Add User");
        btnAdd.setBackground(new Color(46, 204, 113));
        btnAdd.setForeground(Color.WHITE);

        ButtonGroup group = new ButtonGroup();
        // Inspect is the default mode, so it needs a button of its own — a ButtonGroup
        // can never be deselected, so without this the mode becomes unreachable
        // as soon as any other toggle is pressed.
        JToggleButton btnInspect = createToggle(group, "Inspect", Mode.VIEW);
        btnInspect.setSelected(true);
        JToggleButton btnConnect = createToggle(group, "Connect (Add Link)", Mode.CONNECT);
        JToggleButton btnDisconnect = createToggle(group, "Disconnect", Mode.DISCONNECT);
        JToggleButton btnWeight = createToggle(group, "Set Weight", Mode.SET_WEIGHT);
        JToggleButton btnPath = createToggle(group, "Shortest Path", Mode.PATH);
        JToggleButton btnWaypoint = createToggle(group, "Custom Route", Mode.WAYPOINT);
        JToggleButton btnDelete = createToggle(group, "Delete", Mode.DELETE);

        JToggleButton btnPhysics = new JToggleButton("Physics");
        btnPhysics.addActionListener(e -> {
            physicsEnabled = btnPhysics.isSelected();
            statusLabel.setText("Physics: " + (physicsEnabled ? "ON" : "OFF"));
        });

        // Destructive: this discards the current graph. Label it honestly.
        JButton btnReset = new JButton("Reset to Demo Graph");
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
        toolBar.add(btnWaypoint);
        toolBar.addSeparator();
        toolBar.add(btnPhysics);
        toolBar.add(Box.createHorizontalGlue());
        toolBar.add(btnReset);

        add(toolBar, BorderLayout.NORTH);

        // --- Center Canvas ---
        add(canvas, BorderLayout.CENTER);

        // --- Left Stats Panel ---
        statsPanel = new StatsPanel(service);
        add(statsPanel, BorderLayout.WEST);

        // --- Right Panel: a profile card OR algorithm output, swapped by CardLayout ---
        JPanel sidePanel = new JPanel(new BorderLayout());
        sidePanel.setPreferredSize(new Dimension(300, 0));
        sidePanel.setBackground(new Color(43, 43, 43));
        sidePanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        sideTitle = new JLabel("Profile");
        sideTitle.setForeground(Color.WHITE);
        sideTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        sidePanel.add(sideTitle, BorderLayout.NORTH);

        pathDisplay = new JTextArea();
        pathDisplay.setEditable(false);
        pathDisplay.setBackground(new Color(60, 63, 65));
        pathDisplay.setForeground(new Color(220, 220, 220));
        pathDisplay.setFont(new Font("Monospaced", Font.PLAIN, 12));
        pathDisplay.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scroll = new JScrollPane(pathDisplay);
        scroll.setBorder(null);

        profileCard = new ProfileCard(service);

        sideCards = new CardLayout();
        sideStack = new JPanel(sideCards);
        sideStack.add(profileCard, SIDE_PROFILE);
        sideStack.add(scroll, SIDE_TEXT);
        sidePanel.add(sideStack, BorderLayout.CENTER);

        add(sidePanel, BorderLayout.EAST);

        // --- Bottom Status ---
        statusLabel = new JLabel("Welcome to Campus Connect!");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        statusLabel.setForeground(new Color(200, 200, 200));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        userLabel = new JLabel();
        userLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        userLabel.setForeground(new Color(150, 190, 230));
        userLabel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBackground(new Color(30, 30, 30));
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        statusPanel.add(userLabel, BorderLayout.EAST);
        add(statusPanel, BorderLayout.SOUTH);

        // Anything that depends on "who am I" subscribes rather than being poked by hand
        // at each call site — switching user has to refresh several things at once.
        session.addListener(person -> {
            userLabel.setText(person == null
                    ? "Viewing as: nobody — Me ▸ Create My Profile"
                    : "Viewing as: " + person.getAvatarEmoji() + " " + person.getName());
            canvas.setCurrentUser(person);
        });

        // --- Logic ---
        btnAdd.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Enter User Name:");
            if (name != null && !name.trim().isEmpty()) {
                try {
                    service.addRandomUser(name, canvas.getWidth(), canvas.getHeight());
                    onGraphChanged("Added " + name);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage());
                }
            }
        });

        canvas.addPropertyChangeListener("nodeClicked", evt -> {
            Person clicked = (Person) evt.getNewValue();
            if (clicked != null) {
                try {
                    handleNodeClick(clicked);
                } catch (Exception ex) {
                    statusLabel.setText("Error: " + ex.getMessage());
                    JOptionPane.showMessageDialog(this, "Operation failed: " + ex.getMessage());
                    if (currentMode == Mode.WAYPOINT) resetWaypointsButKeepLast(clicked);
                }
            }
        });
        
        canvas.addPropertyChangeListener("canvasClicked", evt -> {
            // Deselect on empty click
            if (currentMode != Mode.WAYPOINT) resetSelection();
        });

        physicsTimer = new Timer(30, e -> {
            if (physicsEnabled) {
                service.updatePhysics(canvas.getWidth(), canvas.getHeight());
                canvas.repaint();
            }
        });
        physicsTimer.start();

        // Load a rich default graph after the window is visible
        SwingUtilities.invokeLater(() -> {
            loadDefaultGraph();
            onGraphChanged("Default campus network loaded. Explore!");
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

        // --- ALGORITHMS ---
        JMenu menuAlgo = new JMenu("Algorithms");
        JMenuItem itemPageRank = new JMenuItem("Compute PageRank (Heatmap)");
        
        JMenu menuCentrality = new JMenu("Centrality");
        JMenuItem centBetw = new JMenuItem("Betweenness Centrality");
        JMenuItem centClose = new JMenuItem("Closeness Centrality");
        JMenuItem centDeg = new JMenuItem("Degree Centrality");
        
        JMenuItem itemCommunity = new JMenuItem("Detect Communities (Louvain)");
        JMenuItem itemBridges = new JMenuItem("Find Bridges & Articulation Points");
        JMenuItem itemCliques = new JMenuItem("Find Cliques");
        JMenuItem itemDiameter = new JMenuItem("Compute Diameter/Radius");
        
        // Each metric now writes to its own field and tells the canvas which one to
        // colour by, so the heatmap and its label can no longer disagree.
        itemPageRank.addActionListener(e -> {
            PageRank.compute(service.getAllUsers(), service.getAdjacencyList());
            canvas.setHeatmapMetric(NodeMetrics.Metric.PAGE_RANK);
            onGraphChanged("PageRank computed. Heatmap enabled.");
        });

        centBetw.addActionListener(e -> {
            Map<Person, Double> res = CentralityMetrics.betweennessCentrality(service.getAllUsers(), service.getAdjacencyList());
            res.forEach((u, v) -> u.getMetrics().setBetweenness(v));
            canvas.setHeatmapMetric(NodeMetrics.Metric.BETWEENNESS);
            onGraphChanged("Betweenness Centrality computed.");
        });
        centClose.addActionListener(e -> {
            Map<Person, Double> res = CentralityMetrics.closenessCentrality(service.getAllUsers(), service.getAdjacencyList());
            res.forEach((u, v) -> u.getMetrics().setCloseness(v));
            canvas.setHeatmapMetric(NodeMetrics.Metric.CLOSENESS);
            onGraphChanged("Closeness Centrality computed.");
        });
        centDeg.addActionListener(e -> {
            Map<Person, Double> res = CentralityMetrics.degreeCentrality(service.getAllUsers(), service.getAdjacencyList());
            res.forEach((u, v) -> u.getMetrics().setDegree(v));
            canvas.setHeatmapMetric(NodeMetrics.Metric.DEGREE);
            onGraphChanged("Degree Centrality computed.");
        });
        
        itemCommunity.addActionListener(e -> {
            CommunityDetection.detectCommunities(service.getAllUsers(), service.getAdjacencyList());
            canvas.setShowCommunities(true);
            onGraphChanged("Communities detected.");
        });
        
        itemBridges.addActionListener(e -> {
            List<Person[]> bridges = GraphAnalyzer.findBridges(service.getAllUsers(), service.getAdjacencyList());
            canvas.setBridges(bridges);
            Set<Person> articulation = GraphAnalyzer.findArticulationPoints(service.getAllUsers(), service.getAdjacencyList());
            showText("Bridges");
            pathDisplay.setText("=== Critical Network Components ===\n\n");
            pathDisplay.append("Bridges Found: " + bridges.size() + " (Highlighted in Red)\n");
            for (Person[] b : bridges) {
                pathDisplay.append(" - " + b[0].getName() + " <-> " + b[1].getName() + "\n");
            }
            pathDisplay.append("\nArticulation Points: " + articulation.size() + "\n");
            for (Person u : articulation) {
                pathDisplay.append(" - " + u.getName() + "\n");
            }
            canvas.repaint();
        });
        
        itemCliques.addActionListener(e -> {
            List<Set<Person>> cliques = GraphAnalyzer.findMaximalCliques(service.getAllUsers(), service.getAdjacencyList());
            cliques.sort((a,b) -> Integer.compare(b.size(), a.size())); // largest first
            showText("Cliques");
            pathDisplay.setText("=== Maximal Cliques (Size >= 2) ===\n\n");
            pathDisplay.append("Found " + cliques.size() + " cliques.\n\n");
            for (int i = 0; i < Math.min(10, cliques.size()); i++) {
                pathDisplay.append("Size " + cliques.get(i).size() + ": " + cliques.get(i).toString() + "\n");
            }
        });
        
        itemDiameter.addActionListener(e -> {
            GraphAnalyzer.DiameterResult res = GraphAnalyzer.computeDiameterAndRadius(service.getAllUsers(), service.getAdjacencyList());
            showText("Dimensions");
            pathDisplay.setText("=== Network Dimensions ===\n\n");
            pathDisplay.append("Diameter (Longest Shortest Path): " + res.diameter + "\n");
            if (res.peripheralNode != null) pathDisplay.append("Peripheral Node: " + res.peripheralNode.getName() + "\n");
            pathDisplay.append("\nRadius (Shortest Max Path): " + res.radius + "\n");
            if (res.centerNode != null) pathDisplay.append("Center Node: " + res.centerNode.getName() + "\n");
        });

        menuCentrality.add(centBetw); menuCentrality.add(centClose); menuCentrality.add(centDeg);
        menuAlgo.add(itemPageRank); menuAlgo.add(menuCentrality); menuAlgo.addSeparator();
        menuAlgo.add(itemCommunity); menuAlgo.add(itemBridges); menuAlgo.add(itemCliques); menuAlgo.add(itemDiameter);

        // --- SOCIAL ---
        JMenu menuSocial = new JMenu("Social");
        JMenuItem itemRecommend = new JMenuItem("Friend Recommendations (Jaccard)");
        
        itemRecommend.addActionListener(e -> {
            if (selection == null) {
                JOptionPane.showMessageDialog(this, "Select a user on the canvas first.");
                return;
            }
            List<FriendRecommender.Recommendation> recs = FriendRecommender.recommend(
                    selection, service.getAllUsers(), service.getAdjacencyList(), 10);
            
            showText("Recommendations");
            pathDisplay.setText("=== Recommendations for " + selection.getName() + " ===\n\n");
            for (FriendRecommender.Recommendation rec : recs) {
                pathDisplay.append(rec.user.getName() + "\nScore: " + String.format("%.3f", rec.score) + "\nReason: " + rec.reason + "\n\n");
            }
        });
        
        menuSocial.add(itemRecommend);
        
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

        menuBar.add(menuFile);
        menuBar.add(menuMe);
        menuBar.add(menuAlgo);
        menuBar.add(menuSocial);
        menuBar.add(menuView);
        setJMenuBar(menuBar);
    }

    private void saveGraph() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                GraphIO.save(service, chooser.getSelectedFile());
                statusLabel.setText("Saved " + service.getAllUsers().size() + " profiles.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error saving: " + ex.getMessage());
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
                JOptionPane.showMessageDialog(this, "Error loading: " + ex.getMessage());
            }
        }
    }
    
    private void exportCsv() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                GraphIO.exportCsv(service, chooser.getSelectedFile());
                statusLabel.setText("CSV exported successfully.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error exporting: " + ex.getMessage());
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
                            JOptionPane.showMessageDialog(this, "Invalid number.");
                        }
                    }
                    resetSelection();
                }
                break;

            case PATH: // Dijkstra
                if (selection == null) {
                    selection = clicked;
                    canvas.setActiveSelection(selection);
                    statusLabel.setText("Start: " + clicked.getName() + ". Click End.");
                    pathDisplay.setText("Select destination...");
                } else {
                    DijkstraAlgorithm.PathResult res = DijkstraAlgorithm.findShortestPath(
                            selection, clicked, service.getAdjacencyList(), service.getEdgeWeights());
                    
                    canvas.setHighlightedPath(res.path, null);
                    showText("Shortest Path");
                    statusLabel.setText("Dijkstra Path Found: " + (res.path.size()-1) + " hops, Cost: " + res.totalCost);
                    
                    if (res.path.isEmpty() && !selection.equals(clicked)) {
                        pathDisplay.setText("No path found.");
                    } else {
                        StringBuilder sb = new StringBuilder();
                        sb.append("=== Dijkstra Shortest Path ===\n\n");
                        for (int i = 0; i < res.path.size(); i++) {
                            sb.append(i + 1).append(". ").append(res.path.get(i).getName()).append("\n");
                            if (i < res.path.size() - 1) {
                                double w = service.getEdgeWeight(res.path.get(i), res.path.get(i+1));
                                sb.append("   --(").append(w).append(")-->\n");
                            }
                        }
                        sb.append("\nTotal Cost: ").append(res.totalCost);
                        pathDisplay.setText(sb.toString());
                    }
                    
                    
                    resetSelection();
                }
                break;

            case WAYPOINT: 
                waypointSequence.add(clicked);
                canvas.setActiveSelection(clicked);

                if (waypointSequence.size() == 1) {
                    statusLabel.setText("Path Start: " + clicked.getName());
                    pathDisplay.setText("Starting at " + clicked.getName() + "...\nSelect next stop.");
                } else {
                    List<Person> fullPath = service.findPathThroughWaypoints(waypointSequence);
                    if (fullPath.isEmpty()) {
                        waypointSequence.remove(clicked);
                        throw new Exception("Cannot reach " + clicked.getName() + " from previous node!");
                    }
                    canvas.setHighlightedPath(fullPath, waypointSequence);
                    showText("Custom Route");
                    statusLabel.setText("Path Extended. Total Steps: " + (fullPath.size()-1));
                    
                    StringBuilder sb = new StringBuilder();
                    sb.append("=== Custom Route ===\n\n");
                    for (int i = 0; i < fullPath.size(); i++) {
                        sb.append(i + 1).append(". ").append(fullPath.get(i).getName()).append("\n");
                    }
                    pathDisplay.setText(sb.toString());
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
            JOptionPane.showMessageDialog(this, "There is nobody on campus yet.");
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
            JOptionPane.showMessageDialog(this,
                    "No profile selected yet. Use Me ▸ Create My Profile, or sign in as an "
                            + "existing student.");
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
            JOptionPane.showMessageDialog(this,
                    "Tell us who you are first — Me ▸ Create My Profile, or sign in as an "
                            + "existing student.");
            return;
        }

        List<Suggestion> suggestions = recommender().recommend(me, 8);
        StringBuilder sb = new StringBuilder();
        sb.append("=== People ").append(me.getName()).append(" should meet ===\n\n");

        if (recommender().isColdStart(me)) {
            sb.append("You don't have many connections yet, so these are based\n")
              .append("entirely on your profile.\n\n");
        }
        if (suggestions.isEmpty()) {
            sb.append("Nothing yet — add a few more interests to your profile.\n");
        }
        for (int i = 0; i < suggestions.size(); i++) {
            Suggestion s = suggestions.get(i);
            sb.append(i + 1).append(". ").append(s.person().getName()).append('\n');
            sb.append("   ").append(s.explanation()).append('\n');
            sb.append(String.format("   match %.0f%%%n%n", s.score() * 100));
        }

        pathDisplay.setText(sb.toString());
        pathDisplay.setCaretPosition(0);
        sideTitle.setText("Who to meet");
        sideCards.show(sideStack, SIDE_TEXT);

        // Light the suggestions up on the canvas so the list maps onto the graph.
        List<Person> highlight = new ArrayList<>();
        for (Suggestion s : suggestions) highlight.add(s.person());
        canvas.setSuggested(highlight);
        statusLabel.setText(suggestions.size() + " suggestions for " + me.getName());
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

    /** Switches the side panel back to algorithm output. */
    private void showText(String title) {
        sideTitle.setText(title);
        sideCards.show(sideStack, SIDE_TEXT);
    }

    private void resetSelection() {
        selection = null;
        canvas.setActiveSelection(null);
    }

    private void resetWaypointsButKeepLast(Person last) {
        resetSelection();
        canvas.setActiveSelection(last);
    }

    /**
     * Resets visual overlays (path highlights, bridges, heatmaps) without clearing graph data.
     */
    private void resetVisualState() {
        resetSelection();
        waypointSequence.clear();
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
            CampusSeed.load(service, canvas.getWidth(), canvas.getHeight());
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

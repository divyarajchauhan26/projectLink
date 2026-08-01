package CampusConnect.ui;

import CampusConnect.algorithm.*;
import CampusConnect.domain.UserNode;
import CampusConnect.service.GraphPersistence;
import CampusConnect.service.NetworkService;

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

    private NetworkService service;
    private NetworkCanvas canvas;
    private StatsPanel statsPanel;
    private JLabel statusLabel;
    private JTextArea pathDisplay; // The text box on the right

    // MODES for clicking on canvas
    private enum Mode { CONNECT, DISCONNECT, WAYPOINT, PATH, DELETE, VIEW, SET_WEIGHT }
    private Mode currentMode = Mode.VIEW;

    // STATE
    private UserNode selection = null;
    private List<UserNode> waypointSequence = new ArrayList<>();

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

        // --- Right Text Panel ---
        JPanel sidePanel = new JPanel(new BorderLayout());
        sidePanel.setPreferredSize(new Dimension(280, 0));
        sidePanel.setBackground(new Color(43, 43, 43));
        sidePanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel titleLbl = new JLabel("Output / Directions");
        titleLbl.setForeground(Color.WHITE);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        sidePanel.add(titleLbl, BorderLayout.NORTH);

        pathDisplay = new JTextArea();
        pathDisplay.setEditable(false);
        pathDisplay.setBackground(new Color(60, 63, 65));
        pathDisplay.setForeground(new Color(220, 220, 220));
        pathDisplay.setFont(new Font("Monospaced", Font.PLAIN, 12));
        pathDisplay.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scroll = new JScrollPane(pathDisplay);
        scroll.setBorder(null);
        sidePanel.add(scroll, BorderLayout.CENTER);

        add(sidePanel, BorderLayout.EAST);

        // --- Bottom Status ---
        statusLabel = new JLabel("Welcome to Campus Connect!");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        statusLabel.setForeground(new Color(200, 200, 200));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBackground(new Color(30, 30, 30));
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        add(statusPanel, BorderLayout.SOUTH);

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
            UserNode clicked = (UserNode) evt.getNewValue();
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
        
        itemPageRank.addActionListener(e -> {
            PageRank.compute(service.getAllUsers(), service.getAdjacencyList());
            canvas.setShowHeatmap(true);
            onGraphChanged("PageRank computed. Heatmap enabled.");
        });
        
        centBetw.addActionListener(e -> {
            Map<UserNode, Double> res = CentralityMetrics.betweennessCentrality(service.getAllUsers(), service.getAdjacencyList());
            res.forEach((u, v) -> u.setRank(v));
            canvas.setShowHeatmap(true);
            onGraphChanged("Betweenness Centrality computed.");
        });
        centClose.addActionListener(e -> {
            Map<UserNode, Double> res = CentralityMetrics.closenessCentrality(service.getAllUsers(), service.getAdjacencyList());
            res.forEach((u, v) -> u.setRank(v));
            canvas.setShowHeatmap(true);
            onGraphChanged("Closeness Centrality computed.");
        });
        centDeg.addActionListener(e -> {
            Map<UserNode, Double> res = CentralityMetrics.degreeCentrality(service.getAllUsers(), service.getAdjacencyList());
            res.forEach((u, v) -> u.setRank(v));
            canvas.setShowHeatmap(true);
            onGraphChanged("Degree Centrality computed.");
        });
        
        itemCommunity.addActionListener(e -> {
            CommunityDetection.detectCommunities(service.getAllUsers(), service.getAdjacencyList());
            canvas.setShowCommunities(true);
            onGraphChanged("Communities detected.");
        });
        
        itemBridges.addActionListener(e -> {
            List<UserNode[]> bridges = GraphAnalyzer.findBridges(service.getAllUsers(), service.getAdjacencyList());
            canvas.setBridges(bridges);
            Set<UserNode> articulation = GraphAnalyzer.findArticulationPoints(service.getAllUsers(), service.getAdjacencyList());
            pathDisplay.setText("=== Critical Network Components ===\n\n");
            pathDisplay.append("Bridges Found: " + bridges.size() + " (Highlighted in Red)\n");
            for (UserNode[] b : bridges) {
                pathDisplay.append(" - " + b[0].getName() + " <-> " + b[1].getName() + "\n");
            }
            pathDisplay.append("\nArticulation Points: " + articulation.size() + "\n");
            for (UserNode u : articulation) {
                pathDisplay.append(" - " + u.getName() + "\n");
            }
            canvas.repaint();
        });
        
        itemCliques.addActionListener(e -> {
            List<Set<UserNode>> cliques = GraphAnalyzer.findMaximalCliques(service.getAllUsers(), service.getAdjacencyList());
            cliques.sort((a,b) -> Integer.compare(b.size(), a.size())); // largest first
            pathDisplay.setText("=== Maximal Cliques (Size >= 2) ===\n\n");
            pathDisplay.append("Found " + cliques.size() + " cliques.\n\n");
            for (int i = 0; i < Math.min(10, cliques.size()); i++) {
                pathDisplay.append("Size " + cliques.get(i).size() + ": " + cliques.get(i).toString() + "\n");
            }
        });
        
        itemDiameter.addActionListener(e -> {
            GraphAnalyzer.DiameterResult res = GraphAnalyzer.computeDiameterAndRadius(service.getAllUsers(), service.getAdjacencyList());
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
            
            pathDisplay.setText("=== Recommendations for " + selection.getName() + " ===\n\n");
            for (FriendRecommender.Recommendation rec : recs) {
                pathDisplay.append(rec.user.getName() + "\nScore: " + String.format("%.3f", rec.score) + "\nReason: " + rec.reason + "\n\n");
            }
        });
        
        menuSocial.add(itemRecommend);
        
        // --- VIEW ---
        JMenu menuView = new JMenu("View");
        JMenuItem viewNormal = new JMenuItem("Normal View");
        JMenuItem viewHeatmap = new JMenuItem("Toggle Heatmap");
        JMenuItem viewCommunity = new JMenuItem("Toggle Communities");
        
        viewNormal.addActionListener(e -> {
            canvas.setShowCommunities(false);
            canvas.setShowHeatmap(false);
            canvas.setBridges(null);
        });
        viewHeatmap.addActionListener(e -> canvas.setShowHeatmap(true));
        viewCommunity.addActionListener(e -> canvas.setShowCommunities(true));
        
        menuView.add(viewNormal); menuView.add(viewHeatmap); menuView.add(viewCommunity);

        menuBar.add(menuFile);
        menuBar.add(menuAlgo);
        menuBar.add(menuSocial);
        menuBar.add(menuView);
        setJMenuBar(menuBar);
    }

    private void saveGraph() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                GraphPersistence.saveToJson(service, chooser.getSelectedFile());
                statusLabel.setText("Graph saved successfully.");
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
                GraphPersistence.loadFromJson(service, file);
                // Clear overlays only — resetView() would wipe the graph we just loaded.
                resetVisualState();
                onGraphChanged("Loaded " + service.getAllUsers().size() + " users from " + file.getName());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error loading: " + ex.getMessage());
            }
        }
    }
    
    private void exportCsv() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                GraphPersistence.exportToCsv(service, chooser.getSelectedFile());
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

    private void handleNodeClick(UserNode clicked) throws Exception {
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
                    List<UserNode> fullPath = service.findPathThroughWaypoints(waypointSequence);
                    if (fullPath.isEmpty()) {
                        waypointSequence.remove(clicked);
                        throw new Exception("Cannot reach " + clicked.getName() + " from previous node!");
                    }
                    canvas.setHighlightedPath(fullPath, waypointSequence);
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
                service.removeUser(clicked);
                onGraphChanged("Deleted " + clicked.getName());
                break;

            case VIEW:
                selection = clicked;
                canvas.setActiveSelection(selection);
                
                // Show info
                pathDisplay.setText("=== Node Info ===\n\n");
                pathDisplay.append("Name: " + clicked.getName() + "\n");
                pathDisplay.append("Degree: " + service.getConnections(clicked).size() + "\n");
                pathDisplay.append("PageRank: " + String.format("%.4f", clicked.getRank()) + "\n");
                pathDisplay.append("Community ID: " + clicked.getCommunityId() + "\n");
                pathDisplay.append("\nConnected to:\n");
                for (UserNode f : service.getConnections(clicked)) {
                    double w = service.getEdgeWeight(clicked, f);
                    pathDisplay.append(" - " + f.getName() + " (w=" + w + ")\n");
                }
                break;
        }
    }

    private void onGraphChanged(String statusMessage) {
        statusLabel.setText(statusMessage);
        statsPanel.updateStats();
        canvas.repaint();
    }

    private void resetSelection() {
        selection = null;
        canvas.setActiveSelection(null);
    }

    private void resetWaypointsButKeepLast(UserNode last) {
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
     * Builds a rich default campus social network with 25 students and ~50 connections.
     */
    private void loadDefaultGraph() {
        try {
            int w = Math.max(800, canvas.getWidth());
            int h = Math.max(600, canvas.getHeight());

            // === STUDENTS ===
            String[] names = {
                "Alice", "Bob", "Charlie", "Diana", "Eve",
                "Frank", "Grace", "Hank", "Ivy", "Jack",
                "Karen", "Leo", "Mia", "Noah", "Olivia",
                "Pete", "Quinn", "Ryan", "Sara", "Tom",
                "Uma", "Victor", "Wendy", "Xavier", "Yara"
            };

            for (String name : names) {
                service.addRandomUser(name, w, h);
            }

            // === CONNECTIONS (building a realistic campus network) ===
            // Computer Science friend group
            connect("Alice", "Bob"); connect("Alice", "Charlie"); connect("Bob", "Charlie");
            connect("Alice", "Diana"); connect("Bob", "Eve"); connect("Charlie", "Eve");
            connect("Diana", "Eve");

            // Arts & Music friend group
            connect("Frank", "Grace"); connect("Grace", "Hank"); connect("Frank", "Hank");
            connect("Frank", "Ivy"); connect("Grace", "Ivy");

            // Sports team
            connect("Jack", "Karen"); connect("Jack", "Leo"); connect("Karen", "Leo");
            connect("Jack", "Mia"); connect("Leo", "Mia"); connect("Karen", "Noah");

            // Study group
            connect("Noah", "Olivia"); connect("Olivia", "Pete"); connect("Pete", "Quinn");
            connect("Noah", "Quinn"); connect("Olivia", "Quinn");

            // Dorm friends
            connect("Ryan", "Sara"); connect("Sara", "Tom"); connect("Ryan", "Tom");
            connect("Tom", "Uma"); connect("Uma", "Victor");
            connect("Victor", "Wendy"); connect("Wendy", "Xavier");
            connect("Xavier", "Yara"); connect("Yara", "Ryan");

            // Cross-group bridges (makes the graph interesting for analysis)
            connect("Charlie", "Frank");   // CS <-> Arts
            connect("Eve", "Jack");         // CS <-> Sports
            connect("Hank", "Noah");        // Arts <-> Study
            connect("Mia", "Olivia");       // Sports <-> Study
            connect("Quinn", "Ryan");       // Study <-> Dorm
            connect("Ivy", "Sara");         // Arts <-> Dorm
            connect("Leo", "Victor");       // Sports <-> Dorm
            connect("Diana", "Wendy");      // CS <-> Dorm
            connect("Bob", "Grace");        // CS <-> Arts
            connect("Karen", "Pete");       // Sports <-> Study
            connect("Alice", "Yara");       // CS <-> Dorm
            connect("Uma", "Hank");         // Dorm <-> Arts

            // A few weighted "close friend" edges
            service.setEdgeWeight(find("Alice"), find("Bob"), 3.0);
            service.setEdgeWeight(find("Frank"), find("Grace"), 2.5);
            service.setEdgeWeight(find("Jack"), find("Karen"), 2.0);
            service.setEdgeWeight(find("Ryan"), find("Sara"), 3.0);
            service.setEdgeWeight(find("Noah"), find("Olivia"), 2.0);

        } catch (Exception e) {
            // A partial graph silently corrupts every metric downstream — say so loudly.
            statusLabel.setText("Demo graph incomplete: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Could not build the full demo network:\n" + e.getMessage()
                            + "\n\nLoaded " + service.getAllUsers().size() + " of 25 students.",
                    "Incomplete Demo Graph", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void connect(String a, String b) {
        UserNode u = find(a), v = find(b);
        if (u != null && v != null) {
            try { service.addConnection(u, v); } catch (Exception ignored) {}
        }
    }

    private UserNode find(String name) {
        return service.findUserByName(name);
    }
}
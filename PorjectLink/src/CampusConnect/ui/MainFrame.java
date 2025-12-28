package CampusConnect.ui;

import CampusConnect.domain.UserNode;
import CampusConnect.service.NetworkService;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainFrame extends JFrame {

    private NetworkService service;
    private NetworkCanvas canvas;
    private JLabel statusLabel;
    private JTextArea pathDisplay; // New: The "GPS" text box

    // MODES
    private enum Mode { CONNECT, WAYPOINT, PATH, DELETE, VIEW }
    private Mode currentMode = Mode.VIEW;

    // STATE
    private UserNode selection = null;
    private List<UserNode> waypointSequence = new ArrayList<>();

    public MainFrame() {
        this.service = new NetworkService();
        this.canvas = new NetworkCanvas(service);

        setTitle("Campus Connect - Social Graph Admin");
        setSize(1200, 800);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // --- Toolbar ---
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBackground(new Color(60, 63, 65));

        JButton btnAdd = new JButton("Add User");
        btnAdd.setBackground(new Color(46, 204, 113));
        btnAdd.setForeground(Color.WHITE);

        ButtonGroup group = new ButtonGroup();
        // Separated Shortest Path and Waypoints completely
        JToggleButton btnConnect = createToggle(group, "Connect (Add Link)", Mode.CONNECT);
        JToggleButton btnPath = createToggle(group, "Shortest Path (A to B)", Mode.PATH);
        JToggleButton btnWaypoint = createToggle(group, "Custom Route (Via...)", Mode.WAYPOINT);
        JToggleButton btnDelete = createToggle(group, "Delete", Mode.DELETE);

        JButton btnReset = new JButton("Reset View");
        btnReset.addActionListener(e -> resetView());

        toolBar.add(btnAdd);
        toolBar.addSeparator();
        toolBar.add(btnConnect);
        toolBar.add(btnDelete);
        toolBar.addSeparator();
        toolBar.add(btnPath); // The "Whole different feature"
        toolBar.add(btnWaypoint);
        toolBar.add(Box.createHorizontalGlue());
        toolBar.add(btnReset);

        add(toolBar, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);

        // --- NEW: SIDE PANEL FOR "DIRECTIONS" ---
        JPanel sidePanel = new JPanel(new BorderLayout());
        sidePanel.setPreferredSize(new Dimension(250, 0));
        sidePanel.setBackground(new Color(43, 43, 43));
        sidePanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel titleLbl = new JLabel("Path Directions");
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

        // --- BOTTOM STATUS ---
        statusLabel = new JLabel("Welcome! Click 'Add User' to start.");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        statusLabel.setForeground(new Color(200, 200, 200));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBackground(new Color(30, 30, 30));
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        add(statusPanel, BorderLayout.SOUTH);

        // --- LOGIC ---
        btnAdd.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Enter Student Name:");
            if (name != null && !name.trim().isEmpty()) {
                try {
                    service.addRandomUser(name, canvas.getWidth(), canvas.getHeight());
                    canvas.repaint();
                    statusLabel.setText("Added " + name);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage());
                }
            }
        });

        canvas.addPropertyChangeListener("nodeClicked", evt -> {
            UserNode clicked = (UserNode) evt.getNewValue();
            try {
                handleNodeClick(clicked);
            } catch (Exception ex) {
                statusLabel.setText("Error: " + ex.getMessage());
                JOptionPane.showMessageDialog(this, "Operation failed: " + ex.getMessage());
                if (currentMode == Mode.WAYPOINT) resetWaypointsButKeepLast(clicked);
            }
            canvas.repaint();
        });
    }

    private JToggleButton createToggle(ButtonGroup group, String name, Mode mode) {
        JToggleButton btn = new JToggleButton(name);
        btn.addActionListener(e -> {
            currentMode = mode;
            resetView();
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
                    canvas.setActiveSelection(selection); // Dotted Highlight
                    statusLabel.setText("Connecting: Selected " + clicked.getName());
                } else {
                    service.addConnection(selection, clicked);
                    statusLabel.setText("Connected " + selection.getName() + " & " + clicked.getName());
                    resetSelection();
                }
                break;

            case PATH: // Shortest Path (A to B only)
                if (selection == null) {
                    selection = clicked;
                    canvas.setActiveSelection(selection); // Dotted Highlight
                    statusLabel.setText("Start: " + clicked.getName() + ". Click End.");
                    pathDisplay.setText("Select destination...");
                } else {
                    List<UserNode> path = service.findShortestPath(selection, clicked);
                    canvas.setHighlightedPath(path, null);
                    statusLabel.setText("Shortest Path Found: " + (path.size()-1) + " hops.");
                    printPathToText(path, "Shortest Path");
                    resetSelection();
                }
                break;

            case WAYPOINT: // Custom Route (A -> B -> C...)
                waypointSequence.add(clicked);
                // ALWAYS Highlight the latest node to show "We are here"
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
                    printPathToText(fullPath, "Custom Route");
                }
                break;

            case DELETE:
                service.removeUser(clicked);
                statusLabel.setText("Deleted " + clicked.getName());
                break;
        }
    }

    private void printPathToText(List<UserNode> path, String title) {
        if (path.isEmpty()) {
            pathDisplay.setText("No path found.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(title).append(" ===\n\n");
        for (int i = 0; i < path.size(); i++) {
            sb.append(i + 1).append(". Go to ").append(path.get(i).getName()).append("\n");
            if (i < path.size() - 1) {
                sb.append("   |\n   v\n");
            }
        }
        sb.append("\n[DESTINATION REACHED]");
        pathDisplay.setText(sb.toString());
    }

    private void resetSelection() {
        selection = null;
        canvas.setActiveSelection(null);
    }

    private void resetWaypointsButKeepLast(UserNode last) {
        // Helper to recover from bad click
        resetSelection();
        canvas.setActiveSelection(last);
    }

    private void resetView() {
        resetSelection();
        waypointSequence.clear();
        canvas.setHighlightedPath(Collections.emptyList(), null);
        pathDisplay.setText("");
        statusLabel.setText("View reset. Select a mode.");
    }
}
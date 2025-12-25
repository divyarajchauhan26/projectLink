package CampusConnect.ui;

import CampusConnect.domain.UserNode;
import CampusConnect.service.NetworkService;
import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

public class MainFrame extends JFrame {

    private NetworkService service;
    private NetworkCanvas canvas;
    private JLabel statusLabel;

    private enum Mode { CONNECT, PATH, DELETE, VIEW }
    private Mode currentMode = Mode.VIEW;
    private UserNode selection = null;

    public MainFrame() {
        this.service = new NetworkService();
        this.canvas = new NetworkCanvas(service);

        setTitle("Campus Connect - Social Graph Admin");
        setSize(1100, 750);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // --- Toolbar ---
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBackground(new Color(60, 63, 65));

        // Note: "Add User" is now a regular JButton, not a Toggle
        JButton btnAdd = new JButton("Add User");
        btnAdd.setBackground(new Color(46, 204, 113)); // Green button to stand out
        btnAdd.setForeground(Color.WHITE);

        ButtonGroup group = new ButtonGroup();
        // We only toggle between logic modes, not "Add" anymore
        JToggleButton btnConnect = createToggle(group, "Connect", Mode.CONNECT);
        JToggleButton btnDelete = createToggle(group, "Delete", Mode.DELETE);
        JToggleButton btnPath = createToggle(group, "Find Path", Mode.PATH);

        JButton btnReset = new JButton("Reset View");
        btnReset.addActionListener(e -> resetView());

        toolBar.add(btnAdd);
        toolBar.addSeparator();
        toolBar.add(btnConnect);
        toolBar.add(btnDelete);
        toolBar.addSeparator();
        toolBar.add(btnPath);
        toolBar.add(Box.createHorizontalGlue());
        toolBar.add(btnReset);

        add(toolBar, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);

        statusLabel = new JLabel("Welcome! Click 'Add User' to start.");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        add(statusLabel, BorderLayout.SOUTH);

        // --- ACTION 1: INSTANT ADD USER ---
        btnAdd.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Enter Student Name:");
            if (name != null && !name.trim().isEmpty()) {
                try {
                    // Automatically find a spot using Canvas dimensions
                    service.addRandomUser(name, canvas.getWidth(), canvas.getHeight());
                    canvas.repaint();
                    statusLabel.setText("Added " + name);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage());
                }
            }
        });

        // --- ACTION 2: LISTENER FOR CLICKS ---
        canvas.addPropertyChangeListener("nodeClicked", evt -> {
            UserNode clicked = (UserNode) evt.getNewValue();
            try {
                handleNodeClick(clicked);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage());
                resetSelection();
            }
            canvas.repaint();
        });
    }

    private JToggleButton createToggle(ButtonGroup group, String name, Mode mode) {
        JToggleButton btn = new JToggleButton(name);
        btn.addActionListener(e -> {
            currentMode = mode;
            resetSelection();
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
                    canvas.setActiveSelection(selection); // SHOW DOTTED CIRCLE
                    statusLabel.setText("Selected " + clicked.getName() + ". Click friend to connect.");
                } else {
                    service.addConnection(selection, clicked);
                    statusLabel.setText("Connected " + selection.getName() + " & " + clicked.getName());
                    resetSelection();
                }
                break;
            case PATH:
                if (selection == null) {
                    selection = clicked;
                    canvas.setActiveSelection(selection); // SHOW DOTTED CIRCLE
                    statusLabel.setText("Start: " + clicked.getName() + ". Click Destination.");
                } else {
                    List<UserNode> path = service.findShortestPath(selection, clicked);
                    canvas.setHighlightedPath(path);
                    statusLabel.setText(path.isEmpty() ? "No path found." : "Path found! Steps: " + (path.size()-1));
                    resetSelection();
                }
                break;
            case DELETE:
                service.removeUser(clicked);
                statusLabel.setText("Deleted " + clicked.getName());
                break;
        }
    }

    private void resetSelection() {
        selection = null;
        canvas.setActiveSelection(null); // Hide dotted circle
    }

    private void resetView() {
        resetSelection();
        canvas.setHighlightedPath(Collections.emptyList());
        statusLabel.setText("View reset.");
    }
}
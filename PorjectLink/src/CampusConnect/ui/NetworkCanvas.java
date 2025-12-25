package CampusConnect.ui;

import CampusConnect.model.GraphModel;
import CampusConnect.model.UserNode;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

public class NetworkCanvas extends JPanel {
    private GraphModel graph;

    // State variables for interaction
    private UserNode selectedToDrag = null; // Node we are moving
    private UserNode connectionSource = null; // Node we want to connect FROM

    public NetworkCanvas(GraphModel graph) {
        this.graph = graph;
        this.setBackground(new Color(30, 30, 30));

        // MOUSE LOGIC
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                UserNode clickedNode = findNodeAt(e.getPoint());

                if (clickedNode != null) {
                    // LOGIC: If we are holding 'Control' key, we drag.
                    // Otherwise, we are trying to connect.
                    if (e.isControlDown()) {
                        selectedToDrag = clickedNode;
                    } else {
                        handleConnectionClick(clickedNode);
                    }
                } else {
                    // Clicked empty space -> deselect everything
                    connectionSource = null;
                }
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                selectedToDrag = null; // Stop dragging
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                // Only allow dragging if we aren't in "Connect Mode"
                if (selectedToDrag != null) {
                    // Update position but check bounds roughly
                    selectedToDrag.setPosition(e.getX(), e.getY());
                    repaint();
                }
            }
        });
    }

    // Helper to find who was clicked
    private UserNode findNodeAt(Point p) {
        for (UserNode node : graph.getUsers()) {
            if (node.contains(p)) return node;
        }
        return null;
    }

    // THE MAGIC: Handles the "Connect A to B" logic
    private void handleConnectionClick(UserNode clickedNode) {
        if (connectionSource == null) {
            // Step 1: Select the first person
            connectionSource = clickedNode;
        } else {
            // Step 2: We already have a source, so connect to this new one!
            graph.addConnection(connectionSource, clickedNode);
            connectionSource = null; // Reset selection
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 1. Draw Links
        g2.setColor(Color.GRAY);
        g2.setStroke(new BasicStroke(2));
        for (UserNode user : graph.getUsers()) {
            for (UserNode friend : graph.getConnections(user)) {
                g2.drawLine(user.getX(), user.getY(), friend.getX(), friend.getY());
            }
        }

        // 2. Draw Nodes
        int radius = 20;
        for (UserNode user : graph.getUsers()) {
            // Highlight the "Source" node in Red if we are trying to connect
            if (user == connectionSource) {
                g2.setColor(Color.RED);
            } else {
                g2.setColor(new Color(100, 149, 237)); // Standard Blue
            }

            g2.fillOval(user.getX() - radius, user.getY() - radius, radius * 2, radius * 2);

            // Draw Name
            g2.setColor(Color.WHITE);
            g2.drawString(user.getName(), user.getX() - 10, user.getY() - 25);
        }
    }
}
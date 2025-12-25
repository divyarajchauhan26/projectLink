package CampusConnect.ui;

import CampusConnect.domain.UserNode;
import CampusConnect.service.NetworkService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.List;
import java.util.ArrayList;

public class NetworkCanvas extends JPanel {

    private NetworkService service;
    private UserNode selectedToDrag = null;

    // VISUAL STATES
    private List<UserNode> highlightedPath = new ArrayList<>();
    private UserNode activeSelection = null; // The node waiting to be connected

    public NetworkCanvas(NetworkService service) {
        this.service = service;
        this.setBackground(new Color(43, 43, 43));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                UserNode clicked = findNodeAt(e.getPoint());
                if (clicked != null) {
                    if (e.isControlDown()) {
                        selectedToDrag = clicked;
                    } else {
                        firePropertyChange("nodeClicked", null, clicked);
                    }
                }
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) { selectedToDrag = null; }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (selectedToDrag != null) {
                    selectedToDrag.setPosition(e.getX(), e.getY());
                    repaint();
                }
            }
        });
    }

    // Called by MainFrame to show the "Dotted Circle"
    public void setActiveSelection(UserNode node) {
        this.activeSelection = node;
        repaint();
    }

    public void setHighlightedPath(List<UserNode> path) {
        this.highlightedPath = path;
        repaint();
    }

    private UserNode findNodeAt(Point p) {
        for (UserNode u : service.getAllUsers()) {
            if (u.contains(p)) return u;
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 1. CONNECTIONS
        g2.setStroke(new BasicStroke(1));
        g2.setColor(new Color(100, 100, 100));
        for (UserNode u : service.getAllUsers()) {
            for (UserNode friend : service.getConnections(u)) {
                g2.drawLine(u.getX(), u.getY(), friend.getX(), friend.getY());
            }
        }

        // 2. PATH HIGHLIGHT
        if (highlightedPath != null && highlightedPath.size() > 1) {
            g2.setStroke(new BasicStroke(4));
            g2.setColor(new Color(46, 204, 113));
            for (int i = 0; i < highlightedPath.size() - 1; i++) {
                UserNode u1 = highlightedPath.get(i);
                UserNode u2 = highlightedPath.get(i + 1);
                g2.drawLine(u1.getX(), u1.getY(), u2.getX(), u2.getY());
            }
        }

        // 3. NODES
        for (UserNode u : service.getAllUsers()) {
            // Base Color
            if (highlightedPath.contains(u)) {
                g2.setColor(new Color(46, 204, 113)); // Green (Path)
            } else if (u.equals(activeSelection)) {
                g2.setColor(new Color(230, 126, 34)); // Orange (Selected)
            } else {
                g2.setColor(new Color(52, 152, 219)); // Blue (Normal)
            }

            g2.fillOval(u.getX() - 20, u.getY() - 20, 40, 40);

            // SPECIAL DOTTED RING for Active Selection
            if (u.equals(activeSelection)) {
                Stroke dashed = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5}, 0);
                g2.setStroke(dashed);
                g2.setColor(Color.WHITE);
                g2.drawOval(u.getX() - 24, u.getY() - 24, 48, 48); // Draw ring outside
            }

            // Name Label
            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g2.setColor(Color.WHITE);
            g2.drawString(u.getName(), u.getX() - 15, u.getY() - 25);
        }
    }
}
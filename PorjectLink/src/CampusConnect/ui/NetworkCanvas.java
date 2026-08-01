package CampusConnect.ui;

import CampusConnect.domain.NodeMetrics;
import CampusConnect.domain.Person;
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
    private Person selectedToDrag = null;

    // VISUAL STATES
    private List<Person> highlightedPath = new ArrayList<>();
    private List<Person> waypoints = new ArrayList<>();
    private Person activeSelection = null;
    
    // NEW VISUAL STATES
    private boolean showCommunities = false;
    private boolean showHeatmap = false;
    /** Which metric the heatmap is currently colouring by. */
    private NodeMetrics.Metric heatmapMetric = NodeMetrics.Metric.PAGE_RANK;
    private List<Person[]> bridges = new ArrayList<>();
    private List<Person> visualizationStep = null; // For step-by-step animation

    // Color palette for communities
    private static final Color[] COMMUNITY_COLORS = {
        new Color(52, 152, 219),  // Blue
        new Color(46, 204, 113),  // Green
        new Color(155, 89, 182),  // Purple
        new Color(241, 196, 15),  // Yellow
        new Color(230, 126, 34),  // Orange
        new Color(231, 76, 60),   // Red
        new Color(26, 188, 156),  // Turquoise
        new Color(52, 73, 94),    // Dark Blue
        new Color(255, 105, 180), // Hot Pink
        new Color(139, 69, 19)    // Saddle Brown
    };

    public NetworkCanvas(NetworkService service) {
        this.service = service;
        this.setBackground(new Color(43, 43, 43));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Person clicked = findNodeAt(e.getPoint());
                if (clicked != null) {
                    if (e.isControlDown()) {
                        selectedToDrag = clicked;
                    } else {
                        firePropertyChange("nodeClicked", null, clicked);
                    }
                } else {
                    // Clicked on empty canvas, fire null to allow deselection if needed
                    firePropertyChange("canvasClicked", null, e.getPoint());
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
                    selectedToDrag.dx = 0;
                    selectedToDrag.dy = 0;
                    repaint();
                }
            }
        });
    }

    // --- State Setters ---

    public void setActiveSelection(Person node) {
        this.activeSelection = node;
        repaint();
    }

    public void setHighlightedPath(List<Person> path, List<Person> currentWaypoints) {
        this.highlightedPath = path;
        this.waypoints = currentWaypoints != null ? currentWaypoints : new ArrayList<>();
        repaint();
    }

    public void setShowCommunities(boolean show) {
        this.showCommunities = show;
        if (show) showHeatmap = false;
        repaint();
    }

    public void setShowHeatmap(boolean show) {
        this.showHeatmap = show;
        if (show) showCommunities = false;
        repaint();
    }

    /**
     * Show the heatmap coloured by a specific metric. Previously the heatmap always read
     * a single shared field, so running betweenness produced a map still labelled
     * "PageRank" — the colours and the legend disagreed.
     */
    public void setHeatmapMetric(NodeMetrics.Metric metric) {
        this.heatmapMetric = metric;
        this.showHeatmap = true;
        this.showCommunities = false;
        repaint();
    }

    public NodeMetrics.Metric getHeatmapMetric() { return heatmapMetric; }

    public void setBridges(List<Person[]> bridges) {
        this.bridges = bridges != null ? bridges : new ArrayList<>();
        repaint();
    }
    
    public void setVisualizationStep(List<Person> nodes) {
        this.visualizationStep = nodes;
        repaint();
    }

    // --- Helpers ---

    private Person findNodeAt(Point p) {
        for (Person u : service.getAllUsers()) {
            if (u.contains(p)) return u;
        }
        return null;
    }

    private Color getHeatmapColor(double value) {
        // value should be 0.0 to 1.0. 
        // 0.0 -> Blue, 0.5 -> Yellow, 1.0 -> Red
        value = Math.max(0.0, Math.min(1.0, value));
        if (value < 0.5) {
            // Blue to Yellow
            double t = value * 2.0;
            int r = (int)(52 * (1-t) + 241 * t);
            int g = (int)(152 * (1-t) + 196 * t);
            int b = (int)(219 * (1-t) + 15 * t);
            return new Color(r, g, b);
        } else {
            // Yellow to Red
            double t = (value - 0.5) * 2.0;
            int r = (int)(241 * (1-t) + 231 * t);
            int g = (int)(196 * (1-t) + 76 * t);
            int b = (int)(15 * (1-t) + 60 * t);
            return new Color(r, g, b);
        }
    }

    // --- Painting ---

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 1. DRAW ALL CONNECTIONS
        g2.setStroke(new BasicStroke(1));
        for (Person u : service.getAllUsers()) {
            for (Person friend : service.getConnections(u)) {
                // Avoid drawing twice for undirected by only drawing if u.id < friend.id
                if (u.getId().compareTo(friend.getId()) < 0) {
                    
                    boolean isBridge = false;
                    for (Person[] b : bridges) {
                        if ((b[0].equals(u) && b[1].equals(friend)) || (b[0].equals(friend) && b[1].equals(u))) {
                            isBridge = true; break;
                        }
                    }

                    if (isBridge) {
                        g2.setColor(new Color(231, 76, 60)); // Red for bridges
                        g2.setStroke(new BasicStroke(3));
                    } else {
                        g2.setColor(new Color(100, 100, 100)); // Default grey
                        g2.setStroke(new BasicStroke(1));
                    }
                    
                    g2.drawLine(u.getX(), u.getY(), friend.getX(), friend.getY());
                    
                    // Optional: Draw edge weights
                    double weight = service.getEdgeWeight(u, friend);
                    if (weight != 1.0) {
                        int mx = (u.getX() + friend.getX()) / 2;
                        int my = (u.getY() + friend.getY()) / 2;
                        g2.setColor(new Color(180, 180, 180));
                        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                        g2.drawString(String.format("%.1f", weight), mx, my);
                    }
                }
            }
        }

        // 2. PATH HIGHLIGHT
        if (highlightedPath != null && highlightedPath.size() > 1) {
            g2.setStroke(new BasicStroke(4));
            g2.setColor(new Color(46, 204, 113));
            for (int i = 0; i < highlightedPath.size() - 1; i++) {
                Person u1 = highlightedPath.get(i);
                Person u2 = highlightedPath.get(i + 1);
                g2.drawLine(u1.getX(), u1.getY(), u2.getX(), u2.getY());
            }
        }

        // 3. NODES
        for (Person u : service.getAllUsers()) {
            
            // Determine Node Color
            if (u.equals(activeSelection)) {
                g2.setColor(new Color(255, 255, 255)); // White (Active)
            } else if (waypoints.contains(u)) {
                g2.setColor(new Color(241, 196, 15)); // Yellow (Waypoint)
            } else if (highlightedPath != null && highlightedPath.contains(u)) {
                g2.setColor(new Color(46, 204, 113)); // Green (Path)
            } else if (visualizationStep != null && visualizationStep.contains(u)) {
                g2.setColor(new Color(155, 89, 182)); // Purple (Visualization)
            } else if (showHeatmap) {
                g2.setColor(getHeatmapColor(u.getMetrics().get(heatmapMetric)));
            } else if (showCommunities && u.getMetrics().getCommunityId() >= 0) {
                int cId = u.getMetrics().getCommunityId() % COMMUNITY_COLORS.length;
                g2.setColor(COMMUNITY_COLORS[cId]);
            } else {
                g2.setColor(new Color(52, 152, 219)); // Blue (Normal)
            }

            g2.fillOval(u.getX() - 20, u.getY() - 20, 40, 40);

            // Dotted Ring for selection
            if (u.equals(activeSelection)) {
                Stroke dashed = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5}, 0);
                g2.setStroke(dashed);
                g2.setColor(Color.WHITE);
                g2.drawOval(u.getX() - 24, u.getY() - 24, 48, 48);
            }
            
            // Halo for visualization step
            if (visualizationStep != null && visualizationStep.contains(u)) {
                g2.setStroke(new BasicStroke(3));
                g2.setColor(new Color(155, 89, 182, 128)); // Semi-transparent purple
                g2.drawOval(u.getX() - 28, u.getY() - 28, 56, 56);
            }

            // Name Label
            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g2.setColor(Color.WHITE);
            g2.drawString(u.getName(), u.getX() - 15, u.getY() - 25);
        }
    }
}
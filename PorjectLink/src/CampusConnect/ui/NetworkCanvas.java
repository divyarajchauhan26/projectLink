package CampusConnect.ui;

import CampusConnect.domain.Category;
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
    /** Node radius. Hit-testing in {@link Person#contains} uses the same 20px. */
    private static final int NODE_R = 20;

    private Person currentUser = null;              // "You" — drawn with a gold ring
    private List<Person> suggested = new ArrayList<>(); // Ghost-edge targets

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
        this.setBackground(Theme.BG);

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

    /** Marks one node as "you", so the canvas has a point of reference. */
    public void setCurrentUser(Person person) {
        this.currentUser = person;
        repaint();
    }

    public Person getCurrentUser() { return currentUser; }

    /**
     * People the recommender suggested. Drawn as dashed "ghost" edges from the current
     * user — connections that do not exist yet but could, which is the whole idea of the
     * affinity graph made visible.
     */
    public void setSuggested(List<Person> people) {
        this.suggested = people != null ? people : new ArrayList<>();
        repaint();
    }

    public void clearSuggested() { setSuggested(null); }

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

    /**
     * Cold-to-hot ramp for the heatmap.
     * <p>
     * Deep indigo through violet to amber, rather than the old blue-yellow-red. Red and
     * green were carrying two other meanings on this canvas already — fragile links and
     * highlighted routes — so a ramp that ran through both made a hot node and a broken
     * connection look like the same statement.
     */
    private Color getHeatmapColor(double value) {
        value = Math.max(0.0, Math.min(1.0, value));
        Color cold = new Color(0x2A3356);
        Color mid  = new Color(0x7C6BD6);
        Color hot  = new Color(0xF0A94E);
        return value < 0.5
                ? Theme.mix(cold, mid, value * 2.0)
                : Theme.mix(mid, hot, (value - 0.5) * 2.0);
    }

    /** The fill for a node, resolved by precedence: explicit states beat overlays. */
    private Color nodeColour(Person u) {
        if (highlightedPath != null && highlightedPath.contains(u)) return Theme.PATH;
        if (visualizationStep != null && visualizationStep.contains(u)) return Theme.ACCENT_DEEP;
        if (showHeatmap) return getHeatmapColor(u.getMetrics().get(heatmapMetric));
        if (showCommunities && u.getMetrics().getCommunityId() >= 0) {
            return COMMUNITY_COLORS[u.getMetrics().getCommunityId() % COMMUNITY_COLORS.length];
        }
        // Otherwise tint by what the person is mostly into, so the plain view still
        // carries information instead of being a field of identical blue dots.
        Category dominant = u.getDominantCategory();
        return dominant == null ? Theme.NODE
                : Theme.mix(new Color(dominant.getRgb()), Theme.BG, 0.30);
    }

    // --- Painting ---

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

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

                    double weight = service.getEdgeWeight(u, friend);

                    if (isBridge) {
                        g2.setColor(Theme.EDGE_FRAGILE);
                        g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    } else {
                        // Closer friendships draw brighter and thicker, so the strength
                        // that already drives warm-intro routing is visible rather than
                        // hidden behind a number printed on the line.
                        double strength = Math.max(0.5, Math.min(3.0, weight)) / 3.0;
                        g2.setColor(Theme.mix(Theme.EDGE, Theme.mix(Theme.EDGE, Theme.TEXT_DIM, 0.5), strength));
                        g2.setStroke(new BasicStroke((float) (0.9 + strength * 1.6),
                                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    }

                    // Weight is no longer printed on the line. A number floating
                    // mid-edge collided with every other edge crossing it, and the
                    // stroke now carries the same information continuously.
                    g2.drawLine(u.getX(), u.getY(), friend.getX(), friend.getY());
                }
            }
        }

        // 1b. GHOST EDGES — suggested connections that do not exist yet
        if (currentUser != null && !suggested.isEmpty()) {
            Stroke ghost = new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    0, new float[]{6, 8}, 0);
            g2.setStroke(ghost);
            g2.setColor(new Color(46, 204, 113, 150));
            for (Person target : suggested) {
                g2.drawLine(currentUser.getX(), currentUser.getY(), target.getX(), target.getY());
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
            int x = u.getX(), y = u.getY();
            Color fill = nodeColour(u);
            boolean isSuggested = currentUser != null && suggested.contains(u);

            // Soft halo behind anything the engine surfaced, so the eye finds the
            // app's own suggestions before it finds anything else on the canvas.
            if (isSuggested) {
                g2.setColor(Theme.alpha(Theme.ACCENT, 34));
                g2.fillOval(x - 30, y - 30, 60, 60);
            }

            // Body, with a darker rim so nodes stay legible against a light heatmap.
            g2.setColor(fill);
            g2.fillOval(x - NODE_R, y - NODE_R, NODE_R * 2, NODE_R * 2);
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(Theme.mix(fill, Color.BLACK, 0.35));
            g2.drawOval(x - NODE_R, y - NODE_R, NODE_R * 2, NODE_R * 2);

            // Rings, outermost first. Each says something different and they can stack:
            // you can be the current user AND selected AND suggested at once.
            if (isSuggested) {
                g2.setStroke(new BasicStroke(2f));
                g2.setColor(Theme.ACCENT);
                g2.drawOval(x - NODE_R - 5, y - NODE_R - 5, (NODE_R + 5) * 2, (NODE_R + 5) * 2);
            }
            if (u.equals(currentUser)) {
                g2.setStroke(new BasicStroke(2.5f));
                g2.setColor(Theme.YOU);
                g2.drawOval(x - NODE_R - 8, y - NODE_R - 8, (NODE_R + 8) * 2, (NODE_R + 8) * 2);
            }
            if (u.equals(activeSelection)) {
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        0, new float[]{4, 4}, 0));
                g2.setColor(Theme.TEXT);
                g2.drawOval(x - NODE_R - 11, y - NODE_R - 11, (NODE_R + 11) * 2, (NODE_R + 11) * 2);
            }

            // Avatar emoji sits inside the node; the initial is the fallback so a node
            // is never a blank disc.
            String emoji = u.getAvatarEmoji();
            if (emoji != null && !emoji.isBlank()) {
                g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 17));
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(Color.WHITE);
                g2.drawString(emoji, x - fm.stringWidth(emoji) / 2, y + fm.getAscent() / 2 - 3);
            } else {
                g2.setFont(Theme.title(14));
                FontMetrics fm = g2.getFontMetrics();
                String initial = u.getName().isEmpty() ? "?" : u.getName().substring(0, 1);
                g2.setColor(Theme.mix(fill, Color.WHITE, 0.85));
                g2.drawString(initial, x - fm.stringWidth(initial) / 2, y + fm.getAscent() / 2 - 3);
            }

            // Name, centred under the node rather than guessed with a fixed offset —
            // the old -15 left every long name visibly off to one side.
            g2.setFont(Theme.title(11));
            FontMetrics nameMetrics = g2.getFontMetrics();
            String label = u.getName();
            int labelX = x - nameMetrics.stringWidth(label) / 2;
            int labelY = y + NODE_R + 15;

            // A slab behind the text keeps names readable where edges run underneath.
            g2.setColor(Theme.alpha(Theme.BG, 190));
            g2.fillRoundRect(labelX - 4, labelY - nameMetrics.getAscent() - 1,
                    nameMetrics.stringWidth(label) + 8, nameMetrics.getHeight(), 6, 6);
            g2.setColor(u.equals(currentUser) ? Theme.YOU
                    : isSuggested ? Theme.ACCENT : Theme.TEXT);
            g2.drawString(label, labelX, labelY);
        }

        // 4. LEGEND
        if (showHeatmap) drawHeatmapLegend(g2);
        else if (showCommunities) drawCommunityLegend(g2);
    }

    /**
     * A colour ramp with its two ends labelled.
     * <p>
     * Without this the heatmap is just coloured dots — the viewer has no way to know
     * whether red means "most" or "least", nor which metric is being shown at all. That
     * was a real gap: choosing a different centrality repainted the canvas with no
     * indication anything had changed.
     */
    private void drawHeatmapLegend(Graphics2D g2) {
        final int x = 14, y = 14, w = 150, h = 12;

        g2.setColor(new Color(0, 0, 0, 140));
        g2.fillRect(x - 8, y - 8, w + 90, h + 44);

        for (int i = 0; i < w; i++) {
            g2.setColor(getHeatmapColor((double) i / (w - 1)));
            g2.drawLine(x + i, y, x + i, y + h);
        }
        g2.setColor(new Color(200, 200, 200));
        g2.drawRect(x, y, w, h);

        g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
        g2.setColor(Color.WHITE);
        String title = heatmapMetric.getLabel();
        if (heatmapMetric == NodeMetrics.Metric.SIMILARITY_TO_ME && currentUser != null) {
            title = "Similarity to " + currentUser.getName();
        }
        g2.drawString(title, x, y + h + 15);

        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        g2.setColor(new Color(180, 180, 180));
        g2.drawString("low", x, y + h + 29);
        String high = "high";
        g2.drawString(high, x + w - g2.getFontMetrics().stringWidth(high), y + h + 29);
    }

    /** Which community colour maps to which id, so the palette means something. */
    private void drawCommunityLegend(Graphics2D g2) {
        java.util.Set<Integer> ids = new java.util.TreeSet<>();
        for (Person p : service.getAllUsers()) {
            int id = p.getMetrics().getCommunityId();
            if (id >= 0) ids.add(id);
        }
        if (ids.isEmpty()) return;

        final int x = 14, y = 14, box = 11, step = 16;
        int rows = Math.min(ids.size(), COMMUNITY_COLORS.length);

        g2.setColor(new Color(0, 0, 0, 140));
        g2.fillRect(x - 8, y - 8, 120, rows * step + 26);

        g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
        g2.setColor(Color.WHITE);
        g2.drawString("Circles", x, y + 4);

        g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        int row = 0;
        for (int id : ids) {
            if (row >= COMMUNITY_COLORS.length) break;
            int ry = y + 14 + row * step;
            g2.setColor(COMMUNITY_COLORS[id % COMMUNITY_COLORS.length]);
            g2.fillRect(x, ry, box, box);
            g2.setColor(new Color(210, 210, 210));
            g2.drawString("Circle " + id, x + box + 6, ry + box - 1);
            row++;
        }
    }
}
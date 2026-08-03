package CampusConnect.ui;

import CampusConnect.domain.Category;
import CampusConnect.domain.NodeMetrics;
import CampusConnect.domain.Person;
import CampusConnect.service.NetworkService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The graph view: a pannable, zoomable map of the campus.
 * <p>
 * Everything inside {@link #paintWorld} draws in <b>world</b> coordinates through the
 * {@link Viewport} transform; everything in {@link #paintOverlay} draws in <b>screen</b>
 * coordinates so it stays put and stays legible whatever the zoom. Hit-testing converts
 * the mouse into world space first — doing that inconsistently is what makes a zoomable
 * canvas start clicking the wrong thing as soon as you zoom.
 */
public class NetworkCanvas extends JPanel {

    /** Node radius in world units. */
    static final int NODE_R = 20;

    private final NetworkService service;
    private final Viewport viewport = new Viewport();

    // --- interaction state ---
    private Person draggingNode = null;
    private Person hovered = null;
    private Point lastDragPoint = null;
    private boolean panning = false;

    /** First endpoint of a two-click action, so the canvas can rubber-band to the cursor. */
    private Person pendingLinkFrom = null;
    private Point cursor = null;

    // --- display state ---
    private List<Person> highlightedPath = new ArrayList<>();
    private Person activeSelection = null;
    private boolean showCommunities = false;
    private boolean showHeatmap = false;
    private NodeMetrics.Metric heatmapMetric = NodeMetrics.Metric.PAGE_RANK;
    private List<Person[]> bridges = new ArrayList<>();
    private List<Person> visualizationStep = null;
    private Person currentUser = null;
    private List<Person> suggested = new ArrayList<>();

    private static final Color[] COMMUNITY_COLORS = {
        new Color(0x4C8DD4), new Color(0x46C08A), new Color(0x9B72D0), new Color(0xE0B23C),
        new Color(0xE08A45), new Color(0xD75F6A), new Color(0x38B3A8), new Color(0x6A7FB5),
        new Color(0xD173A8), new Color(0xA1794E)
    };

    public NetworkCanvas(NetworkService service) {
        this.service = service;
        setBackground(Theme.BG);
        setFocusable(true);

        installMouse();
        installKeys();

        // The camera eases toward its target; this drives that without a second thread.
        new Timer(16, e -> { if (viewport.step()) repaint(); }).start();
    }

    // ================= input =================

    private void installMouse() {
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                Person hit = nodeAt(e.getPoint());
                lastDragPoint = e.getPoint();

                if (hit != null && (e.isControlDown() || SwingUtilities.isMiddleMouseButton(e))) {
                    draggingNode = hit;                       // reposition a node
                } else if (hit != null && SwingUtilities.isLeftMouseButton(e)) {
                    firePropertyChange("nodeClicked", null, hit);
                } else if (hit == null) {
                    // Empty space: begin a pan. Dragging the background to move the map is
                    // what everybody already expects from a map.
                    panning = true;
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    firePropertyChange("canvasClicked", null, e.getPoint());
                }
                repaint();
            }

            @Override public void mouseReleased(MouseEvent e) {
                draggingNode = null;
                panning = false;
                lastDragPoint = null;
                setCursor(Cursor.getDefaultCursor());
            }

            @Override public void mouseDragged(MouseEvent e) {
                cursor = e.getPoint();
                if (draggingNode != null) {
                    Point2D w = viewport.toWorld(e.getPoint());
                    draggingNode.setPosition((int) w.getX(), (int) w.getY());
                    repaint();
                } else if (panning && lastDragPoint != null) {
                    viewport.panBy(e.getX() - lastDragPoint.x, e.getY() - lastDragPoint.y);
                    lastDragPoint = e.getPoint();
                    repaint();
                }
            }

            @Override public void mouseMoved(MouseEvent e) {
                cursor = e.getPoint();
                Person under = nodeAt(e.getPoint());
                // Repaint only when the hovered node actually changes, otherwise every
                // mouse move repaints the whole graph.
                boolean changed = under != hovered;
                hovered = under;
                setCursor(under != null
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
                setToolTipText(under == null ? null : tooltipFor(under));
                if (changed || pendingLinkFrom != null) repaint();
            }

            @Override public void mouseExited(MouseEvent e) {
                hovered = null; cursor = null; repaint();
            }

            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                // Zoom toward the pointer, so what you are looking at is what you get.
                viewport.zoomAt(e.getPoint(), e.getWheelRotation() < 0 ? 1.12 : 1 / 1.12);
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    private void installKeys() {
        bind("F", "fit", () -> fitToView());
        bind("0", "reset", () -> { viewport.reset(); repaint(); });
        bind("EQUALS", "zoomIn", () -> zoomAtCentre(1.2));
        bind("PLUS", "zoomIn2", () -> zoomAtCentre(1.2));
        bind("MINUS", "zoomOut", () -> zoomAtCentre(1 / 1.2));
        bind("ESCAPE", "cancel", () -> firePropertyChange("cancelPending", false, true));
    }

    private void bind(String key, String name, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

    private void zoomAtCentre(double factor) {
        viewport.zoomAt(new Point(getWidth() / 2, getHeight() / 2), factor);
        repaint();
    }

    /** Hit-test in world space, so nodes stay clickable at any zoom. */
    private Person nodeAt(Point screen) {
        Point2D w = viewport.toWorld(screen);
        Person best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Person p : service.getAllUsers()) {
            double d = Point2D.distance(w.getX(), w.getY(), p.x, p.y);
            // Nearest wins, so overlapping nodes select predictably rather than by
            // whichever happened to come first in the list.
            if (d <= NODE_R && d < bestDistance) { bestDistance = d; best = p; }
        }
        return best;
    }

    private String tooltipFor(Person p) {
        StringBuilder sb = new StringBuilder("<html><b>").append(esc(p.getName())).append("</b>");
        if (p.getYear() > 0 || !p.getMajor().isBlank()) {
            sb.append("<br>").append(p.getYear() > 0 ? "Year " + p.getYear() + " " : "")
              .append(esc(p.getMajor()));
        }
        sb.append("<br>").append(service.getConnections(p).size()).append(" connections");
        if (!p.getInterests().isEmpty()) {
            sb.append("<br><i>");
            int n = 0;
            for (var t : p.getInterests()) {
                if (n++ > 0) sb.append(", ");
                if (n > 3) { sb.append("…"); break; }
                sb.append(esc(t.label()));
            }
            sb.append("</i>");
        }
        return sb.append("</html>").toString();
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;");
    }

    // ================= public API =================

    public Viewport getViewport() { return viewport; }

    public void fitToView() {
        viewport.fit(service.getAllUsers(), getWidth(), getHeight(), NODE_R);
        repaint();
    }

    public void focusOn(Person person) {
        viewport.focusOn(person, getWidth(), getHeight());
        repaint();
    }

    public void setActiveSelection(Person node) { activeSelection = node; repaint(); }

    public void setHighlightedPath(List<Person> path, List<Person> unusedWaypoints) {
        this.highlightedPath = path != null ? path : new ArrayList<>();
        repaint();
    }

    public void setShowCommunities(boolean show) {
        showCommunities = show;
        if (show) showHeatmap = false;
        repaint();
    }

    public void setShowHeatmap(boolean show) {
        showHeatmap = show;
        if (show) showCommunities = false;
        repaint();
    }

    /**
     * Show the heatmap coloured by a specific metric. The canvas previously read one
     * shared field, so running betweenness produced a map still labelled "PageRank".
     */
    public void setHeatmapMetric(NodeMetrics.Metric metric) {
        heatmapMetric = metric;
        showHeatmap = true;
        showCommunities = false;
        repaint();
    }

    public NodeMetrics.Metric getHeatmapMetric() { return heatmapMetric; }

    public void setCurrentUser(Person person) { currentUser = person; repaint(); }
    public Person getCurrentUser() { return currentUser; }

    /** People the engine suggested — drawn as dashed edges the graph does not have yet. */
    public void setSuggested(List<Person> people) {
        suggested = people != null ? people : new ArrayList<>();
        repaint();
    }

    public void clearSuggested() { setSuggested(null); }

    public void setBridges(List<Person[]> b) {
        bridges = b != null ? b : new ArrayList<>();
        repaint();
    }

    public void setVisualizationStep(List<Person> nodes) { visualizationStep = nodes; repaint(); }

    /** The node a two-click action started from, so the canvas can rubber-band to it. */
    public void setPendingLinkFrom(Person from) { pendingLinkFrom = from; repaint(); }

    // ================= painting =================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        AffineTransform saved = viewport.apply(g2);
        paintWorld(g2);
        g2.setTransform(saved);

        paintOverlay(g2);
    }

    /** Everything that lives at a place in the graph. */
    private void paintWorld(Graphics2D g2) {
        // Hovering focuses attention: the hovered node and its neighbours stay bright,
        // everything else fades. On a dense graph this is the difference between seeing
        // one person's world and seeing a hairball.
        Set<Person> focus = null;
        if (hovered != null) {
            focus = new HashSet<>(service.getConnections(hovered));
            focus.add(hovered);
        }

        drawEdges(g2, focus);
        drawGhostEdges(g2);
        drawPath(g2);
        drawPendingLink(g2);
        drawNodes(g2, focus);
    }

    private void drawEdges(Graphics2D g2, Set<Person> focus) {
        for (Person u : service.getAllUsers()) {
            for (Person v : service.getConnections(u)) {
                if (u.getId().compareTo(v.getId()) >= 0) continue; // undirected: draw once

                boolean isBridge = false;
                for (Person[] b : bridges) {
                    if ((b[0] == u && b[1] == v) || (b[0] == v && b[1] == u)) { isBridge = true; break; }
                }
                boolean lit = focus == null || (focus.contains(u) && focus.contains(v));

                if (isBridge) {
                    g2.setColor(fade(Theme.EDGE_FRAGILE, lit));
                    g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                } else {
                    // Stroke weight and brightness carry friendship strength, which is
                    // the same number that drives warm-intro routing.
                    double strength = Math.max(0.5, Math.min(3.0, service.getEdgeWeight(u, v))) / 3.0;
                    Color base = Theme.mix(Theme.EDGE, Theme.TEXT_DIM, strength * 0.6);
                    g2.setColor(fade(base, lit));
                    g2.setStroke(new BasicStroke((float) (1.0 + strength * 1.8),
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                }
                g2.drawLine(u.getX(), u.getY(), v.getX(), v.getY());
            }
        }
    }

    private void drawGhostEdges(Graphics2D g2) {
        if (currentUser == null || suggested.isEmpty()) return;
        g2.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                0, new float[]{7, 9}, 0));
        g2.setColor(Theme.alpha(Theme.EDGE_GHOST, 170));
        for (Person target : suggested) {
            g2.drawLine(currentUser.getX(), currentUser.getY(), target.getX(), target.getY());
        }
    }

    private void drawPath(Graphics2D g2) {
        if (highlightedPath == null || highlightedPath.size() < 2) return;
        g2.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(Theme.PATH);
        for (int i = 0; i < highlightedPath.size() - 1; i++) {
            Person a = highlightedPath.get(i), b = highlightedPath.get(i + 1);
            g2.drawLine(a.getX(), a.getY(), b.getX(), b.getY());
        }
    }

    /**
     * Rubber-band from the first-clicked node to the cursor.
     * <p>
     * Two-click modes used to give no feedback at all beyond a line of status text, so
     * after the first click there was nothing on screen saying the app was waiting.
     */
    private void drawPendingLink(Graphics2D g2) {
        if (pendingLinkFrom == null || cursor == null) return;
        Point2D w = viewport.toWorld(cursor);
        g2.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                0, new float[]{5, 5}, 0));
        g2.setColor(Theme.YOU);
        g2.drawLine(pendingLinkFrom.getX(), pendingLinkFrom.getY(),
                (int) w.getX(), (int) w.getY());
    }

    private void drawNodes(Graphics2D g2, Set<Person> focus) {
        for (Person u : service.getAllUsers()) {
            int x = u.getX(), y = u.getY();
            boolean lit = focus == null || focus.contains(u);
            boolean isSuggested = currentUser != null && suggested.contains(u);
            Color fill = fade(nodeColour(u), lit);

            if (isSuggested) {
                g2.setColor(Theme.alpha(Theme.ACCENT, lit ? 40 : 14));
                g2.fillOval(x - 32, y - 32, 64, 64);
            }
            if (u == hovered) {
                g2.setColor(Theme.alpha(Theme.TEXT, 30));
                g2.fillOval(x - 30, y - 30, 60, 60);
            }

            g2.setColor(fill);
            g2.fillOval(x - NODE_R, y - NODE_R, NODE_R * 2, NODE_R * 2);
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(fade(Theme.mix(nodeColour(u), Color.BLACK, 0.4), lit));
            g2.drawOval(x - NODE_R, y - NODE_R, NODE_R * 2, NODE_R * 2);

            // Rings stack outward; a node can be suggested AND you AND selected.
            if (isSuggested) ring(g2, x, y, NODE_R + 5, fade(Theme.ACCENT, lit), 2f, false);
            if (u == currentUser) ring(g2, x, y, NODE_R + 8, Theme.YOU, 2.5f, false);
            if (u == activeSelection) ring(g2, x, y, NODE_R + 11, Theme.TEXT, 2f, true);

            drawGlyph(g2, u, x, y, lit);
            drawLabel(g2, u, x, y, lit, isSuggested);
        }
    }

    private void ring(Graphics2D g2, int x, int y, int r, Color c, float w, boolean dashed) {
        g2.setStroke(dashed
                ? new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{4, 4}, 0)
                : new BasicStroke(w));
        g2.setColor(c);
        g2.drawOval(x - r, y - r, r * 2, r * 2);
    }

    private void drawGlyph(Graphics2D g2, Person u, int x, int y, boolean lit) {
        String emoji = u.getAvatarEmoji();
        if (emoji != null && !emoji.isBlank()) {
            g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 17));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(fade(Color.WHITE, lit));
            g2.drawString(emoji, x - fm.stringWidth(emoji) / 2, y + fm.getAscent() / 2 - 3);
        } else {
            // Initial rather than an empty disc, so a profile without an emoji still
            // reads as a person.
            g2.setFont(Theme.title(15));
            FontMetrics fm = g2.getFontMetrics();
            String initial = u.getName().isEmpty() ? "?" : u.getName().substring(0, 1).toUpperCase();
            g2.setColor(fade(Color.WHITE, lit));
            g2.drawString(initial, x - fm.stringWidth(initial) / 2, y + fm.getAscent() / 2 - 3);
        }
    }

    private void drawLabel(Graphics2D g2, Person u, int x, int y, boolean lit, boolean isSuggested) {
        // Names disappear when zoomed far out — at 40% they overlap into an unreadable
        // smear, and the shapes alone carry the structure.
        if (viewport.getZoom() < 0.5 && u != hovered && u != currentUser) return;

        g2.setFont(Theme.title(11));
        FontMetrics fm = g2.getFontMetrics();
        String label = u.getName();
        int lx = x - fm.stringWidth(label) / 2;
        int ly = y + NODE_R + 15;

        g2.setColor(Theme.alpha(Theme.BG, lit ? 200 : 90));
        g2.fillRoundRect(lx - 5, ly - fm.getAscent() - 2, fm.stringWidth(label) + 10,
                fm.getHeight() + 2, 7, 7);
        Color text = u == currentUser ? Theme.YOU : isSuggested ? Theme.ACCENT : Theme.TEXT;
        g2.setColor(fade(text, lit));
        g2.drawString(label, lx, ly);
    }

    /** Dim anything outside the hover focus set. */
    private Color fade(Color c, boolean lit) {
        return lit ? c : Theme.alpha(c, 45);
    }

    private Color nodeColour(Person u) {
        if (highlightedPath != null && highlightedPath.contains(u)) return Theme.PATH;
        if (visualizationStep != null && visualizationStep.contains(u)) return Theme.ACCENT_DEEP;
        if (showHeatmap) return heatColour(u.getMetrics().get(heatmapMetric));
        if (showCommunities && u.getMetrics().getCommunityId() >= 0) {
            return COMMUNITY_COLORS[u.getMetrics().getCommunityId() % COMMUNITY_COLORS.length];
        }
        Category dominant = u.getDominantCategory();
        return dominant == null ? Theme.NODE
                : Theme.mix(new Color(dominant.getRgb()), Theme.BG, 0.28);
    }

    /**
     * Indigo → violet → amber. Deliberately avoids red and green, which already mean
     * "fragile link" and "highlighted route" on this canvas.
     */
    private Color heatColour(double v) {
        v = Math.max(0, Math.min(1, v));
        Color cold = new Color(0x2A3356), mid = new Color(0x7C6BD6), hot = new Color(0xF0A94E);
        return v < 0.5 ? Theme.mix(cold, mid, v * 2) : Theme.mix(mid, hot, (v - 0.5) * 2);
    }

    // ================= screen-space overlay =================

    /** Chrome that must not scale or move with the graph. */
    private void paintOverlay(Graphics2D g2) {
        if (showHeatmap) drawHeatLegend(g2);
        else if (showCommunities) drawCommunityLegend(g2);
        drawMinimap(g2);
        drawZoomBadge(g2);
    }

    /**
     * A whole-world thumbnail with the current view outlined.
     * <p>
     * Zoom without a minimap trades one problem for another: you can finally see detail,
     * but you lose track of where that detail sits, and panning becomes a hunt. Only
     * shown when zoomed in past the point where the full graph fits, since below that
     * the minimap would just be a smaller copy of what is already on screen.
     */
    private void drawMinimap(Graphics2D g2) {
        if (viewport.getZoom() <= 0.62) return;

        final int w = 150, h = 96;
        final int x = getWidth() - w - 14, y = 14;
        double sx = (double) w / CampusConnect.service.NetworkService.WORLD_WIDTH;
        double sy = (double) h / CampusConnect.service.NetworkService.WORLD_HEIGHT;

        g2.setColor(Theme.alpha(Theme.BG, 220));
        g2.fillRoundRect(x, y, w, h, 8, 8);
        g2.setColor(Theme.BORDER);
        g2.drawRoundRect(x, y, w, h, 8, 8);

        for (Person p : service.getAllUsers()) {
            g2.setColor(p == currentUser ? Theme.YOU
                    : suggested.contains(p) ? Theme.ACCENT
                    : Theme.alpha(Theme.TEXT_DIM, 150));
            int px = x + (int) (p.x * sx), py = y + (int) (p.y * sy);
            int r = (p == currentUser) ? 3 : 2;
            g2.fillOval(px - r, py - r, r * 2, r * 2);
        }

        // The rectangle is where the camera is looking, in world units.
        double viewW = getWidth() / viewport.getZoom();
        double viewH = getHeight() / viewport.getZoom();
        double viewX = -viewport.getPanX() / viewport.getZoom();
        double viewY = -viewport.getPanY() / viewport.getZoom();

        g2.setStroke(new BasicStroke(1.4f));
        g2.setColor(Theme.ACCENT);
        g2.drawRect(x + (int) (viewX * sx), y + (int) (viewY * sy),
                Math.max(4, (int) (viewW * sx)), Math.max(4, (int) (viewH * sy)));
    }

    private void drawHeatLegend(Graphics2D g2) {
        final int w = 148, h = 10;
        final int x = 16, y = getHeight() - h - 58;
        g2.setColor(Theme.alpha(Theme.BG, 210));
        g2.fillRoundRect(x - 10, y - 10, w + 100, h + 46, 10, 10);
        g2.setColor(Theme.BORDER);
        g2.drawRoundRect(x - 10, y - 10, w + 100, h + 46, 10, 10);

        for (int i = 0; i < w; i++) {
            g2.setColor(heatColour((double) i / (w - 1)));
            g2.drawLine(x + i, y, x + i, y + h);
        }

        g2.setFont(Theme.title(11));
        g2.setColor(Theme.TEXT);
        String title = heatmapMetric == NodeMetrics.Metric.SIMILARITY_TO_ME && currentUser != null
                ? "Match with " + currentUser.getName().split(" ")[0]
                : heatmapMetric.getLabel();
        g2.drawString(title, x, y + h + 16);

        g2.setFont(Theme.body(10));
        g2.setColor(Theme.TEXT_FAINT);
        g2.drawString("low", x, y + h + 30);
        String high = "high";
        g2.drawString(high, x + w - g2.getFontMetrics().stringWidth(high), y + h + 30);
    }

    private void drawCommunityLegend(Graphics2D g2) {
        java.util.Set<Integer> ids = new java.util.TreeSet<>();
        for (Person p : service.getAllUsers()) {
            int id = p.getMetrics().getCommunityId();
            if (id >= 0) ids.add(id);
        }
        if (ids.isEmpty()) return;

        final int box = 10, step = 16;
        int rows = Math.min(ids.size(), COMMUNITY_COLORS.length);
        final int x = 16, y = getHeight() - (rows * step + 30) - 14;

        g2.setColor(Theme.alpha(Theme.BG, 210));
        g2.fillRoundRect(x - 10, y - 10, 116, rows * step + 30, 10, 10);
        g2.setColor(Theme.BORDER);
        g2.drawRoundRect(x - 10, y - 10, 116, rows * step + 30, 10, 10);

        g2.setFont(Theme.title(11));
        g2.setColor(Theme.TEXT);
        g2.drawString("Circles", x, y + 6);

        g2.setFont(Theme.body(11));
        int row = 0;
        for (int id : ids) {
            if (row >= COMMUNITY_COLORS.length) break;
            int ry = y + 16 + row * step;
            g2.setColor(COMMUNITY_COLORS[id % COMMUNITY_COLORS.length]);
            g2.fillRoundRect(x, ry, box, box, 3, 3);
            g2.setColor(Theme.TEXT_DIM);
            g2.drawString("Circle " + id, x + box + 7, ry + box);
            row++;
        }
    }

    /** Zoom level plus the two keys that fix a lost camera. */
    private void drawZoomBadge(Graphics2D g2) {
        String text = viewport.zoomPercent() + "%   ·   F fit   ·   0 reset   ·   drag to pan";
        g2.setFont(Theme.body(10));
        FontMetrics fm = g2.getFontMetrics();
        int w = fm.stringWidth(text) + 20;
        int h = fm.getHeight() + 8;
        int x = getWidth() - w - 14, y = getHeight() - h - 14;

        g2.setColor(Theme.alpha(Theme.BG, 210));
        g2.fillRoundRect(x, y, w, h, 9, 9);
        g2.setColor(Theme.BORDER);
        g2.drawRoundRect(x, y, w, h, 9, 9);
        g2.setColor(Theme.TEXT_FAINT);
        g2.drawString(text, x + 10, y + fm.getAscent() + 4);
    }
}

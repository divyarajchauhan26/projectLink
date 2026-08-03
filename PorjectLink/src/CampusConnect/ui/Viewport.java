package CampusConnect.ui;

import CampusConnect.domain.Person;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.Collection;

/**
 * The camera: a pan offset and a zoom factor over the graph.
 * <p>
 * Without this the canvas was locked 1:1 to the window, and since the physics loop clamps
 * nodes inside the canvas bounds, a small window did not scroll — it <em>crushed</em> the
 * graph into itself. There was no way to see anything that ended up off-screen, because
 * nothing could be off-screen and nothing could be moved.
 * <p>
 * Two coordinate spaces from here on:
 * <ul>
 *   <li><b>World</b> — where people actually are. Physics, layout and persistence all
 *       work here and never know about the camera.</li>
 *   <li><b>Screen</b> — pixels in the component. Mouse events arrive in this space.</li>
 * </ul>
 * Everything that takes a mouse position must convert with {@link #toWorld}, and
 * everything that draws must go through {@link #apply}. Mixing them is the classic bug in
 * a zoomable canvas: hit-testing quietly drifts from what is drawn as soon as you zoom.
 */
public final class Viewport {

    public static final double MIN_ZOOM = 0.25;
    public static final double MAX_ZOOM = 3.0;

    private double zoom = 1.0;
    private double panX = 0, panY = 0;

    /** Where the camera is heading, for the eased approach in {@link #step}. */
    private double targetZoom = 1.0;
    private double targetPanX = 0, targetPanY = 0;
    private boolean animating = false;

    // ================= state =================

    public double getZoom() { return zoom; }
    public double getPanX() { return panX; }
    public double getPanY() { return panY; }
    public boolean isAnimating() { return animating; }

    /** Percentage for display, e.g. 150 at 1.5x. */
    public int zoomPercent() { return (int) Math.round(zoom * 100); }

    // ================= transforms =================

    /** Push the camera onto a graphics context. Callers must restore the old transform. */
    public AffineTransform apply(Graphics2D g2) {
        AffineTransform previous = g2.getTransform();
        g2.translate(panX, panY);
        g2.scale(zoom, zoom);
        return previous;
    }

    public Point2D toWorld(Point screen) {
        return new Point2D.Double((screen.x - panX) / zoom, (screen.y - panY) / zoom);
    }

    public Point2D toScreen(double worldX, double worldY) {
        return new Point2D.Double(worldX * zoom + panX, worldY * zoom + panY);
    }

    /**
     * Convert a screen distance to a world distance. Hit-test radii are specified in world
     * units, so a node stays equally easy to click at any zoom level.
     */
    public double toWorldDistance(double screenDistance) {
        return screenDistance / zoom;
    }

    /** The inverse transform, for anything that needs it directly. */
    public AffineTransform inverse() throws NoninvertibleTransformException {
        AffineTransform t = new AffineTransform();
        t.translate(panX, panY);
        t.scale(zoom, zoom);
        return t.createInverse();
    }

    // ================= movement =================

    public void panBy(double dxScreen, double dyScreen) {
        panX += dxScreen;
        panY += dyScreen;
        stopAnimating();
    }

    /**
     * Zoom about a fixed screen point — the point under the cursor stays under the cursor.
     * <p>
     * Zooming about the origin or the component centre instead is the thing that makes a
     * canvas feel like it is fighting you: the content you were aiming at slides away
     * exactly when you try to look closer.
     */
    public void zoomAt(Point screenAnchor, double factor) {
        double newZoom = clampZoom(zoom * factor);
        if (newZoom == zoom) return;

        // Keep the world point under the anchor pinned there.
        Point2D world = toWorld(screenAnchor);
        zoom = newZoom;
        panX = screenAnchor.x - world.getX() * zoom;
        panY = screenAnchor.y - world.getY() * zoom;
        stopAnimating();
    }

    public void reset() {
        setTarget(1.0, 0, 0);
    }

    /**
     * Frame everything with a margin, animated.
     *
     * @param people    what to fit
     * @param viewW     component width in pixels
     * @param viewH     component height in pixels
     * @param nodeRadius world-space radius, so nodes at the edge are not half cut off
     */
    public void fit(Collection<Person> people, int viewW, int viewH, int nodeRadius) {
        if (people == null || people.isEmpty() || viewW <= 0 || viewH <= 0) return;

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Person p : people) {
            minX = Math.min(minX, p.x - nodeRadius);
            minY = Math.min(minY, p.y - nodeRadius);
            maxX = Math.max(maxX, p.x + nodeRadius);
            maxY = Math.max(maxY, p.y + nodeRadius);
        }

        // Extra headroom at the top for name labels, which are drawn above each node.
        double margin = 40;
        double spanX = Math.max(1, maxX - minX) + margin * 2;
        double spanY = Math.max(1, maxY - minY) + margin * 2;

        double fitZoom = clampZoom(Math.min(viewW / spanX, viewH / spanY));
        double centreX = (minX + maxX) / 2.0;
        double centreY = (minY + maxY) / 2.0;

        setTarget(fitZoom,
                viewW / 2.0 - centreX * fitZoom,
                viewH / 2.0 - centreY * fitZoom);
    }

    /** Centre on one person without changing zoom — used by search. */
    public void centreOn(Person person, int viewW, int viewH) {
        if (person == null) return;
        setTarget(zoom, viewW / 2.0 - person.x * zoom, viewH / 2.0 - person.y * zoom);
    }

    /** Centre on someone and zoom in enough to read their surroundings. */
    public void focusOn(Person person, int viewW, int viewH) {
        if (person == null) return;
        double target = Math.max(zoom, 1.15);
        setTarget(target, viewW / 2.0 - person.x * target, viewH / 2.0 - person.y * target);
    }

    // ================= animation =================

    private void setTarget(double z, double px, double py) {
        targetZoom = clampZoom(z);
        targetPanX = px;
        targetPanY = py;
        animating = true;
    }

    private void stopAnimating() {
        animating = false;
        targetZoom = zoom;
        targetPanX = panX;
        targetPanY = panY;
    }

    /**
     * Advance the eased camera one frame.
     * <p>
     * Exponential easing rather than a jump, because a camera that teleports leaves the
     * viewer with no idea whether the graph moved or they did. Driven by the existing
     * repaint timer, so no extra thread.
     *
     * @return true if anything moved and a repaint is needed
     */
    public boolean step() {
        if (!animating) return false;

        final double ease = 0.22;
        double dz = targetZoom - zoom;
        double dx = targetPanX - panX;
        double dy = targetPanY - panY;

        // Below a pixel of movement, snap and stop — otherwise it creeps forever and
        // repaints every frame for nothing.
        if (Math.abs(dz) < 0.001 && Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5) {
            zoom = targetZoom;
            panX = targetPanX;
            panY = targetPanY;
            animating = false;
            return true;
        }

        zoom += dz * ease;
        panX += dx * ease;
        panY += dy * ease;
        return true;
    }

    private static double clampZoom(double z) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
    }
}

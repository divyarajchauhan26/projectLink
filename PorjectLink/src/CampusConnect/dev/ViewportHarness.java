package CampusConnect.dev;

import CampusConnect.domain.Person;
import CampusConnect.persist.CampusSeed;
import CampusConnect.service.NetworkService;
import CampusConnect.ui.Viewport;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.List;

/**
 * The camera.
 * <p>
 * Worth testing properly despite living in {@code ui/}, because it is pure arithmetic
 * with no Swing in it, and because the failure it guards against is silent: if screen↔world
 * conversion drifts, clicks land on the wrong node only once you have zoomed, which looks
 * like a hit-testing bug rather than a transform bug.
 *
 * <pre>java -cp "out;lib/gson-2.11.0.jar" CampusConnect.dev.ViewportHarness</pre>
 */
public class ViewportHarness {

    private static int checks = 0, failures = 0;

    private static void check(String label, boolean ok) {
        checks++;
        if (!ok) failures++;
        System.out.println("  " + (ok ? "PASS  " : "FAIL  ") + label);
    }

    private static boolean near(double a, double b) { return Math.abs(a - b) < 0.5; }

    public static void main(String[] args) {
        roundTrip();
        zoomAnchor();
        limits();
        fitting();

        System.out.println();
        System.out.println(failures == 0
                ? "=== ALL " + checks + " CHECKS PASSED ==="
                : "=== " + failures + " of " + checks + " CHECKS FAILED ===");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---------- the invariant everything else depends on ----------

    private static void roundTrip() {
        System.out.println("\n=== SCREEN <-> WORLD ===");
        Viewport v = new Viewport();

        // Identity to start with.
        Point2D w = v.toWorld(new Point(300, 200));
        check("untransformed screen equals world", near(w.getX(), 300) && near(w.getY(), 200));

        v.zoomAt(new Point(400, 300), 1.6);
        v.panBy(-120, 75);

        // Converting out and back must land where it started, at every zoom.
        for (int[] pt : new int[][]{{0, 0}, {123, 456}, {800, 600}, {-50, 20}}) {
            Point screen = new Point(pt[0], pt[1]);
            Point2D world = v.toWorld(screen);
            Point2D back = v.toScreen(world.getX(), world.getY());
            check(String.format("(%d,%d) survives screen->world->screen", pt[0], pt[1]),
                    near(back.getX(), screen.x) && near(back.getY(), screen.y));
        }

        System.out.printf("  zoom %.2f, pan (%.0f, %.0f)%n", v.getZoom(), v.getPanX(), v.getPanY());

        // Hit radii are quoted in world units, so they must scale with the camera.
        check("a screen distance converts to a smaller world distance when zoomed in",
                v.toWorldDistance(20) < 20);
    }

    // ---------- the thing that makes zooming feel right ----------

    private static void zoomAnchor() {
        System.out.println("\n=== ZOOM ANCHORING ===");
        Viewport v = new Viewport();
        Point anchor = new Point(250, 180);

        Point2D before = v.toWorld(anchor);
        v.zoomAt(anchor, 1.5);
        Point2D after = v.toWorld(anchor);

        // Whatever was under the cursor must still be under the cursor. Zooming about
        // the origin instead is what makes a canvas feel like it is fighting you.
        check("the world point under the cursor does not move",
                near(before.getX(), after.getX()) && near(before.getY(), after.getY()));

        v.zoomAt(anchor, 1 / 1.5);
        check("zooming back restores the original scale", near(v.getZoom(), 1.0));
    }

    private static void limits() {
        System.out.println("\n=== LIMITS ===");
        Viewport v = new Viewport();

        for (int i = 0; i < 50; i++) v.zoomAt(new Point(0, 0), 1.5);
        check("zoom stops at the maximum", v.getZoom() <= Viewport.MAX_ZOOM + 1e-9);
        System.out.printf("  after 50 zoom-ins: %.2f%n", v.getZoom());

        for (int i = 0; i < 100; i++) v.zoomAt(new Point(0, 0), 1 / 1.5);
        check("zoom stops at the minimum", v.getZoom() >= Viewport.MIN_ZOOM - 1e-9);
        System.out.printf("  after 100 zoom-outs: %.2f%n", v.getZoom());

        v.reset();
        while (v.step()) { /* run the easing to completion */ }
        check("reset returns to 1:1", near(v.getZoom(), 1.0));
        check("reset returns to the origin", near(v.getPanX(), 0) && near(v.getPanY(), 0));
        check("a settled camera stops asking for repaints", !v.step());
    }

    // ---------- fit ----------

    private static void fitting() {
        System.out.println("\n=== FIT TO VIEW ===");
        NetworkService svc = new NetworkService();
        CampusSeed.load(svc, NetworkService.WORLD_WIDTH, NetworkService.WORLD_HEIGHT);
        List<Person> people = svc.getAllUsers();

        int viewW = 800, viewH = 600;
        Viewport v = new Viewport();
        v.fit(people, viewW, viewH, 20);
        while (v.step()) { /* settle */ }

        System.out.printf("  fitted to %.0f%% for %d people%n", v.getZoom() * 100, people.size());
        check("fit produces a usable zoom", v.getZoom() >= Viewport.MIN_ZOOM && v.getZoom() <= Viewport.MAX_ZOOM);

        // The whole point: after fitting, everybody is on screen.
        int offScreen = 0;
        for (Person p : people) {
            Point2D s = v.toScreen(p.x, p.y);
            if (s.getX() < 0 || s.getY() < 0 || s.getX() > viewW || s.getY() > viewH) offScreen++;
        }
        System.out.println("  people outside the viewport after fit: " + offScreen);
        check("fit brings every single person on screen", offScreen == 0);

        // A window half the size must still fit everyone, just smaller.
        Viewport small = new Viewport();
        small.fit(people, 400, 300, 20);
        while (small.step()) { }
        int offSmall = 0;
        for (Person p : people) {
            Point2D s = small.toScreen(p.x, p.y);
            if (s.getX() < 0 || s.getY() < 0 || s.getX() > 400 || s.getY() > 300) offSmall++;
        }
        System.out.printf("  half-size window: %.0f%%, %d off screen%n", small.getZoom() * 100, offSmall);
        check("fit still works in a small window", offSmall == 0);
        check("a smaller window fits at a smaller zoom", small.getZoom() <= v.getZoom());

        // Degenerate inputs must not throw or produce NaN.
        Viewport edge = new Viewport();
        edge.fit(List.of(), 800, 600, 20);
        check("fitting nothing is a no-op", near(edge.getZoom(), 1.0));
        edge.fit(people, 0, 0, 20);
        check("fitting into a zero-size view is a no-op", near(edge.getZoom(), 1.0));

        Viewport one = new Viewport();
        one.fit(List.of(people.get(0)), 800, 600, 20);
        while (one.step()) { }
        check("fitting a single person stays finite",
                !Double.isNaN(one.getZoom()) && !Double.isNaN(one.getPanX()));

        // Centring must actually centre.
        Viewport centred = new Viewport();
        Person target = svc.findUserByName("Kabir Khan");
        centred.centreOn(target, viewW, viewH);
        while (centred.step()) { }
        Point2D onScreen = centred.toScreen(target.x, target.y);
        check("centreOn puts the person in the middle",
                near(onScreen.getX(), viewW / 2.0) && near(onScreen.getY(), viewH / 2.0));
    }
}

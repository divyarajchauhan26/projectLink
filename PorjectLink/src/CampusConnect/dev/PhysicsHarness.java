package CampusConnect.dev;

import CampusConnect.domain.Person;
import CampusConnect.persist.CampusSeed;
import CampusConnect.service.NetworkService;

import java.util.List;

/**
 * Invariants for the force-directed layout.
 * <p>
 * The layout runs 33 times a second for the whole life of the app, so a single bad
 * frame is permanent: once a coordinate becomes NaN every later comparison against it
 * is false, the node stops being drawable, and it never recovers. These checks exist
 * because exactly that happened — two connected nodes at identical coordinates made the
 * spring force divide by zero, and the nodes silently vanished.
 *
 * <pre>java -cp out CampusConnect.dev.PhysicsHarness</pre>
 */
public class PhysicsHarness {

    private static int checks = 0, failures = 0;

    private static void check(String label, boolean ok) {
        checks++;
        if (!ok) failures++;
        System.out.println("  " + (ok ? "PASS  " : "FAIL  ") + label);
    }

    public static void main(String[] args) {
        coincidentConnected();
        coincidentUnconnected();
        wholeCampusStaysSane();

        System.out.println();
        System.out.println(failures == 0
                ? "=== ALL " + checks + " CHECKS PASSED ==="
                : "=== " + failures + " of " + checks + " CHECKS FAILED ===");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---------- the original bug ----------

    private static void coincidentConnected() {
        System.out.println("\n=== two CONNECTED nodes at the same point ===");
        NetworkService svc = new NetworkService();
        svc.addUserAtPosition("A", 400, 300);
        svc.addUserAtPosition("B", 400, 300);
        List<Person> us = svc.getAllUsers();
        try { svc.addConnection(us.get(0), us.get(1)); } catch (Exception e) { }

        for (int i = 0; i < 200; i++) svc.updatePhysics(800, 600);

        check("coordinates stay finite", allFinite(us));
        System.out.printf("        A=(%.1f, %.1f)  B=(%.1f, %.1f)%n",
                us.get(0).x, us.get(0).y, us.get(1).x, us.get(1).y);
        check("repulsion separated them", separation(us.get(0), us.get(1)) > 1.0);
    }

    private static void coincidentUnconnected() {
        System.out.println("\n=== two UNCONNECTED nodes at the same point ===");
        NetworkService svc = new NetworkService();
        svc.addUserAtPosition("A", 200, 200);
        svc.addUserAtPosition("B", 200, 200);
        List<Person> us = svc.getAllUsers();

        for (int i = 0; i < 200; i++) svc.updatePhysics(800, 600);

        check("coordinates stay finite", allFinite(us));
        check("repulsion separated them", separation(us.get(0), us.get(1)) > 1.0);
    }

    // ---------- the real graph ----------

    private static void wholeCampusStaysSane() {
        System.out.println("\n=== 40-student campus, 600 ticks ===");
        NetworkService svc = new NetworkService();
        CampusSeed.load(svc, 1100, 700);
        List<Person> all = svc.getAllUsers();

        double startEnergy = kineticEnergy(all);
        for (int i = 0; i < 600; i++) svc.updatePhysics(1100, 700);
        double endEnergy = kineticEnergy(all);

        check("no NaN or infinity anywhere", allFinite(all));

        boolean inBounds = true;
        for (Person p : all) {
            if (p.x < 0 || p.y < 0 || p.x > 1100 || p.y > 700) inBounds = false;
        }
        check("every node stayed on the canvas", inBounds);

        // Damping should bleed energy off rather than let the simulation run away.
        System.out.printf("        kinetic energy %.1f -> %.1f%n", startEnergy, endEnergy);
        check("simulation did not explode", endEnergy < 1e7);

        // Nodes that share an edge should end up nearer than the canvas diagonal;
        // if springs were broken everything would just drift to the boundary.
        double avgEdgeLength = 0;
        int edges = 0;
        for (Person p : all) {
            for (Person q : svc.getConnections(p)) {
                avgEdgeLength += separation(p, q);
                edges++;
            }
        }
        avgEdgeLength /= Math.max(1, edges);
        System.out.printf("        mean edge length %.1f px%n", avgEdgeLength);
        check("springs are pulling neighbours together", avgEdgeLength < 400);
    }

    // ---------- helpers ----------

    private static boolean allFinite(List<Person> people) {
        for (Person p : people) {
            if (Double.isNaN(p.x) || Double.isNaN(p.y)
                    || Double.isInfinite(p.x) || Double.isInfinite(p.y)) return false;
        }
        return true;
    }

    private static double separation(Person a, Person b) {
        double dx = a.x - b.x, dy = a.y - b.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static double kineticEnergy(List<Person> people) {
        double e = 0;
        for (Person p : people) e += p.dx * p.dx + p.dy * p.dy;
        return e;
    }
}

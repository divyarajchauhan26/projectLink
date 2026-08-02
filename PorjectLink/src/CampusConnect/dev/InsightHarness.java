package CampusConnect.dev;

import CampusConnect.domain.Person;
import CampusConnect.persist.CampusSeed;
import CampusConnect.service.InsightService;
import CampusConnect.service.NetworkService;

import java.util.List;

/**
 * The human-language layer over the graph algorithms.
 * <p>
 * The algorithms themselves were already verified — this checks the part that was
 * actually missing, namely whether the output means anything. A circle named "Circle 3"
 * and a circle named "The Guitar crowd" are the same Louvain result; only one of them is
 * a feature.
 *
 * <pre>java -cp "out;lib/gson-2.11.0.jar" CampusConnect.dev.InsightHarness</pre>
 */
public class InsightHarness {

    private static int checks = 0, failures = 0;

    private static void check(String label, boolean ok) {
        checks++;
        if (!ok) failures++;
        System.out.println("  " + (ok ? "PASS  " : "FAIL  ") + label);
    }

    public static void main(String[] args) {
        NetworkService svc = new NetworkService();
        CampusSeed.load(svc, 1100, 700);
        InsightService insights = new InsightService(svc);

        circles(svc, insights);
        squads(insights);
        isolated(svc, insights);
        personal(svc, insights);

        System.out.println();
        System.out.println(failures == 0
                ? "=== ALL " + checks + " CHECKS PASSED ==="
                : "=== " + failures + " of " + checks + " CHECKS FAILED ===");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---------- circles ----------

    private static void circles(NetworkService svc, InsightService insights) {
        System.out.println("\n=== YOUR CIRCLES ===");
        List<InsightService.Circle> circles = insights.circles();

        for (InsightService.Circle c : circles) {
            System.out.printf("  %-38s %2d people, %.0f%% dense%n",
                    c.name(), c.size(), c.density() * 100);
        }

        check("circles were found", !circles.isEmpty());

        int total = circles.stream().mapToInt(InsightService.Circle::size).sum();
        check("every student lands in exactly one circle", total == svc.getAllUsers().size());

        // The naming is the whole feature. A circle called "Circle 4" is the raw Louvain
        // id leaking through, which is what this replaced.
        long named = circles.stream()
                .filter(c -> c.size() >= 3)
                .filter(c -> c.name().startsWith("The "))
                .count();
        long sizeable = circles.stream().filter(c -> c.size() >= 3).count();
        System.out.printf("  %d of %d circles with 3+ members got an interest-based name%n",
                named, sizeable);
        check("most sizeable circles are named after a shared interest",
                sizeable == 0 || named * 2 >= sizeable);

        check("density is a proper fraction", circles.stream()
                .allMatch(c -> c.density() >= 0 && c.density() <= 1.0001));
    }

    // ---------- squads ----------

    private static void squads(InsightService insights) {
        System.out.println("\n=== SQUADS ===");
        List<InsightService.Squad> squads = insights.squads(3);

        for (int i = 0; i < Math.min(6, squads.size()); i++) {
            InsightService.Squad s = squads.get(i);
            System.out.printf("  %d people%s%n", s.size(),
                    s.sharedInterest() == null ? "" : " · all into " + s.sharedInterest());
            System.out.println("    " + String.join(", ",
                    s.members().stream().map(Person::getName).toList()));
        }

        check("squads of 3+ were found", !squads.isEmpty());
        check("no squad is smaller than the minimum",
                squads.stream().allMatch(s -> s.size() >= 3));
        check("squads are ordered largest first",
                squads.size() < 2 || squads.get(0).size() >= squads.get(1).size());
        check("a higher minimum returns fewer squads",
                insights.squads(4).size() <= squads.size());
    }

    // ---------- who the network is failing ----------

    private static void isolated(NetworkService svc, InsightService insights) {
        System.out.println("\n=== WHO THE NETWORK IS FAILING ===");
        List<InsightService.Isolated> isolated = insights.isolated();

        for (InsightService.Isolated i : isolated) {
            System.out.printf("  %-20s %s%n", i.person().getName(), i.reason());
        }

        // The three seeded first-years are exactly the people this should surface.
        boolean aarav = isolated.stream().anyMatch(i -> i.person().getName().equals("Aarav Jain"));
        check("Aarav (zero connections) is flagged", aarav);

        check("nobody well-connected is flagged",
                isolated.stream().noneMatch(i -> svc.getConnections(i.person()).size() > 3));
        check("the most stranded person is listed first",
                isolated.isEmpty() || isolated.get(0).degree() == 0);
        check("every entry carries a human reason",
                isolated.stream().allMatch(i -> i.reason() != null && !i.reason().isBlank()));
    }

    // ---------- personal ----------

    private static void personal(NetworkService svc, InsightService insights) {
        System.out.println("\n=== ARCHETYPES & REACH ===");
        List<InsightService.Circle> circles = insights.circles();

        for (String name : new String[]{
                "Rahul Verma", "Aarav Jain", "Varun Nambiar", "Kabir Khan", "Ritu Saxena"}) {
            Person p = svc.findUserByName(name);
            String archetype = insights.archetype(p);
            int one = insights.reachWithin(p, 1);
            int two = insights.reachWithin(p, 2);
            int three = insights.reachWithin(p, 3);
            List<String> bridges = insights.bridgesBetween(p, circles);

            System.out.printf("  %-18s %-14s reach %d/%d/%d%s%n",
                    name, archetype, one, two, three,
                    bridges.isEmpty() ? "" : "  bridges to " + bridges.size() + " circle(s)");

            check(name + ": reach grows with distance", one <= two && two <= three);
            check(name + ": 1-hop reach equals degree", one == svc.getConnections(p).size());
            check(name + ": has an archetype", archetype != null && !archetype.isBlank());
        }

        Person aarav = svc.findUserByName("Aarav Jain");
        check("a student with no friends is a Newcomer",
                insights.archetype(aarav).equals("Newcomer"));
        check("a student with no friends reaches nobody", insights.reachWithin(aarav, 3) == 0);
        check("a student with no friends bridges nothing",
                insights.bridgesBetween(aarav, circles).isEmpty());
    }
}

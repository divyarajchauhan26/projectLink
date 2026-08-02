package CampusConnect.dev;

import CampusConnect.algorithm.similarity.SimilarityEngine;
import CampusConnect.domain.Person;
import CampusConnect.persist.CampusSeed;
import CampusConnect.service.ConnectionService;
import CampusConnect.service.InsightService;
import CampusConnect.service.NetworkService;
import CampusConnect.service.RecommendationService;
import CampusConnect.service.RecommendationService.Suggestion;

import java.util.List;
import java.util.Set;

/**
 * Warm introductions, connection requests, icebreakers, and the isolation nudge.
 *
 * <pre>java -cp "out;lib/gson-2.11.0.jar" CampusConnect.dev.ConnectionHarness</pre>
 */
public class ConnectionHarness {

    private static int checks = 0, failures = 0;

    private static void check(String label, boolean ok) {
        checks++;
        if (!ok) failures++;
        System.out.println("  " + (ok ? "PASS  " : "FAIL  ") + label);
    }

    public static void main(String[] args) throws Exception {
        NetworkService svc = new NetworkService();
        CampusSeed.load(svc, 1100, 700);
        InsightService insights = new InsightService(svc);

        warmIntro(svc, insights);
        requests(svc);
        icebreakers(svc);
        isolationNudge(svc);

        System.out.println();
        System.out.println(failures == 0
                ? "=== ALL " + checks + " CHECKS PASSED ==="
                : "=== " + failures + " of " + checks + " CHECKS FAILED ===");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---------- warm introductions ----------

    private static void warmIntro(NetworkService svc, InsightService insights) {
        System.out.println("\n=== WARM INTRODUCTIONS ===");

        Person kabir = svc.findUserByName("Kabir Khan");
        Person priya = svc.findUserByName("Priya Menon");
        List<Person> chain = insights.warmestIntroduction(kabir, priya);

        System.out.println("  " + insights.describeIntroduction(chain));
        System.out.print("  chain: ");
        for (int i = 0; i < chain.size(); i++) {
            if (i > 0) System.out.print(" -> ");
            System.out.print(chain.get(i).getName());
        }
        System.out.println();

        check("a route exists between two connected clusters", !chain.isEmpty());
        check("the chain starts at the asker", chain.get(0) == kabir);
        check("the chain ends at the target", chain.get(chain.size() - 1) == priya);

        // Every consecutive pair must be an actual friendship, or the "introduction" is
        // asking somebody to introduce you to a stranger.
        boolean realLinks = true;
        for (int i = 0; i < chain.size() - 1; i++) {
            if (!svc.getConnections(chain.get(i)).contains(chain.get(i + 1))) realLinks = false;
        }
        check("every step is a real friendship", realLinks);

        // The point of the whole feature: warmth beats brevity. The chosen route must
        // have a better total 1/strength cost than the plain hop-count shortest path.
        List<Person> shortest = svc.findShortestPath(kabir, priya);
        double warmCost = cost(svc, chain), shortCost = cost(svc, shortest);
        System.out.printf("  warm route: %d steps, cost %.2f%n", chain.size() - 1, warmCost);
        System.out.printf("  fewest-hops route: %d steps, cost %.2f%n", shortest.size() - 1, shortCost);
        check("the warm route is at least as warm as the shortest one", warmCost <= shortCost + 1e-9);

        Person aarav = svc.findUserByName("Aarav Jain");
        check("no route to someone with no connections",
                insights.warmestIntroduction(kabir, aarav).isEmpty());
        check("unreachable targets are described, not crashed",
                !insights.describeIntroduction(List.of()).isBlank());
        check("a direct friend is reported as already known",
                insights.describeIntroduction(
                        insights.warmestIntroduction(kabir, svc.findUserByName("Meera Joshi")))
                        .contains("already know"));
        check("asking for a route to yourself is empty",
                insights.warmestIntroduction(kabir, kabir).isEmpty());
    }

    private static double cost(NetworkService svc, List<Person> path) {
        double total = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            total += 1.0 / Math.max(0.1, svc.getEdgeWeight(path.get(i), path.get(i + 1)));
        }
        return total;
    }

    // ---------- requests ----------

    private static void requests(NetworkService svc) throws Exception {
        System.out.println("\n=== REQUESTS ===");
        ConnectionService conn = new ConnectionService(svc);

        Person aarav = svc.findUserByName("Aarav Jain");
        Person kabir = svc.findUserByName("Kabir Khan");
        Person meera = svc.findUserByName("Meera Joshi");

        ConnectionService.Request r = conn.request(aarav, kabir, "Hey, fancy a jam?");
        check("a request can be sent", r != null);
        check("it appears in the recipient's inbox", conn.incoming(kabir).size() == 1);
        check("it appears in the sender's outbox", conn.outgoing(aarav).size() == 1);

        // Crucially, requesting does NOT create the edge — that is the whole difference
        // from V1, where clicking two nodes connected them unilaterally.
        check("requesting does not connect anyone yet",
                !svc.getConnections(aarav).contains(kabir));

        check("a duplicate request is refused", conn.request(aarav, kabir, "again") == null);
        check("the reverse duplicate is also refused", conn.request(kabir, aarav, "hi") == null);
        check("nobody can request themselves", conn.request(aarav, aarav, "") == null);

        conn.accept(r, ConnectionService.Kind.FRIEND, ConnectionService.Origin.SUGGESTED);
        check("accepting creates the connection", svc.getConnections(aarav).contains(kabir));
        check("the request leaves the queue", conn.incoming(kabir).isEmpty());
        check("origin is recorded",
                conn.metaFor(aarav, kabir).origin() == ConnectionService.Origin.SUGGESTED);
        check("already-connected people cannot be re-requested",
                conn.request(aarav, kabir, "again") == null);

        ConnectionService.Request r2 = conn.request(aarav, meera, "hello");
        check("declining removes the request", conn.decline(r2));
        check("declining does not connect", !svc.getConnections(aarav).contains(meera));
        check("declining twice is a no-op", !conn.decline(r2));

        System.out.printf("  %.0f%% of recorded connections came from a suggestion%n",
                conn.suggestedShare() * 100);
        check("suggested share is measurable", conn.suggestedShare() >= 0);
    }

    // ---------- icebreakers ----------

    private static void icebreakers(NetworkService svc) {
        System.out.println("\n=== ICEBREAKERS ===");
        ConnectionService conn = new ConnectionService(svc);
        SimilarityEngine sim = SimilarityEngine.build(svc.getAllUsers());

        Person aarav = svc.findUserByName("Aarav Jain");
        Person kabir = svc.findUserByName("Kabir Khan");
        Person distantPerson = svc.findUserByName("Sara DSouza");

        String shared = conn.icebreaker(aarav, kabir, sim);
        String distant = conn.icebreaker(aarav, distantPerson, sim);
        System.out.println("  Aarav -> Kabir:  " + shared);
        System.out.println("  Aarav -> Sara:   " + distant);

        check("an opener is produced for a close match", !shared.isBlank());
        check("it names the person", shared.contains("Kabir"));
        check("it cites something they share", shared.toLowerCase().contains("guitar")
                || shared.toLowerCase().contains("indie") || shared.toLowerCase().contains("poetry"));
        check("an opener still exists with little in common", !distant.isBlank());
        check("the fallback still names the person", distant.contains("Sara"));
    }

    // ---------- the pro-social nudge ----------

    private static void isolationNudge(NetworkService svc) {
        System.out.println("\n=== ISOLATION NUDGE ===");

        RecommendationService.Weights on = RecommendationService.Weights.defaults();
        RecommendationService.Weights off = new RecommendationService.Weights(
                on.interest(), on.bio(), on.context(), on.structural(),
                on.intent(), on.teachLearn(), on.popularityPenalty(), 0.0);

        int withNudge = countIsolatedSuggestions(svc, new RecommendationService(svc, on));
        int without = countIsolatedSuggestions(svc, new RecommendationService(svc, off));

        System.out.println("  appearances of barely-connected students across all feeds:");
        System.out.println("    nudge off  " + without);
        System.out.println("    nudge on   " + withNudge);

        // The point of the term: the network should work to pull in the people it is
        // failing, not just serve whoever is already well connected.
        check("the nudge surfaces isolated students more often", withNudge >= without);
        check("it does not take over the feed",
                withNudge < svc.getAllUsers().size() * 5 * 0.6);
    }

    private static int countIsolatedSuggestions(NetworkService svc, RecommendationService rec) {
        int n = 0;
        for (Person p : svc.getAllUsers()) {
            for (Suggestion s : rec.recommend(p, 5, 0.0, Set.of())) {
                if (svc.getConnections(s.person()).size() < 3) n++;
            }
        }
        return n;
    }
}

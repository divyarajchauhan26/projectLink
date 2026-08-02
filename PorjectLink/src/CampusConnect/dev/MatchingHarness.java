package CampusConnect.dev;

import CampusConnect.domain.Person;
import CampusConnect.persist.CampusSeed;
import CampusConnect.service.NetworkService;
import CampusConnect.service.RecommendationService;
import CampusConnect.service.RecommendationService.Suggestion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The M4 checkpoint: print who the engine thinks each student should meet, and check the
 * answers we can state in advance.
 * <p>
 * This exists <em>before</em> any UI on purpose. Judging match quality and debugging Swing
 * layout are both hard, and doing them at once means never knowing which one is broken.
 * Here the weights can be retuned and re-read in seconds.
 *
 * <pre>java -cp "out;lib/gson-2.11.0.jar" CampusConnect.dev.MatchingHarness</pre>
 */
public class MatchingHarness {

    private static int checks = 0, failures = 0;

    private static void check(String label, boolean ok) {
        checks++;
        if (!ok) failures++;
        System.out.println("  " + (ok ? "PASS  " : "FAIL  ") + label);
    }

    public static void main(String[] args) {
        NetworkService svc = new NetworkService();
        CampusSeed.load(svc, 1100, 700);
        RecommendationService recommender = new RecommendationService(svc);

        spotlight(svc, recommender);
        everyone(svc, recommender);
        invariants(svc, recommender);
        affinityMap(svc, recommender);

        System.out.println();
        System.out.println(failures == 0
                ? "=== ALL " + checks + " CHECKS PASSED ==="
                : "=== " + failures + " of " + checks + " CHECKS FAILED ===");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---------- the cases with a known right answer ----------

    private static void spotlight(NetworkService svc, RecommendationService rec) {
        System.out.println("\n"  + "=".repeat(78));
        System.out.println("COLD START — the students the product exists for");
        System.out.println("=".repeat(78));

        for (String name : new String[]{"Aarav Jain", "Ira Bhattacharya", "Tanvi Deshmukh"}) {
            Person p = svc.findUserByName(name);
            printProfile(svc, p, rec);
            List<Suggestion> suggestions = rec.recommend(p, 5);
            printSuggestions(suggestions, true);

            check(name + " gets suggestions despite no graph signal", !suggestions.isEmpty());
        }

        // Aarav plays guitar, is into indie and writes poetry, and knows nobody.
        // Kabir plays guitar, is into indie and writes poetry. If anything else wins,
        // the content signal is not doing its job.
        Person aarav = svc.findUserByName("Aarav Jain");
        List<Suggestion> forAarav = rec.recommend(aarav, 5);
        check("Kabir is Aarav's top match",
                !forAarav.isEmpty() && forAarav.get(0).person().getName().equals("Kabir Khan"));

        // Tanvi wants a mentor; Aditya, Ananya and Ritu all offer to mentor.
        Person tanvi = svc.findUserByName("Tanvi Deshmukh");
        boolean mentorSurfaced = rec.recommend(tanvi, 5).stream()
                .anyMatch(s -> s.person().isLookingFor(CampusConnect.domain.Intent.MENTEE));
        check("Tanvi (wants a mentor) is shown someone offering to mentor", mentorSurfaced);
    }

    private static void printProfile(NetworkService svc, Person p, RecommendationService rec) {
        System.out.println();
        System.out.println("-".repeat(78));
        System.out.printf("%s %s  ·  year %d %s  ·  %s, %s%n",
                p.getAvatarEmoji(), p.getName(), p.getYear(), p.getMajor(),
                p.getHometown(), p.getHostel());
        System.out.println("  \"" + p.getBio() + "\"");
        System.out.println("  interests: " + interestLine(p));
        System.out.println("  wants: " + p.getLookingFor());
        System.out.printf("  %d friends%s%n", svc.getConnections(p).size(),
                rec.isColdStart(p) ? "   [COLD START — structural weight redistributed]" : "");
        System.out.println("-".repeat(78));
    }

    // ---------- the whole campus ----------

    private static void everyone(NetworkService svc, RecommendationService rec) {
        System.out.println("\n" + "=".repeat(78));
        System.out.println("TOP 3 FOR EVERY STUDENT");
        System.out.println("=".repeat(78));

        for (Person p : svc.getAllUsers()) {
            System.out.printf("%n%s %s (%d friends)%n",
                    p.getAvatarEmoji(), p.getName(), svc.getConnections(p).size());
            for (Suggestion s : rec.recommend(p, 3)) {
                System.out.printf("    %.3f  %-20s %s%n",
                        s.score(), s.person().getName(), s.explanation());
            }
        }
    }

    // ---------- properties that must hold for everybody ----------

    private static void invariants(NetworkService svc, RecommendationService rec) {
        System.out.println("\n" + "=".repeat(78));
        System.out.println("INVARIANTS");
        System.out.println("=".repeat(78));

        int emptyLists = 0, selfSuggested = 0, alreadyFriends = 0;
        Map<String, Integer> appearances = new HashMap<>();

        for (Person p : svc.getAllUsers()) {
            List<Suggestion> suggestions = rec.recommend(p, 5);
            if (suggestions.isEmpty()) emptyLists++;
            for (Suggestion s : suggestions) {
                if (s.person() == p) selfSuggested++;
                if (svc.getConnections(p).contains(s.person())) alreadyFriends++;
                appearances.merge(s.person().getName(), 1, Integer::sum);
            }
        }

        check("nobody is suggested to themselves", selfSuggested == 0);
        check("nobody is suggested a person they already know", alreadyFriends == 0);
        check("everybody gets at least one suggestion", emptyLists == 0);

        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(appearances.entrySet());
        ranked.sort((a, b) -> b.getValue() - a.getValue());

        System.out.println("\n  Most-suggested people (watch for one name dominating):");
        for (int i = 0; i < Math.min(6, ranked.size()); i++) {
            Map.Entry<String, Integer> e = ranked.get(i);
            System.out.printf("    %-20s in %2d of %d lists%n",
                    e.getKey(), e.getValue(), svc.getAllUsers().size());
        }

        // Thresholds are relative to the campus, not absolute. An earlier version
        // hardcoded "25+ distinct" and "at most 20 lists", which silently encoded the
        // seed being 40 people and broke the moment it was not.
        int population = svc.getAllUsers().size();
        int topCount = ranked.isEmpty() ? 0 : ranked.get(0).getValue();
        check("no single person owns more than half the lists", topCount <= population / 2);

        System.out.printf("%n  %d of %d people appear across all lists%n",
                appearances.size(), population);
        check("suggestions reach most of the campus (60%+)",
                appearances.size() >= population * 0.6);

        popularityAbTest(svc);
    }

    /**
     * Prove the popularity penalty actually does something.
     * <p>
     * Asserting "the hub appears in few lists" looks like a test and is not: Rahul is
     * already friends with all seven sports people, so he is filtered out as an existing
     * friend and scores zero appearances whether or not the penalty exists. The check
     * passes for entirely the wrong reason. Measuring the mean degree of everybody who
     * gets suggested, with the penalty on and off, tests the mechanism instead of a
     * coincidence.
     */
    private static void popularityAbTest(NetworkService svc) {
        RecommendationService.Weights on = RecommendationService.Weights.defaults();
        RecommendationService.Weights off = new RecommendationService.Weights(
                on.interest(), on.bio(), on.context(), on.structural(),
                on.intent(), on.teachLearn(), 0.0, on.isolationBoost());

        double withPenalty = meanDegreeOfSuggested(svc, new RecommendationService(svc, on));
        double withoutPenalty = meanDegreeOfSuggested(svc, new RecommendationService(svc, off));

        System.out.println("\n  Popularity penalty A/B — mean degree of suggested people:");
        System.out.printf("    penalty off  %.3f%n", withoutPenalty);
        System.out.printf("    penalty on   %.3f%n", withPenalty);
        check("penalty steers suggestions away from the already-popular",
                withPenalty < withoutPenalty);
    }

    private static double meanDegreeOfSuggested(NetworkService svc, RecommendationService rec) {
        double total = 0;
        int count = 0;
        for (Person p : svc.getAllUsers()) {
            for (Suggestion s : rec.recommend(p, 5)) {
                total += svc.getConnections(s.person()).size();
                count++;
            }
        }
        return count == 0 ? 0 : total / count;
    }

    // ---------- the similarity heatmap ----------

    /**
     * The affinity map behind the "similarity to me" heatmap.
     * <p>
     * The heatmap and the ranked suggestion list are two views of the same numbers, and a
     * user will absolutely put them side by side. If the hottest node on the map were not
     * the top of the list, one of them would be lying — so that agreement is checked
     * directly rather than assumed from the fact that both call score().
     */
    private static void affinityMap(NetworkService svc, RecommendationService rec) {
        System.out.println("\n" + "=".repeat(78));
        System.out.println("SIMILARITY HEATMAP");
        System.out.println("=".repeat(78));

        for (String name : new String[]{"Aarav Jain", "Priya Menon"}) {
            Person me = svc.findUserByName(name);
            Map<Person, Double> affinity = rec.affinityTo(me);

            check(name + ": map covers everyone but themselves",
                    affinity.size() == svc.getAllUsers().size() - 1);
            check(name + ": no self entry", !affinity.containsKey(me));

            boolean inRange = affinity.values().stream().allMatch(v -> v >= 0.0 && v <= 1.0);
            check(name + ": every value is within [0,1]", inRange);

            // Existing friends must be present — the heatmap shades the whole campus,
            // not just the people you have yet to meet.
            List<Person> friends = svc.getConnections(me);
            boolean friendsIncluded = friends.isEmpty()
                    || friends.stream().allMatch(affinity::containsKey);
            check(name + ": existing friends are included", friendsIncluded);

            List<Map.Entry<Person, Double>> ranked = new ArrayList<>(affinity.entrySet());
            ranked.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

            System.out.printf("%n  %s — hottest on the map:%n", name);
            for (int i = 0; i < Math.min(4, ranked.size()); i++) {
                Map.Entry<Person, Double> e = ranked.get(i);
                boolean isFriend = friends.contains(e.getKey());
                System.out.printf("    %.2f  %-20s %s%n",
                        e.getValue(), e.getKey().getName(), isFriend ? "(already a friend)" : "");
            }

            // The hottest non-friend must be the same person the list puts first.
            Person hottestStranger = ranked.stream()
                    .map(Map.Entry::getKey)
                    .filter(p -> !friends.contains(p))
                    .findFirst().orElse(null);
            List<Suggestion> top = rec.recommend(me, 1);
            check(name + ": hottest non-friend equals the top suggestion",
                    hottestStranger != null && !top.isEmpty()
                            && hottestStranger == top.get(0).person());
        }

        // A null user must not blow up — the menu item is reachable before signing in.
        check("affinityTo(null) returns empty rather than throwing",
                rec.affinityTo(null).isEmpty());
    }

    // ---------- formatting ----------

    private static void printSuggestions(List<Suggestion> suggestions, boolean detailed) {
        int rank = 1;
        for (Suggestion s : suggestions) {
            Person p = s.person();
            System.out.printf("%n  %d. %s %-20s  score %.3f%n",
                    rank++, p.getAvatarEmoji(), p.getName(), s.score());
            System.out.println("     " + s.explanation());
            if (detailed) {
                var sig = s.signals();
                System.out.printf("     interest %.2f | bio %.2f | context %.2f | "
                                + "structural %.2f | intent %.2f | teach %.2f | popularity %.2f%n",
                        sig.interest(), sig.bio(), sig.context(),
                        sig.structural(), sig.intent(), sig.teachLearn(), sig.popularity());
            }
        }
    }

    private static String interestLine(Person p) {
        StringBuilder sb = new StringBuilder();
        p.getInterestIntensities().forEach((tag, intensity) -> {
            if (sb.length() > 0) sb.append(", ");
            sb.append(tag.label()).append('(').append(intensity).append(')');
        });
        return sb.toString();
    }
}

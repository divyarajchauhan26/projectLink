package CampusConnect.dev;

import CampusConnect.domain.Intent;
import CampusConnect.domain.InterestTag;
import CampusConnect.domain.Person;
import CampusConnect.persist.CampusSeed;
import CampusConnect.persist.GraphIO;
import CampusConnect.service.NetworkService;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Verifies the seeded campus and that a full profile survives a save/load round-trip.
 *
 * <pre>java -cp "out;lib/gson-2.11.0.jar" CampusConnect.dev.SeedHarness</pre>
 */
public class SeedHarness {

    private static int checks = 0, failures = 0;

    private static void check(String label, boolean ok) {
        checks++;
        if (!ok) failures++;
        System.out.println("  " + (ok ? "PASS  " : "FAIL  ") + label);
    }

    public static void main(String[] args) throws Exception {
        NetworkService svc = new NetworkService();
        CampusSeed.load(svc, 1100, 700);

        shape(svc);
        coldStart(svc);
        roundTrip(svc);
        previewMatch(svc);

        System.out.println();
        System.out.println(failures == 0
                ? "=== ALL " + checks + " CHECKS PASSED ==="
                : "=== " + failures + " of " + checks + " CHECKS FAILED ===");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---------- shape of the campus ----------

    private static void shape(NetworkService svc) {
        List<Person> all = svc.getAllUsers();
        System.out.println("\n=== CAMPUS ===");
        System.out.println("  " + all.size() + " students, " + svc.getEdgeCount() + " connections");
        System.out.printf("  avg degree %.2f, density %.3f, clustering %.3f%n",
                svc.getAverageDegree(), svc.getDensity(), svc.averageClusteringCoefficient());

        check("24 students seeded", all.size() == 24);

        int noBio = 0, fewInterests = 0, noIntent = 0, noLang = 0;
        for (Person p : all) {
            if (p.getBio().isBlank()) noBio++;
            if (p.getInterests().size() < 3) fewInterests++;
            if (p.getLookingFor().isEmpty()) noIntent++;
            if (p.getLanguages().isEmpty()) noLang++;
        }
        check("every student has a bio", noBio == 0);
        check("every student has 3+ interests", fewInterests == 0);
        check("every student declares an intent", noIntent == 0);
        check("every student lists a language", noLang == 0);

        Set<InterestTag> distinct = new LinkedHashSet<>();
        for (Person p : all) distinct.addAll(p.getInterests());
        System.out.println("  " + distinct.size() + " distinct interests in use");
        check("interest vocabulary is spread (30+ distinct)", distinct.size() >= 30);

        // A hub is needed to prove the popularity penalty does something in M4.
        Person hub = svc.findUserByName("Rahul Verma");
        int hubDegree = svc.getConnections(hub).size();
        System.out.println("  hub: Rahul Verma, degree " + hubDegree);
        // Hub-ness is relative to the campus, not an absolute count: on 24 people a
        // degree of 6 against an average of 2.7 is the same structural role that 7
        // played on 40.
        check("Rahul is a genuine hub (2x the average degree)",
                hubDegree >= svc.getAverageDegree() * 2);
    }

    // ---------- the cold-start cases ----------

    private static void coldStart(NetworkService svc) {
        System.out.println("\n=== COLD START ===");
        for (String name : new String[]{"Aarav Jain", "Ira Bhattacharya", "Tanvi Deshmukh"}) {
            Person p = svc.findUserByName(name);
            int degree = p == null ? -1 : svc.getConnections(p).size();
            System.out.printf("  %-20s degree %d, %d interests, year %d%n",
                    name, degree, p == null ? 0 : p.getInterests().size(), p == null ? 0 : p.getYear());
            check(name + " is barely connected (degree < 3)", degree >= 0 && degree < 3);
        }
        Person aarav = svc.findUserByName("Aarav Jain");
        check("Aarav has zero connections (pure cold start)", svc.getConnections(aarav).isEmpty());
    }

    // ---------- persistence ----------

    private static void roundTrip(NetworkService original) throws Exception {
        System.out.println("\n=== ROUND TRIP ===");

        File tmp = File.createTempFile("campus_", ".json");
        tmp.deleteOnExit();
        GraphIO.save(original, tmp);
        System.out.println("  wrote " + tmp.length() + " bytes");

        NetworkService loaded = new NetworkService();
        GraphIO.LoadReport report = GraphIO.load(loaded, tmp);
        System.out.println("  read back: " + report.summary());
        for (String w : report.warnings()) System.out.println("    warning: " + w);

        check("load reported no warnings", report.clean());
        check("student count survives", loaded.getAllUsers().size() == original.getAllUsers().size());
        check("connection count survives", loaded.getEdgeCount() == original.getEdgeCount());

        // Compare one full profile field by field — a count match alone would not catch
        // silently dropped bios, intensities or intents.
        Person before = original.findUserByName("Kabir Khan");
        Person after = loaded.findUserByName("Kabir Khan");
        check("Kabir survives", after != null);
        if (before != null && after != null) {
            check("  id preserved", before.getId().equals(after.getId()));
            check("  emoji preserved", before.getAvatarEmoji().equals(after.getAvatarEmoji()));
            check("  major preserved", before.getMajor().equals(after.getMajor()));
            check("  year preserved", before.getYear() == after.getYear());
            check("  hometown preserved", before.getHometown().equals(after.getHometown()));
            check("  hostel preserved", before.getHostel().equals(after.getHostel()));
            check("  bio preserved", before.getBio().equals(after.getBio()));
            check("  languages preserved", before.getLanguages().equals(after.getLanguages()));
            check("  interests preserved", before.getInterests().equals(after.getInterests()));
            check("  intensities preserved",
                    before.getInterestIntensities().equals(after.getInterestIntensities()));
            check("  intents preserved", before.getLookingFor().equals(after.getLookingFor()));
            check("  canTeach preserved", before.getCanTeach().equals(after.getCanTeach()));
            check("  wantsToLearn preserved", before.getWantsToLearn().equals(after.getWantsToLearn()));
            check("  position preserved",
                    before.getX() == after.getX() && before.getY() == after.getY());
        }

        Person ka = loaded.findUserByName("Kabir Khan");
        Person me = loaded.findUserByName("Meera Joshi");
        check("edge weight 3.0 survives", Math.abs(loaded.getEdgeWeight(ka, me) - 3.0) < 1e-9);

        // Computed metrics must NOT be written — they go stale the moment the graph moves.
        check("metrics are not persisted (communityId still -1)",
                after != null && after.getMetrics().getCommunityId() == -1);
    }

    // ---------- a preview of what M4 has to get right ----------

    private static void previewMatch(NetworkService svc) {
        System.out.println("\n=== M4 PREVIEW: who should Aarav meet? ===");
        Person aarav = svc.findUserByName("Aarav Jain");
        System.out.println("  Aarav (0 friends): " + labels(aarav.getInterests()));
        System.out.println("  looking for: " + aarav.getLookingFor());
        System.out.println();

        // Naive shared-interest count. M4 replaces this with IDF weighting, bio
        // similarity and a popularity penalty — but the right answer should not change.
        List<String> ranked = new ArrayList<>();
        for (Person other : svc.getAllUsers()) {
            if (other.equals(aarav)) continue;
            Set<InterestTag> shared = new LinkedHashSet<>(aarav.getInterests());
            shared.retainAll(other.getInterests());
            if (!shared.isEmpty()) {
                ranked.add(String.format("  %2d shared  %-20s %s",
                        shared.size(), other.getName(), labels(shared)));
            }
        }
        ranked.sort((a, b) -> b.substring(0, 5).trim().compareTo(a.substring(0, 5).trim()));
        ranked.stream().limit(6).forEach(System.out::println);

        boolean kabirFound = ranked.stream().anyMatch(s -> s.contains("Kabir"));
        check("Kabir surfaces for Aarav on interests alone", kabirFound);
    }

    private static String labels(Set<InterestTag> tags) {
        StringBuilder sb = new StringBuilder();
        for (InterestTag t : tags) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(t.label());
        }
        return sb.toString();
    }
}

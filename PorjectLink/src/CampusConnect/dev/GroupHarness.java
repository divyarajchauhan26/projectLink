package CampusConnect.dev;

import CampusConnect.domain.Group;
import CampusConnect.domain.Person;
import CampusConnect.persist.CampusSeed;
import CampusConnect.persist.GraphIO;
import CampusConnect.service.GroupService;
import CampusConnect.service.InsightService;
import CampusConnect.service.NetworkService;

import java.io.File;
import java.util.List;

/**
 * Groups: the derived ones, the created ones, fit scoring, and persistence.
 *
 * <pre>java -cp "out;lib/gson-2.11.0.jar" CampusConnect.dev.GroupHarness</pre>
 */
public class GroupHarness {

    private static int checks = 0, failures = 0;

    private static void check(String label, boolean ok) {
        checks++;
        if (!ok) failures++;
        System.out.println("  " + (ok ? "PASS  " : "FAIL  ") + label);
    }

    public static void main(String[] args) throws Exception {
        NetworkService svc = new NetworkService();
        CampusSeed.load(svc, 1100, 700);
        GroupService groups = new GroupService(svc, new InsightService(svc));

        derived(svc, groups);
        fit(svc, groups);
        persistence(svc, groups);

        System.out.println();
        System.out.println(failures == 0
                ? "=== ALL " + checks + " CHECKS PASSED ==="
                : "=== " + failures + " of " + checks + " CHECKS FAILED ===");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---------- groups nobody had to create ----------

    private static void derived(NetworkService svc, GroupService groups) {
        System.out.println("\n=== INTEREST GROUPS (bipartite projection) ===");
        List<Group> interest = groups.interestGroups();

        for (int i = 0; i < Math.min(8, interest.size()); i++) {
            Group g = interest.get(i);
            System.out.printf("  %-24s %2d people · %s%n",
                    g.getName(), g.size(), groups.cohesionLabel(g));
        }

        check("interest groups were derived", !interest.isEmpty());
        check("none is below the minimum size", interest.stream().allMatch(g -> g.size() >= 3));
        check("all are tagged INTEREST",
                interest.stream().allMatch(g -> g.getOrigin() == Group.Origin.INTEREST));
        check("each carries the tag it came from",
                interest.stream().allMatch(g -> g.getTags().size() == 1));
        check("largest first", interest.size() < 2 || interest.get(0).size() >= interest.get(1).size());

        // Membership must actually match the profiles it was derived from.
        Group first = interest.get(0);
        var tag = first.getTags().iterator().next();
        long actual = svc.getAllUsers().stream().filter(p -> p.hasInterest(tag)).count();
        check("membership matches who holds the tag", first.size() == actual);

        System.out.println("\n=== SQUAD SUGGESTIONS (from cliques) ===");
        List<Group> squads = groups.suggestedSquads(3);
        for (int i = 0; i < Math.min(4, squads.size()); i++) {
            System.out.printf("  %-32s %d people%n", squads.get(i).getName(), squads.get(i).size());
        }
        check("squads were suggested", !squads.isEmpty());
        check("squad members all know each other", squads.stream().allMatch(g -> {
            List<Person> m = groups.membersOf(g);
            for (int i = 0; i < m.size(); i++) {
                for (int j = i + 1; j < m.size(); j++) {
                    if (!svc.getConnections(m.get(i)).contains(m.get(j))) return false;
                }
            }
            return true;
        }));
        // A clique is fully connected by definition, so cohesion must be exactly 1.
        check("a squad's cohesion is 1.0",
                squads.stream().allMatch(g -> Math.abs(groups.cohesion(g) - 1.0) < 1e-9));
    }

    // ---------- fit ----------

    private static void fit(NetworkService svc, GroupService groups) {
        System.out.println("\n=== WHERE WOULD THEY FIT? ===");

        for (String name : new String[]{"Aarav Jain", "Tanvi Deshmukh", "Om Prakash"}) {
            Person p = svc.findUserByName(name);
            List<GroupService.Fit> fits = groups.groupsYoudFitInto(p, 3);
            System.out.println("  " + name + ":");
            for (GroupService.Fit f : fits) {
                System.out.printf("    %.0f%%  %-22s knows %d%n",
                        f.score() * 100, f.group().getName(), f.membersKnown());
            }
            check(name + " gets group suggestions", !fits.isEmpty());
            check(name + ": fits are ordered best first",
                    fits.size() < 2 || fits.get(0).score() >= fits.get(1).score());
            check(name + ": never suggested a group they are in",
                    fits.stream().noneMatch(f -> f.group().contains(p)));
            check(name + ": scores stay within [0,1]",
                    fits.stream().allMatch(f -> f.score() >= 0 && f.score() <= 1.0001));
        }

        // Aarav already holds the guitar tag, so he is a member of the Guitar group by
        // definition and must NOT be suggested it. For interest groups the useful
        // suggestion is the opposite: a group whose subject he has not listed, whose
        // members nonetheless overlap with him — which is how a person discovers an
        // interest through people rather than the other way round.
        Person aarav = svc.findUserByName("Aarav Jain");
        List<GroupService.Fit> aaravFits = groups.groupsYoudFitInto(aarav, 8);

        check("Aarav is not suggested groups he already belongs to",
                aaravFits.stream().noneMatch(f -> f.group().contains(aarav)));

        // Every suggestion should come from shared ground, not noise: at least the top
        // one must have members who overlap with his interests.
        GroupService.Fit top = aaravFits.get(0);
        boolean overlap = groups.membersOf(top.group()).stream()
                .anyMatch(m -> m.getInterests().stream().anyMatch(aarav::hasInterest));
        check("Aarav's top group has members who share his interests", overlap);

        // And with zero connections, the score must be carried entirely by interests.
        check("Aarav's top group is scored without any social signal", top.membersKnown() == 0);
    }

    // ---------- persistence ----------

    private static void persistence(NetworkService svc, GroupService groups) throws Exception {
        System.out.println("\n=== PERSISTENCE ===");

        Group made = new Group("The Test Squad", Group.Origin.USER);
        made.setDescription("hand made");
        for (Person p : List.of(svc.findUserByName("Kabir Khan"),
                                svc.findUserByName("Meera Joshi"))) {
            made.addMember(p);
        }
        groups.add(made);

        File tmp = File.createTempFile("groups_", ".json");
        tmp.deleteOnExit();
        GraphIO.save(svc, groups.all(), tmp);

        NetworkService loadedSvc = new NetworkService();
        GraphIO.LoadReport report = GraphIO.load(loadedSvc, tmp);
        System.out.println("  " + report.summary());

        check("the created group round-trips", report.groups().size() == 1);
        Group back = report.groups().get(0);
        check("name survives", back.getName().equals("The Test Squad"));
        check("description survives", back.getDescription().equals("hand made"));
        check("origin survives", back.getOrigin() == Group.Origin.USER);
        check("members survive", back.getMemberIds().size() == 2);

        // Derived groups must never be written — a saved copy would drift out of step
        // with the profiles it came from.
        List<Group> mixed = new java.util.ArrayList<>(groups.interestGroups());
        mixed.add(made);
        File tmp2 = File.createTempFile("groups2_", ".json");
        tmp2.deleteOnExit();
        GraphIO.save(svc, mixed, tmp2);
        GraphIO.LoadReport r2 = GraphIO.load(new NetworkService(), tmp2);
        check("derived interest groups are not persisted", r2.groups().size() == 1);
    }
}

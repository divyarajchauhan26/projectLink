package CampusConnect.service;

import CampusConnect.algorithm.similarity.InterestSimilarity;
import CampusConnect.domain.Group;
import CampusConnect.domain.InterestTag;
import CampusConnect.domain.Person;

import java.util.*;

/**
 * Groups: creating them, finding them, and deciding which ones a person would fit.
 * <p>
 * The interesting part is that groups do not have to be created by hand. Two structures
 * already in the graph propose them for free:
 * <ul>
 *   <li><b>Cliques</b> — a set of people who all already know each other is a friend group
 *       that exists in real life but has never been named. The app can offer the name
 *       rather than asking somebody to build the group from nothing.</li>
 *   <li><b>The person↔interest bipartite graph</b> — everyone who plays badminton is
 *       already a group in every sense except that nobody wrote it down. Projecting that
 *       bipartite graph gives interest crowds with no extra data at all.</li>
 * </ul>
 */
public final class GroupService {

    /** Below this, an interest crowd is too small to be worth calling a group. */
    private static final int MIN_INTEREST_GROUP = 3;

    private final NetworkService service;
    private final InsightService insights;
    private final List<Group> groups = new ArrayList<>();

    public GroupService(NetworkService service, InsightService insights) {
        this.service = service;
        this.insights = insights;
    }

    // ================= the store =================

    public List<Group> all() { return Collections.unmodifiableList(groups); }

    public void add(Group group) {
        if (group != null && !groups.contains(group)) groups.add(group);
    }

    public void remove(Group group) { groups.remove(group); }

    public void clear() { groups.clear(); }

    /** Members of a group, resolved back to people. Missing ids are skipped. */
    public List<Person> membersOf(Group group) {
        List<Person> out = new ArrayList<>();
        for (String id : group.getMemberIds()) {
            Person p = service.findUserById(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    public List<Group> groupsFor(Person person) {
        List<Group> out = new ArrayList<>();
        for (Group g : groups) if (g.contains(person)) out.add(g);
        return out;
    }

    // ================= derived groups =================

    /**
     * Interest crowds, from the person↔interest bipartite graph.
     * <p>
     * Rebuilt rather than stored: membership is simply "who holds this tag", so persisting
     * it would let it drift out of step with the profiles it is derived from.
     */
    public List<Group> interestGroups() {
        Map<InterestTag, List<Person>> byTag = new LinkedHashMap<>();
        for (Person p : service.getAllUsers()) {
            for (InterestTag t : p.getInterests()) {
                byTag.computeIfAbsent(t, k -> new ArrayList<>()).add(p);
            }
        }

        List<Group> out = new ArrayList<>();
        for (Map.Entry<InterestTag, List<Person>> e : byTag.entrySet()) {
            if (e.getValue().size() < MIN_INTEREST_GROUP) continue;
            Group g = new Group("id:" + e.getKey().id(),
                    e.getKey().label(), Group.Origin.INTEREST);
            g.addTag(e.getKey());
            g.setDescription("Everyone into " + e.getKey().label());
            for (Person p : e.getValue()) g.addMember(p);
            out.add(g);
        }
        out.sort((a, b) -> b.size() - a.size());
        return out;
    }

    /**
     * Groups proposed from cliques — people who all already know each other but have
     * never named themselves.
     */
    public List<Group> suggestedSquads(int minSize) {
        List<Group> out = new ArrayList<>();
        for (InsightService.Squad squad : insights.squads(minSize)) {
            // Named after its members, because nothing else about a clique is unique:
            // two different squads can share both their strongest interest and their
            // alphabetically-first member, and then the user is picking blind from a
            // dropdown of identical labels.
            Group g = new Group(nameFromMembers(squad.members()), Group.Origin.SQUAD);
            g.setDescription(squad.size() + " people who all know each other"
                    + (squad.sharedInterest() == null ? "" : " · all into " + squad.sharedInterest()));
            for (Person p : squad.members()) g.addMember(p);
            out.add(g);
        }
        return out;
    }

    /** "Aditya, Rhea &amp; Sneha", or "Aditya, Rhea + 3 more" once that gets unwieldy. */
    private static String nameFromMembers(List<Person> members) {
        List<String> first = new ArrayList<>();
        for (Person p : members) first.add(p.getName().split(" ")[0]);

        if (first.size() <= 3) {
            if (first.size() == 1) return first.get(0);
            return String.join(", ", first.subList(0, first.size() - 1))
                    + " & " + first.get(first.size() - 1);
        }
        return first.get(0) + ", " + first.get(1) + " + " + (first.size() - 2) + " more";
    }

    // ================= fit =================

    /**
     * How well a person suits a group, in [0,1].
     * <p>
     * Blends how much of the group's shared interest they hold with how many members they
     * already know. Both matter and neither is sufficient: matching the interests of a
     * group where you know nobody is intimidating, and knowing everybody in a group whose
     * whole subject bores you is pointless.
     */
    public double fitScore(Person person, Group group) {
        if (person == null || group == null || group.contains(person)) return 0;

        List<Person> members = membersOf(group);
        if (members.isEmpty()) return 0;

        InterestSimilarity idf = InterestSimilarity.build(service.getAllUsers());

        // Interest overlap with the group's aggregate profile, weighted by rarity.
        Map<InterestTag, Integer> groupTags = new HashMap<>();
        for (Person m : members) {
            for (InterestTag t : m.getInterests()) groupTags.merge(t, 1, Integer::sum);
        }
        double matched = 0, available = 0;
        for (Map.Entry<InterestTag, Integer> e : groupTags.entrySet()) {
            double coverage = (double) e.getValue() / members.size();
            double weight = coverage * idf.idf(e.getKey());
            available += weight;
            if (person.hasInterest(e.getKey())) matched += weight;
        }
        double interestFit = available == 0 ? 0 : matched / available;

        long known = members.stream().filter(m -> service.getConnections(person).contains(m)).count();
        double socialFit = (double) known / members.size();

        return 0.7 * interestFit + 0.3 * socialFit;
    }

    public record Fit(Group group, double score, int membersKnown) {}

    /** Groups this person would slot into, best first. */
    public List<Fit> groupsYoudFitInto(Person person, int limit) {
        List<Group> candidates = new ArrayList<>(groups);
        candidates.addAll(interestGroups());

        List<Fit> fits = new ArrayList<>();
        for (Group g : candidates) {
            if (g.contains(person)) continue;
            double score = fitScore(person, g);
            if (score <= 0.05) continue;
            long known = membersOf(g).stream()
                    .filter(m -> service.getConnections(person).contains(m)).count();
            fits.add(new Fit(g, score, (int) known));
        }
        fits.sort((a, b) -> Double.compare(b.score(), a.score()));
        return fits.size() <= limit ? fits : fits.subList(0, limit);
    }

    // ================= health =================

    /**
     * Fraction of possible internal friendships that exist.
     * <p>
     * A high number means everyone already knows everyone, which is comfortable for
     * members and forbidding to a newcomer. A low number means the group is a collection
     * of strangers with a shared label — which is exactly where an introduction helps
     * most. Neither is "good"; the number is only useful once you know which you have.
     */
    public double cohesion(Group group) {
        List<Person> members = membersOf(group);
        int n = members.size();
        if (n < 2) return 0;

        Set<Person> inside = new HashSet<>(members);
        int edges = 0;
        for (Person p : members) {
            for (Person q : service.getConnections(p)) if (inside.contains(q)) edges++;
        }
        return (edges / 2.0) / (n * (n - 1) / 2.0);
    }

    public String cohesionLabel(Group group) {
        double c = cohesion(group);
        if (c >= 0.7) return "very tight — hard to break into";
        if (c >= 0.4) return "close-knit";
        if (c >= 0.15) return "friendly";
        return "mostly strangers — a good place to introduce people";
    }
}

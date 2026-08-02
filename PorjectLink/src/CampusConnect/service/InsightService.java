package CampusConnect.service;

import CampusConnect.algorithm.CentralityMetrics;
import CampusConnect.algorithm.CommunityDetection;
import CampusConnect.algorithm.GraphAnalyzer;
import CampusConnect.algorithm.similarity.InterestSimilarity;
import CampusConnect.domain.InterestTag;
import CampusConnect.domain.Person;

import java.util.*;

/**
 * Turns the graph algorithms into sentences about people.
 * <p>
 * Every algorithm here already existed and was already correct — Louvain, Brandes'
 * betweenness, Bron-Kerbosch, connected components. What they lacked was a reason for a
 * student to care. "Modularity: 0.412" and "Betweenness: 0.83" are true statements that
 * answer nobody's question.
 * <p>
 * The rule applied throughout: <b>if an algorithm's output cannot be phrased as a sentence
 * about a specific person, it does not belong in the menu.</b> That is what removed
 * waypoint routing and cycle detection, and what turned community detection into "your
 * circles" and connected components into "who the network is failing".
 */
public final class InsightService {

    private final NetworkService service;

    public InsightService(NetworkService service) {
        this.service = service;
    }

    // ================= circles =================

    /** A detected community, named after what its members actually have in common. */
    public record Circle(int id, String name, List<Person> members, double density) {
        public int size() { return members.size(); }
    }

    /**
     * Run community detection and name each cluster after its most distinctive shared
     * interest.
     * <p>
     * The naming is the point. "Circle 3" is a debugging artefact; "the Guitar crowd" is
     * something a student recognises as their friends. Names come from the interest with
     * the highest <em>coverage × rarity</em> within the circle — coverage alone would name
     * half the circles after Programming, and rarity alone would pick something one member
     * happens to like.
     */
    public List<Circle> circles() {
        List<Person> people = service.getAllUsers();
        if (people.isEmpty()) return List.of();

        Map<Integer, List<Person>> detected =
                CommunityDetection.detectCommunities(people, service.getAdjacencyList());
        InterestSimilarity idf = InterestSimilarity.build(people);

        List<Circle> circles = new ArrayList<>();
        for (Map.Entry<Integer, List<Person>> e : detected.entrySet()) {
            List<Person> members = e.getValue();
            circles.add(new Circle(e.getKey(), nameFor(members, idf), members, density(members)));
        }
        circles.sort((a, b) -> b.size() - a.size());
        return circles;
    }

    private String nameFor(List<Person> members, InterestSimilarity idf) {
        if (members.size() == 1) return members.get(0).getName() + " (on their own)";

        Map<InterestTag, Integer> holders = new HashMap<>();
        for (Person p : members) {
            for (InterestTag t : p.getInterests()) holders.merge(t, 1, Integer::sum);
        }
        if (holders.isEmpty()) return "Circle of " + members.size();

        List<Map.Entry<InterestTag, Integer>> ranked = new ArrayList<>(holders.entrySet());
        ranked.sort((a, b) -> Double.compare(
                weight(b, members.size(), idf), weight(a, members.size(), idf)));

        // A tag only names a circle if a real share of the circle holds it, otherwise the
        // name describes one person rather than the group.
        List<String> strong = new ArrayList<>();
        for (Map.Entry<InterestTag, Integer> entry : ranked) {
            if (entry.getValue() * 2 >= members.size() || entry.getValue() >= 3) {
                strong.add(entry.getKey().label());
            }
            if (strong.size() == 2) break;
        }

        if (strong.isEmpty()) return "Circle of " + members.size();
        if (strong.size() == 1) return "The " + strong.get(0) + " crowd";
        return "The " + strong.get(0) + " & " + strong.get(1) + " crowd";
    }

    private static double weight(Map.Entry<InterestTag, Integer> e, int size, InterestSimilarity idf) {
        double coverage = (double) e.getValue() / size;
        return coverage * idf.idf(e.getKey());
    }

    /** Fraction of possible internal connections that exist — cliquey vs welcoming. */
    private double density(List<Person> members) {
        int n = members.size();
        if (n < 2) return 0;
        Set<Person> inside = new HashSet<>(members);
        int edges = 0;
        for (Person p : members) {
            for (Person q : service.getConnections(p)) if (inside.contains(q)) edges++;
        }
        return (edges / 2.0) / (n * (n - 1) / 2.0);
    }

    // ================= squads =================

    /** A group where genuinely everybody knows everybody. */
    public record Squad(List<Person> members, String sharedInterest) {
        public int size() { return members.size(); }
    }

    /**
     * Maximal cliques, reframed.
     * <p>
     * A clique of five is not an abstraction — it is a group of five people who all
     * actually know each other, which is what a friend group <em>is</em>. This is also the
     * natural seed for user-created groups later: the app can propose one rather than
     * asking somebody to build it from nothing.
     */
    public List<Squad> squads(int minSize) {
        List<Person> people = service.getAllUsers();
        if (people.isEmpty()) return List.of();

        InterestSimilarity idf = InterestSimilarity.build(people);
        List<Squad> squads = new ArrayList<>();

        for (Set<Person> clique :
                GraphAnalyzer.findMaximalCliques(people, service.getAdjacencyList())) {
            if (clique.size() < minSize) continue;
            List<Person> members = new ArrayList<>(clique);
            members.sort(Comparator.comparing(Person::getName));
            squads.add(new Squad(members, commonInterest(members, idf)));
        }
        squads.sort((a, b) -> b.size() - a.size());
        return squads;
    }

    /** The rarest interest every member shares, or null if they share none. */
    private String commonInterest(List<Person> members, InterestSimilarity idf) {
        if (members.isEmpty()) return null;
        Set<InterestTag> shared = new LinkedHashSet<>(members.get(0).getInterests());
        for (Person p : members) shared.retainAll(p.getInterests());
        return shared.stream()
                .max(Comparator.comparingDouble(idf::idf))
                .map(InterestTag::label)
                .orElse(null);
    }

    // ================= who the network is failing =================

    /**
     * Students the network is not serving.
     * <p>
     * This inverts the usual question. A recommender normally asks "what does this user
     * want"; this asks "who is nobody finding". Those are the people most likely to give
     * up on campus life, and the graph already knows exactly who they are — it was just
     * never asked.
     */
    public record Isolated(Person person, int degree, String reason) {}

    public List<Isolated> isolated() {
        List<Isolated> out = new ArrayList<>();
        List<List<Person>> components =
                GraphAnalyzer.findConnectedComponents(service.getAllUsers(), service.getAdjacencyList());

        int largest = components.stream().mapToInt(List::size).max().orElse(0);

        for (List<Person> component : components) {
            boolean cutOff = component.size() < largest && component.size() <= 3;
            for (Person p : component) {
                int degree = service.getConnections(p).size();
                if (degree == 0) {
                    out.add(new Isolated(p, 0, "knows nobody yet"));
                } else if (cutOff) {
                    out.add(new Isolated(p, degree,
                            "cut off from the main campus in a group of " + component.size()));
                } else if (degree == 1) {
                    out.add(new Isolated(p, 1, "has only one connection"));
                }
            }
        }
        out.sort(Comparator.comparingInt(Isolated::degree));
        return out;
    }

    // ================= personal insight =================

    /**
     * What role this person plays, derived from degree, betweenness and clustering.
     * <p>
     * Three numbers nobody would read on their own, combined into one word somebody would
     * repeat to a friend.
     */
    public String archetype(Person person) {
        List<Person> people = service.getAllUsers();
        if (people.size() < 3) return "Newcomer";

        int degree = service.getConnections(person).size();
        double avgDegree = service.getAverageDegree();
        double clustering = service.clusteringCoefficient(person);

        Map<Person, Double> betweenness =
                CentralityMetrics.betweennessCentrality(people, service.getAdjacencyList());
        double bridge = betweenness.getOrDefault(person, 0.0);

        if (degree == 0) return "Newcomer";
        // Deliberately not "Just Arrived". This is derived from degree alone, and a
        // fourth-year with two close friends is not new — claiming otherwise states
        // something about time that the graph never measured, and reads as a judgement.
        if (degree <= 2) return "Quiet One";
        if (bridge > 0.45) return "The Bridge";
        if (degree > avgDegree * 1.6) return "Connector";
        if (clustering > 0.6) return "Loyalist";
        if (clustering < 0.25 && degree >= avgDegree) return "Explorer";
        return "Regular";
    }

    /** "You are 2 handshakes from 34 people." */
    public int reachWithin(Person person, int hops) {
        Set<Person> seen = new HashSet<>();
        Set<Person> frontier = new HashSet<>();
        frontier.add(person);
        seen.add(person);

        for (int h = 0; h < hops; h++) {
            Set<Person> next = new HashSet<>();
            for (Person p : frontier) {
                for (Person n : service.getConnections(p)) {
                    if (seen.add(n)) next.add(n);
                }
            }
            frontier = next;
            if (frontier.isEmpty()) break;
        }
        return seen.size() - 1; // exclude the person themselves
    }

    /**
     * Which circles this person personally holds together.
     *
     * @return the names of circles they connect that would otherwise be further apart,
     *         or an empty list when they bridge nothing
     */
    public List<String> bridgesBetween(Person person, List<Circle> circles) {
        Map<Integer, String> nameById = new HashMap<>();
        for (Circle c : circles) nameById.put(c.id(), c.name());

        int own = person.getMetrics().getCommunityId();
        Set<String> others = new LinkedHashSet<>();
        for (Person friend : service.getConnections(person)) {
            int theirs = friend.getMetrics().getCommunityId();
            if (theirs != own && nameById.containsKey(theirs)) others.add(nameById.get(theirs));
        }
        return new ArrayList<>(others);
    }
}

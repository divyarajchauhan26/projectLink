package CampusConnect.algorithm;

import CampusConnect.domain.Person;

import java.util.*;

/**
 * Structural graph analysis algorithms:
 * - Bridge detection (Tarjan's)
 * - Articulation point detection
 * - Clique detection (Bron-Kerbosch)
 * - Cycle detection
 * - Diameter & Radius computation
 * - Connected components
 */
public class GraphAnalyzer {

    // ========== BRIDGES (Tarjan's Algorithm) ==========

    /**
     * Find all bridge edges — edges whose removal disconnects the graph.
     * @return list of pairs [u, v] representing bridge edges
     */
    public static List<Person[]> findBridges(
            List<Person> users,
            Map<Person, List<Person>> adjacencyList) {

        List<Person[]> bridges = new ArrayList<>();
        Map<Person, Integer> disc = new HashMap<>();
        Map<Person, Integer> low = new HashMap<>();
        Set<Person> visited = new HashSet<>();
        int[] timer = {0};

        for (Person u : users) {
            if (!visited.contains(u)) {
                bridgeDFS(u, null, disc, low, visited, adjacencyList, bridges, timer);
            }
        }
        return bridges;
    }

    private static void bridgeDFS(Person u, Person parent,
                                   Map<Person, Integer> disc, Map<Person, Integer> low,
                                   Set<Person> visited, Map<Person, List<Person>> adj,
                                   List<Person[]> bridges, int[] timer) {
        visited.add(u);
        disc.put(u, timer[0]);
        low.put(u, timer[0]);
        timer[0]++;

        for (Person v : adj.getOrDefault(u, Collections.emptyList())) {
            if (!visited.contains(v)) {
                bridgeDFS(v, u, disc, low, visited, adj, bridges, timer);
                low.put(u, Math.min(low.get(u), low.get(v)));

                // If low[v] > disc[u], then u-v is a bridge
                if (low.get(v) > disc.get(u)) {
                    bridges.add(new Person[]{u, v});
                }
            } else if (!v.equals(parent)) {
                low.put(u, Math.min(low.get(u), disc.get(v)));
            }
        }
    }

    // ========== ARTICULATION POINTS ==========

    /**
     * Find all articulation points — nodes whose removal disconnects the graph.
     */
    public static Set<Person> findArticulationPoints(
            List<Person> users,
            Map<Person, List<Person>> adjacencyList) {

        Set<Person> articulationPoints = new HashSet<>();
        Map<Person, Integer> disc = new HashMap<>();
        Map<Person, Integer> low = new HashMap<>();
        Set<Person> visited = new HashSet<>();
        int[] timer = {0};

        for (Person u : users) {
            if (!visited.contains(u)) {
                articulationDFS(u, null, disc, low, visited, adjacencyList, articulationPoints, timer);
            }
        }
        return articulationPoints;
    }

    private static void articulationDFS(Person u, Person parent,
                                         Map<Person, Integer> disc, Map<Person, Integer> low,
                                         Set<Person> visited, Map<Person, List<Person>> adj,
                                         Set<Person> points, int[] timer) {
        visited.add(u);
        disc.put(u, timer[0]);
        low.put(u, timer[0]);
        timer[0]++;
        int children = 0;

        for (Person v : adj.getOrDefault(u, Collections.emptyList())) {
            if (!visited.contains(v)) {
                children++;
                articulationDFS(v, u, disc, low, visited, adj, points, timer);
                low.put(u, Math.min(low.get(u), low.get(v)));

                // u is an articulation point if:
                // 1) u is root and has 2+ children
                if (parent == null && children > 1) points.add(u);
                // 2) u is not root and low[v] >= disc[u]
                if (parent != null && low.get(v) >= disc.get(u)) points.add(u);
            } else if (!v.equals(parent)) {
                low.put(u, Math.min(low.get(u), disc.get(v)));
            }
        }
    }

    // ========== CLIQUE DETECTION (Bron-Kerbosch) ==========

    /**
     * Find all maximal cliques using the Bron-Kerbosch algorithm with pivoting.
     */
    public static List<Set<Person>> findMaximalCliques(
            List<Person> users,
            Map<Person, List<Person>> adjacencyList) {

        List<Set<Person>> cliques = new ArrayList<>();
        Set<Person> r = new HashSet<>();
        Set<Person> p = new HashSet<>(users);
        Set<Person> x = new HashSet<>();

        bronKerbosch(r, p, x, adjacencyList, cliques);
        return cliques;
    }

    private static void bronKerbosch(Set<Person> r, Set<Person> p, Set<Person> x,
                                      Map<Person, List<Person>> adj,
                                      List<Set<Person>> cliques) {
        if (p.isEmpty() && x.isEmpty()) {
            if (r.size() >= 2) { // Only report cliques of size >= 2
                cliques.add(new HashSet<>(r));
            }
            return;
        }

        // Choose pivot: node in P ∪ X with most connections to P
        Set<Person> union = new HashSet<>(p);
        union.addAll(x);
        Person pivot = null;
        int maxNeighborsInP = -1;
        for (Person u : union) {
            int count = 0;
            for (Person neighbor : adj.getOrDefault(u, Collections.emptyList())) {
                if (p.contains(neighbor)) count++;
            }
            if (count > maxNeighborsInP) {
                maxNeighborsInP = count;
                pivot = u;
            }
        }

        // P \ N(pivot)
        Set<Person> candidates = new HashSet<>(p);
        if (pivot != null) {
            candidates.removeAll(adj.getOrDefault(pivot, Collections.emptyList()));
        }

        for (Person v : candidates) {
            Set<Person> neighborsOfV = new HashSet<>(adj.getOrDefault(v, Collections.emptyList()));

            Set<Person> newR = new HashSet<>(r);
            newR.add(v);
            Set<Person> newP = new HashSet<>(p);
            newP.retainAll(neighborsOfV);
            Set<Person> newX = new HashSet<>(x);
            newX.retainAll(neighborsOfV);

            bronKerbosch(newR, newP, newX, adj, cliques);

            p.remove(v);
            x.add(v);
        }
    }

    /**
     * Find the single largest clique.
     */
    public static Set<Person> findLargestClique(
            List<Person> users,
            Map<Person, List<Person>> adjacencyList) {
        List<Set<Person>> all = findMaximalCliques(users, adjacencyList);
        return all.stream().max(Comparator.comparingInt(Set::size)).orElse(Collections.emptySet());
    }

    // ========== CYCLE DETECTION ==========

    /**
     * Detect if the graph contains any cycle.
     */
    public static boolean hasCycle(
            List<Person> users,
            Map<Person, List<Person>> adjacencyList) {

        Set<Person> visited = new HashSet<>();
        for (Person u : users) {
            if (!visited.contains(u)) {
                if (cycleDFS(u, null, visited, adjacencyList)) return true;
            }
        }
        return false;
    }

    private static boolean cycleDFS(Person u, Person parent,
                                     Set<Person> visited,
                                     Map<Person, List<Person>> adj) {
        visited.add(u);
        for (Person v : adj.getOrDefault(u, Collections.emptyList())) {
            if (!visited.contains(v)) {
                if (cycleDFS(v, u, visited, adj)) return true;
            } else if (!v.equals(parent)) {
                return true; // Back edge found
            }
        }
        return false;
    }

    // ========== DIAMETER & RADIUS ==========

    /**
     * Result of diameter/radius computation.
     */
    public static class DiameterResult {
        public final int diameter;           // Longest shortest path
        public final int radius;             // Shortest eccentricity
        public final Person centerNode;    // Node with minimum eccentricity
        public final Person peripheralNode;// Node with maximum eccentricity
        public final Map<Person, Integer> eccentricities;

        public DiameterResult(int diameter, int radius, Person center,
                              Person peripheral, Map<Person, Integer> eccentricities) {
            this.diameter = diameter;
            this.radius = radius;
            this.centerNode = center;
            this.peripheralNode = peripheral;
            this.eccentricities = eccentricities;
        }
    }

    /**
     * Compute network diameter, radius, center, and periphery.
     */
    public static DiameterResult computeDiameterAndRadius(
            List<Person> users,
            Map<Person, List<Person>> adjacencyList) {

        Map<Person, Integer> eccentricities = new HashMap<>();
        int diameter = 0;
        int radius = Integer.MAX_VALUE;
        Person center = null;
        Person peripheral = null;

        for (Person source : users) {
            // BFS from source
            Map<Person, Integer> distances = new HashMap<>();
            Queue<Person> queue = new LinkedList<>();
            distances.put(source, 0);
            queue.add(source);

            int maxDist = 0;
            while (!queue.isEmpty()) {
                Person current = queue.poll();
                for (Person neighbor : adjacencyList.getOrDefault(current, Collections.emptyList())) {
                    if (!distances.containsKey(neighbor)) {
                        int d = distances.get(current) + 1;
                        distances.put(neighbor, d);
                        maxDist = Math.max(maxDist, d);
                        queue.add(neighbor);
                    }
                }
            }

            eccentricities.put(source, maxDist);
            if (maxDist > diameter) {
                diameter = maxDist;
                peripheral = source;
            }
            if (maxDist < radius && maxDist > 0) {
                radius = maxDist;
                center = source;
            }
        }

        if (radius == Integer.MAX_VALUE) radius = 0;
        return new DiameterResult(diameter, radius, center, peripheral, eccentricities);
    }

    // ========== CONNECTED COMPONENTS ==========

    /**
     * Find all connected components using BFS.
     */
    public static List<List<Person>> findConnectedComponents(
            List<Person> users,
            Map<Person, List<Person>> adjacencyList) {

        List<List<Person>> components = new ArrayList<>();
        Set<Person> visited = new HashSet<>();

        for (Person u : users) {
            if (!visited.contains(u)) {
                List<Person> component = new ArrayList<>();
                Queue<Person> queue = new LinkedList<>();
                queue.add(u);
                visited.add(u);

                while (!queue.isEmpty()) {
                    Person current = queue.poll();
                    component.add(current);
                    for (Person neighbor : adjacencyList.getOrDefault(current, Collections.emptyList())) {
                        if (!visited.contains(neighbor)) {
                            visited.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
                components.add(component);
            }
        }
        return components;
    }
}

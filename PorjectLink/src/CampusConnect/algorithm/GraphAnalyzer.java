package CampusConnect.algorithm;

import CampusConnect.domain.UserNode;

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
    public static List<UserNode[]> findBridges(
            List<UserNode> users,
            Map<UserNode, List<UserNode>> adjacencyList) {

        List<UserNode[]> bridges = new ArrayList<>();
        Map<UserNode, Integer> disc = new HashMap<>();
        Map<UserNode, Integer> low = new HashMap<>();
        Set<UserNode> visited = new HashSet<>();
        int[] timer = {0};

        for (UserNode u : users) {
            if (!visited.contains(u)) {
                bridgeDFS(u, null, disc, low, visited, adjacencyList, bridges, timer);
            }
        }
        return bridges;
    }

    private static void bridgeDFS(UserNode u, UserNode parent,
                                   Map<UserNode, Integer> disc, Map<UserNode, Integer> low,
                                   Set<UserNode> visited, Map<UserNode, List<UserNode>> adj,
                                   List<UserNode[]> bridges, int[] timer) {
        visited.add(u);
        disc.put(u, timer[0]);
        low.put(u, timer[0]);
        timer[0]++;

        for (UserNode v : adj.getOrDefault(u, Collections.emptyList())) {
            if (!visited.contains(v)) {
                bridgeDFS(v, u, disc, low, visited, adj, bridges, timer);
                low.put(u, Math.min(low.get(u), low.get(v)));

                // If low[v] > disc[u], then u-v is a bridge
                if (low.get(v) > disc.get(u)) {
                    bridges.add(new UserNode[]{u, v});
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
    public static Set<UserNode> findArticulationPoints(
            List<UserNode> users,
            Map<UserNode, List<UserNode>> adjacencyList) {

        Set<UserNode> articulationPoints = new HashSet<>();
        Map<UserNode, Integer> disc = new HashMap<>();
        Map<UserNode, Integer> low = new HashMap<>();
        Set<UserNode> visited = new HashSet<>();
        int[] timer = {0};

        for (UserNode u : users) {
            if (!visited.contains(u)) {
                articulationDFS(u, null, disc, low, visited, adjacencyList, articulationPoints, timer);
            }
        }
        return articulationPoints;
    }

    private static void articulationDFS(UserNode u, UserNode parent,
                                         Map<UserNode, Integer> disc, Map<UserNode, Integer> low,
                                         Set<UserNode> visited, Map<UserNode, List<UserNode>> adj,
                                         Set<UserNode> points, int[] timer) {
        visited.add(u);
        disc.put(u, timer[0]);
        low.put(u, timer[0]);
        timer[0]++;
        int children = 0;

        for (UserNode v : adj.getOrDefault(u, Collections.emptyList())) {
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
    public static List<Set<UserNode>> findMaximalCliques(
            List<UserNode> users,
            Map<UserNode, List<UserNode>> adjacencyList) {

        List<Set<UserNode>> cliques = new ArrayList<>();
        Set<UserNode> r = new HashSet<>();
        Set<UserNode> p = new HashSet<>(users);
        Set<UserNode> x = new HashSet<>();

        bronKerbosch(r, p, x, adjacencyList, cliques);
        return cliques;
    }

    private static void bronKerbosch(Set<UserNode> r, Set<UserNode> p, Set<UserNode> x,
                                      Map<UserNode, List<UserNode>> adj,
                                      List<Set<UserNode>> cliques) {
        if (p.isEmpty() && x.isEmpty()) {
            if (r.size() >= 2) { // Only report cliques of size >= 2
                cliques.add(new HashSet<>(r));
            }
            return;
        }

        // Choose pivot: node in P ∪ X with most connections to P
        Set<UserNode> union = new HashSet<>(p);
        union.addAll(x);
        UserNode pivot = null;
        int maxNeighborsInP = -1;
        for (UserNode u : union) {
            int count = 0;
            for (UserNode neighbor : adj.getOrDefault(u, Collections.emptyList())) {
                if (p.contains(neighbor)) count++;
            }
            if (count > maxNeighborsInP) {
                maxNeighborsInP = count;
                pivot = u;
            }
        }

        // P \ N(pivot)
        Set<UserNode> candidates = new HashSet<>(p);
        if (pivot != null) {
            candidates.removeAll(adj.getOrDefault(pivot, Collections.emptyList()));
        }

        for (UserNode v : candidates) {
            Set<UserNode> neighborsOfV = new HashSet<>(adj.getOrDefault(v, Collections.emptyList()));

            Set<UserNode> newR = new HashSet<>(r);
            newR.add(v);
            Set<UserNode> newP = new HashSet<>(p);
            newP.retainAll(neighborsOfV);
            Set<UserNode> newX = new HashSet<>(x);
            newX.retainAll(neighborsOfV);

            bronKerbosch(newR, newP, newX, adj, cliques);

            p.remove(v);
            x.add(v);
        }
    }

    /**
     * Find the single largest clique.
     */
    public static Set<UserNode> findLargestClique(
            List<UserNode> users,
            Map<UserNode, List<UserNode>> adjacencyList) {
        List<Set<UserNode>> all = findMaximalCliques(users, adjacencyList);
        return all.stream().max(Comparator.comparingInt(Set::size)).orElse(Collections.emptySet());
    }

    // ========== CYCLE DETECTION ==========

    /**
     * Detect if the graph contains any cycle.
     */
    public static boolean hasCycle(
            List<UserNode> users,
            Map<UserNode, List<UserNode>> adjacencyList) {

        Set<UserNode> visited = new HashSet<>();
        for (UserNode u : users) {
            if (!visited.contains(u)) {
                if (cycleDFS(u, null, visited, adjacencyList)) return true;
            }
        }
        return false;
    }

    private static boolean cycleDFS(UserNode u, UserNode parent,
                                     Set<UserNode> visited,
                                     Map<UserNode, List<UserNode>> adj) {
        visited.add(u);
        for (UserNode v : adj.getOrDefault(u, Collections.emptyList())) {
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
        public final UserNode centerNode;    // Node with minimum eccentricity
        public final UserNode peripheralNode;// Node with maximum eccentricity
        public final Map<UserNode, Integer> eccentricities;

        public DiameterResult(int diameter, int radius, UserNode center,
                              UserNode peripheral, Map<UserNode, Integer> eccentricities) {
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
            List<UserNode> users,
            Map<UserNode, List<UserNode>> adjacencyList) {

        Map<UserNode, Integer> eccentricities = new HashMap<>();
        int diameter = 0;
        int radius = Integer.MAX_VALUE;
        UserNode center = null;
        UserNode peripheral = null;

        for (UserNode source : users) {
            // BFS from source
            Map<UserNode, Integer> distances = new HashMap<>();
            Queue<UserNode> queue = new LinkedList<>();
            distances.put(source, 0);
            queue.add(source);

            int maxDist = 0;
            while (!queue.isEmpty()) {
                UserNode current = queue.poll();
                for (UserNode neighbor : adjacencyList.getOrDefault(current, Collections.emptyList())) {
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
    public static List<List<UserNode>> findConnectedComponents(
            List<UserNode> users,
            Map<UserNode, List<UserNode>> adjacencyList) {

        List<List<UserNode>> components = new ArrayList<>();
        Set<UserNode> visited = new HashSet<>();

        for (UserNode u : users) {
            if (!visited.contains(u)) {
                List<UserNode> component = new ArrayList<>();
                Queue<UserNode> queue = new LinkedList<>();
                queue.add(u);
                visited.add(u);

                while (!queue.isEmpty()) {
                    UserNode current = queue.poll();
                    component.add(current);
                    for (UserNode neighbor : adjacencyList.getOrDefault(current, Collections.emptyList())) {
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

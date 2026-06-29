package CampusConnect.algorithm;

import CampusConnect.domain.UserNode;

import java.util.*;

/**
 * Centrality metrics for social network analysis.
 * - Degree Centrality: how many connections a node has
 * - Closeness Centrality: how close a node is to all others
 * - Betweenness Centrality: how often a node bridges shortest paths (Brandes' algorithm)
 */
public class CentralityMetrics {

    /**
     * Degree Centrality: connections / (n - 1), normalized to [0, 1].
     */
    public static Map<UserNode, Double> degreeCentrality(
            List<UserNode> users,
            Map<UserNode, List<UserNode>> adjacencyList) {

        Map<UserNode, Double> result = new HashMap<>();
        int n = users.size();
        if (n <= 1) {
            for (UserNode u : users) result.put(u, 0.0);
            return result;
        }
        for (UserNode u : users) {
            int degree = adjacencyList.getOrDefault(u, Collections.emptyList()).size();
            result.put(u, (double) degree / (n - 1));
        }
        return result;
    }

    /**
     * Closeness Centrality: (n - 1) / sum of shortest distances to all reachable nodes.
     * Uses BFS for unweighted shortest paths.
     */
    public static Map<UserNode, Double> closenessCentrality(
            List<UserNode> users,
            Map<UserNode, List<UserNode>> adjacencyList) {

        Map<UserNode, Double> result = new HashMap<>();
        int n = users.size();
        if (n <= 1) {
            for (UserNode u : users) result.put(u, 0.0);
            return result;
        }

        for (UserNode source : users) {
            // BFS from source
            Map<UserNode, Integer> distances = bfsDistances(source, adjacencyList);
            int reachable = distances.size() - 1; // exclude self
            if (reachable == 0) {
                result.put(source, 0.0);
                continue;
            }
            double sumDist = 0;
            for (Map.Entry<UserNode, Integer> entry : distances.entrySet()) {
                if (!entry.getKey().equals(source)) {
                    sumDist += entry.getValue();
                }
            }
            // Wasserman-Faust normalization for disconnected graphs
            double closeness = (double) reachable / (n - 1) * (reachable / sumDist);
            result.put(source, closeness);
        }
        return result;
    }

    /**
     * Betweenness Centrality using Brandes' algorithm — O(VE).
     * Counts how often each node sits on shortest paths between other pairs.
     */
    public static Map<UserNode, Double> betweennessCentrality(
            List<UserNode> users,
            Map<UserNode, List<UserNode>> adjacencyList) {

        Map<UserNode, Double> centrality = new HashMap<>();
        for (UserNode u : users) centrality.put(u, 0.0);

        for (UserNode s : users) {
            // --- Brandes' single-source ---
            Stack<UserNode> stack = new Stack<>();
            Map<UserNode, List<UserNode>> predecessors = new HashMap<>();
            Map<UserNode, Integer> sigma = new HashMap<>();    // # shortest paths
            Map<UserNode, Integer> dist = new HashMap<>();     // distance from s
            Map<UserNode, Double> delta = new HashMap<>();     // dependency

            for (UserNode u : users) {
                predecessors.put(u, new ArrayList<>());
                sigma.put(u, 0);
                dist.put(u, -1);
                delta.put(u, 0.0);
            }
            sigma.put(s, 1);
            dist.put(s, 0);

            Queue<UserNode> queue = new LinkedList<>();
            queue.add(s);

            while (!queue.isEmpty()) {
                UserNode v = queue.poll();
                stack.push(v);

                for (UserNode w : adjacencyList.getOrDefault(v, Collections.emptyList())) {
                    // w discovered for first time?
                    if (dist.get(w) < 0) {
                        dist.put(w, dist.get(v) + 1);
                        queue.add(w);
                    }
                    // shortest path to w via v?
                    if (dist.get(w) == dist.get(v) + 1) {
                        sigma.put(w, sigma.get(w) + sigma.get(v));
                        predecessors.get(w).add(v);
                    }
                }
            }

            // Accumulation
            while (!stack.isEmpty()) {
                UserNode w = stack.pop();
                for (UserNode v : predecessors.get(w)) {
                    double contribution = (double) sigma.get(v) / sigma.get(w) * (1.0 + delta.get(w));
                    delta.put(v, delta.get(v) + contribution);
                }
                if (!w.equals(s)) {
                    centrality.put(w, centrality.get(w) + delta.get(w));
                }
            }
        }

        // For undirected graph, divide by 2
        for (UserNode u : users) {
            centrality.put(u, centrality.get(u) / 2.0);
        }

        // Normalize to [0, 1]
        double maxVal = centrality.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        if (maxVal > 0) {
            for (UserNode u : users) {
                centrality.put(u, centrality.get(u) / maxVal);
            }
        }

        return centrality;
    }

    /**
     * BFS distances from a source node.
     */
    private static Map<UserNode, Integer> bfsDistances(
            UserNode source,
            Map<UserNode, List<UserNode>> adjacencyList) {

        Map<UserNode, Integer> distances = new HashMap<>();
        Queue<UserNode> queue = new LinkedList<>();
        distances.put(source, 0);
        queue.add(source);

        while (!queue.isEmpty()) {
            UserNode current = queue.poll();
            int currentDist = distances.get(current);
            for (UserNode neighbor : adjacencyList.getOrDefault(current, Collections.emptyList())) {
                if (!distances.containsKey(neighbor)) {
                    distances.put(neighbor, currentDist + 1);
                    queue.add(neighbor);
                }
            }
        }
        return distances;
    }
}

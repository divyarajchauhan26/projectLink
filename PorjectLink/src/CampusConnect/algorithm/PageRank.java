package CampusConnect.algorithm;

import CampusConnect.domain.UserNode;

import java.util.*;

/**
 * Google's PageRank algorithm adapted for social graphs.
 * Ranks users by their influence/importance in the network.
 */
public class PageRank {

    private static final double DEFAULT_DAMPING = 0.85;
    private static final int DEFAULT_ITERATIONS = 100;
    private static final double EPSILON = 1e-6;

    /**
     * Compute PageRank for all nodes in the graph.
     *
     * @param users         all users in the graph
     * @param adjacencyList the graph adjacency list
     * @param damping       damping factor (typically 0.85)
     * @param maxIterations maximum number of iterations
     * @return map of UserNode → rank score (sums to 1.0)
     */
    public static Map<UserNode, Double> compute(
            List<UserNode> users,
            Map<UserNode, List<UserNode>> adjacencyList,
            double damping,
            int maxIterations) {

        int n = users.size();
        if (n == 0) return Collections.emptyMap();

        // Initialize ranks uniformly
        Map<UserNode, Double> ranks = new HashMap<>();
        double initialRank = 1.0 / n;
        for (UserNode u : users) {
            ranks.put(u, initialRank);
        }

        // Iterative computation
        for (int iter = 0; iter < maxIterations; iter++) {
            Map<UserNode, Double> newRanks = new HashMap<>();
            double danglingSum = 0.0;

            // Calculate dangling node contribution (nodes with no outgoing edges)
            for (UserNode u : users) {
                List<UserNode> neighbors = adjacencyList.getOrDefault(u, Collections.emptyList());
                if (neighbors.isEmpty()) {
                    danglingSum += ranks.get(u);
                }
            }

            double maxDiff = 0.0;
            for (UserNode u : users) {
                double incomingRank = 0.0;

                // Sum rank contributions from all neighbors pointing to u
                // (In undirected graph, all neighbors "point" to u)
                List<UserNode> neighbors = adjacencyList.getOrDefault(u, Collections.emptyList());
                for (UserNode neighbor : neighbors) {
                    int neighborDegree = adjacencyList.getOrDefault(neighbor, Collections.emptyList()).size();
                    if (neighborDegree > 0) {
                        incomingRank += ranks.get(neighbor) / neighborDegree;
                    }
                }

                // PageRank formula: (1-d)/N + d * (incoming + dangling/N)
                double newRank = (1.0 - damping) / n
                        + damping * (incomingRank + danglingSum / n);
                newRanks.put(u, newRank);

                maxDiff = Math.max(maxDiff, Math.abs(newRank - ranks.get(u)));
            }

            ranks = newRanks;

            // Convergence check
            if (maxDiff < EPSILON) break;
        }

        // Normalize to [0, 1] range for visualization
        double maxRank = ranks.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        double minRank = ranks.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double range = maxRank - minRank;

        Map<UserNode, Double> normalized = new HashMap<>();
        for (UserNode u : users) {
            double norm = range > 0 ? (ranks.get(u) - minRank) / range : 0.5;
            normalized.put(u, norm);
            u.getMetrics().setPageRank(norm); // Store on node for heatmap
        }

        return normalized;
    }

    /**
     * Compute PageRank with default parameters.
     */
    public static Map<UserNode, Double> compute(
            List<UserNode> users,
            Map<UserNode, List<UserNode>> adjacencyList) {
        return compute(users, adjacencyList, DEFAULT_DAMPING, DEFAULT_ITERATIONS);
    }
}

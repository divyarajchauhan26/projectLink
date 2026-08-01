package CampusConnect.algorithm;

import CampusConnect.domain.UserNode;

import java.util.*;

/**
 * Louvain community detection algorithm.
 * Detects densely connected groups (communities) in the network
 * by optimizing modularity.
 */
public class CommunityDetection {

    /**
     * Run Louvain community detection.
     *
     * @return map of communityId → list of users in that community
     */
    public static Map<Integer, List<UserNode>> detectCommunities(
            List<UserNode> users,
            Map<UserNode, List<UserNode>> adjacencyList) {

        if (users.isEmpty()) return Collections.emptyMap();

        // Total edges (each undirected edge counted once)
        int totalEdges = 0;
        for (UserNode u : users) {
            totalEdges += adjacencyList.getOrDefault(u, Collections.emptyList()).size();
        }
        totalEdges /= 2; // undirected
        double m = totalEdges;

        if (m == 0) {
            // No edges: each node is its own community
            Map<Integer, List<UserNode>> result = new HashMap<>();
            for (int i = 0; i < users.size(); i++) {
                users.get(i).getMetrics().setCommunityId(i);
                List<UserNode> list = new ArrayList<>();
                list.add(users.get(i));
                result.put(i, list);
            }
            return result;
        }

        // Degree map
        Map<UserNode, Integer> degree = new HashMap<>();
        for (UserNode u : users) {
            degree.put(u, adjacencyList.getOrDefault(u, Collections.emptyList()).size());
        }

        // Initialize: each node in its own community
        Map<UserNode, Integer> community = new HashMap<>();
        for (int i = 0; i < users.size(); i++) {
            community.put(users.get(i), i);
        }

        boolean improved = true;
        int maxPasses = 50;
        int pass = 0;

        while (improved && pass < maxPasses) {
            improved = false;
            pass++;

            // Shuffle order for better convergence
            List<UserNode> shuffled = new ArrayList<>(users);
            Collections.shuffle(shuffled);

            for (UserNode node : shuffled) {
                int currentComm = community.get(node);
                int ki = degree.get(node);

                // Calculate modularity gain for moving to each neighbor's community
                // Collect neighbor communities
                Map<Integer, Double> neighborCommEdges = new HashMap<>();
                for (UserNode neighbor : adjacencyList.getOrDefault(node, Collections.emptyList())) {
                    int nComm = community.get(neighbor);
                    neighborCommEdges.merge(nComm, 1.0, Double::sum);
                }

                // Calculate sum of degrees in each community
                Map<Integer, Double> commDegreeSum = new HashMap<>();
                for (UserNode u : users) {
                    int c = community.get(u);
                    commDegreeSum.merge(c, (double) degree.get(u), Double::sum);
                }

                // Remove node from its current community for calculation
                double sumCurrentComm = commDegreeSum.getOrDefault(currentComm, 0.0) - ki;
                double edgesToCurrent = neighborCommEdges.getOrDefault(currentComm, 0.0);

                double bestGain = 0.0;
                int bestComm = currentComm;

                for (Map.Entry<Integer, Double> entry : neighborCommEdges.entrySet()) {
                    int targetComm = entry.getKey();
                    if (targetComm == currentComm) continue;

                    double edgesToTarget = entry.getValue();
                    double sumTargetComm = commDegreeSum.getOrDefault(targetComm, 0.0);

                    // Modularity gain formula
                    double gain = (edgesToTarget - edgesToCurrent) / m
                            - ki * (sumTargetComm - sumCurrentComm) / (2.0 * m * m);

                    if (gain > bestGain) {
                        bestGain = gain;
                        bestComm = targetComm;
                    }
                }

                if (bestComm != currentComm) {
                    community.put(node, bestComm);
                    improved = true;
                }
            }
        }

        // Compact community IDs (rename to 0, 1, 2, ...)
        Map<Integer, Integer> remapping = new HashMap<>();
        int nextId = 0;
        for (UserNode u : users) {
            int oldId = community.get(u);
            if (!remapping.containsKey(oldId)) {
                remapping.put(oldId, nextId++);
            }
        }

        // Assign to nodes and build result
        Map<Integer, List<UserNode>> result = new HashMap<>();
        for (UserNode u : users) {
            int newId = remapping.get(community.get(u));
            u.getMetrics().setCommunityId(newId);
            result.computeIfAbsent(newId, k -> new ArrayList<>()).add(u);
        }

        return result;
    }

    /**
     * Calculate graph modularity for the current community assignment.
     */
    public static double calculateModularity(
            List<UserNode> users,
            Map<UserNode, List<UserNode>> adjacencyList) {

        int totalEdges = 0;
        for (UserNode u : users) {
            totalEdges += adjacencyList.getOrDefault(u, Collections.emptyList()).size();
        }
        double m = totalEdges / 2.0;
        if (m == 0) return 0.0;

        double q = 0.0;
        for (UserNode u : users) {
            for (UserNode v : users) {
                if (u.getMetrics().getCommunityId() != v.getMetrics().getCommunityId()) continue;

                int connected = adjacencyList.getOrDefault(u, Collections.emptyList()).contains(v) ? 1 : 0;
                int ku = adjacencyList.getOrDefault(u, Collections.emptyList()).size();
                int kv = adjacencyList.getOrDefault(v, Collections.emptyList()).size();

                q += connected - (double)(ku * kv) / (2.0 * m);
            }
        }
        return q / (2.0 * m);
    }
}

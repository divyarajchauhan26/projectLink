package CampusConnect.algorithm;

import CampusConnect.domain.UserNode;

import java.util.*;

/**
 * Friend recommendation engine using multiple similarity metrics:
 * - Common Neighbors
 * - Jaccard Coefficient
 * - Adamic-Adar Index
 */
public class FriendRecommender {

    /**
     * A recommendation result: a suggested user with a score.
     */
    public static class Recommendation {
        public final UserNode user;
        public final double score;
        public final int mutualFriends;
        public final String reason;

        public Recommendation(UserNode user, double score, int mutualFriends, String reason) {
            this.user = user;
            this.score = score;
            this.mutualFriends = mutualFriends;
            this.reason = reason;
        }
    }

    /**
     * Get friend recommendations for a user, sorted by score (highest first).
     * Combines Jaccard + Adamic-Adar for a composite score.
     */
    public static List<Recommendation> recommend(
            UserNode target,
            List<UserNode> allUsers,
            Map<UserNode, List<UserNode>> adjacencyList,
            int maxResults) {

        Set<UserNode> targetFriends = new HashSet<>(
                adjacencyList.getOrDefault(target, Collections.emptyList()));

        List<Recommendation> recommendations = new ArrayList<>();

        for (UserNode candidate : allUsers) {
            // Skip self and existing friends
            if (candidate.equals(target) || targetFriends.contains(candidate)) continue;

            Set<UserNode> candidateFriends = new HashSet<>(
                    adjacencyList.getOrDefault(candidate, Collections.emptyList()));

            // Common neighbors
            Set<UserNode> common = new HashSet<>(targetFriends);
            common.retainAll(candidateFriends);
            int mutualCount = common.size();

            if (mutualCount == 0) continue; // No mutual friends, skip

            // Jaccard coefficient: |A ∩ B| / |A ∪ B|
            Set<UserNode> union = new HashSet<>(targetFriends);
            union.addAll(candidateFriends);
            double jaccard = union.isEmpty() ? 0.0 : (double) mutualCount / union.size();

            // Adamic-Adar index: Σ 1/log(degree(z)) for each common neighbor z
            double adamicAdar = 0.0;
            for (UserNode z : common) {
                int zDegree = adjacencyList.getOrDefault(z, Collections.emptyList()).size();
                if (zDegree > 1) {
                    adamicAdar += 1.0 / Math.log(zDegree);
                }
            }

            // Composite score: weighted combination
            double score = 0.4 * jaccard + 0.6 * (adamicAdar / Math.max(1, allUsers.size()));

            String reason = mutualCount + " mutual friend" + (mutualCount > 1 ? "s" : "")
                    + " (Jaccard: " + String.format("%.2f", jaccard) + ")";

            recommendations.add(new Recommendation(candidate, score, mutualCount, reason));
        }

        // Sort by score descending
        recommendations.sort((a, b) -> Double.compare(b.score, a.score));

        // Return top N
        return recommendations.subList(0, Math.min(maxResults, recommendations.size()));
    }

    /**
     * Find mutual friends between two users.
     */
    public static List<UserNode> findMutualFriends(
            UserNode u1, UserNode u2,
            Map<UserNode, List<UserNode>> adjacencyList) {

        Set<UserNode> friends1 = new HashSet<>(
                adjacencyList.getOrDefault(u1, Collections.emptyList()));
        Set<UserNode> friends2 = new HashSet<>(
                adjacencyList.getOrDefault(u2, Collections.emptyList()));

        friends1.retainAll(friends2);
        return new ArrayList<>(friends1);
    }

    /**
     * Compute Jaccard similarity between two users.
     */
    public static double jaccardSimilarity(
            UserNode u1, UserNode u2,
            Map<UserNode, List<UserNode>> adjacencyList) {

        Set<UserNode> friends1 = new HashSet<>(
                adjacencyList.getOrDefault(u1, Collections.emptyList()));
        Set<UserNode> friends2 = new HashSet<>(
                adjacencyList.getOrDefault(u2, Collections.emptyList()));

        Set<UserNode> intersection = new HashSet<>(friends1);
        intersection.retainAll(friends2);

        Set<UserNode> union = new HashSet<>(friends1);
        union.addAll(friends2);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
}

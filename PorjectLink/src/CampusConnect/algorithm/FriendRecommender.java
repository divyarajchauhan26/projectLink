package CampusConnect.algorithm;

import CampusConnect.domain.Person;

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
        public final Person user;
        public final double score;
        public final int mutualFriends;
        public final String reason;

        public Recommendation(Person user, double score, int mutualFriends, String reason) {
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
            Person target,
            List<Person> allUsers,
            Map<Person, List<Person>> adjacencyList,
            int maxResults) {

        Set<Person> targetFriends = new HashSet<>(
                adjacencyList.getOrDefault(target, Collections.emptyList()));

        List<Recommendation> recommendations = new ArrayList<>();

        for (Person candidate : allUsers) {
            // Skip self and existing friends
            if (candidate.equals(target) || targetFriends.contains(candidate)) continue;

            Set<Person> candidateFriends = new HashSet<>(
                    adjacencyList.getOrDefault(candidate, Collections.emptyList()));

            // Common neighbors
            Set<Person> common = new HashSet<>(targetFriends);
            common.retainAll(candidateFriends);
            int mutualCount = common.size();

            if (mutualCount == 0) continue; // No mutual friends, skip

            // Jaccard coefficient: |A ∩ B| / |A ∪ B|
            Set<Person> union = new HashSet<>(targetFriends);
            union.addAll(candidateFriends);
            double jaccard = union.isEmpty() ? 0.0 : (double) mutualCount / union.size();

            // Adamic-Adar index: Σ 1/log(degree(z)) for each common neighbor z
            double adamicAdar = 0.0;
            for (Person z : common) {
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
    public static List<Person> findMutualFriends(
            Person u1, Person u2,
            Map<Person, List<Person>> adjacencyList) {

        Set<Person> friends1 = new HashSet<>(
                adjacencyList.getOrDefault(u1, Collections.emptyList()));
        Set<Person> friends2 = new HashSet<>(
                adjacencyList.getOrDefault(u2, Collections.emptyList()));

        friends1.retainAll(friends2);
        return new ArrayList<>(friends1);
    }

    /**
     * Adamic-Adar index for a single pair: Σ 1/log(degree(z)) over common neighbours z.
     * <p>
     * The intuition is that a mutual friend who knows everybody is weak evidence, while a
     * mutual friend who knows six people is strong evidence — the same rarity argument
     * that IDF makes about interests. Extracted so the V2 recommender can use it as its
     * structural term instead of reimplementing it.
     */
    public static double adamicAdar(Person a, Person b, Map<Person, List<Person>> adjacencyList) {
        double score = 0.0;
        for (Person z : commonNeighbours(a, b, adjacencyList)) {
            int degree = adjacencyList.getOrDefault(z, Collections.emptyList()).size();
            if (degree > 1) score += 1.0 / Math.log(degree);
        }
        return score;
    }

    /** Friends that both people share. */
    public static List<Person> commonNeighbours(Person a, Person b, Map<Person, List<Person>> adjacencyList) {
        Set<Person> shared = new HashSet<>(adjacencyList.getOrDefault(a, Collections.emptyList()));
        shared.retainAll(new HashSet<>(adjacencyList.getOrDefault(b, Collections.emptyList())));
        return new ArrayList<>(shared);
    }

    /**
     * Compute Jaccard similarity between two users.
     */
    public static double jaccardSimilarity(
            Person u1, Person u2,
            Map<Person, List<Person>> adjacencyList) {

        Set<Person> friends1 = new HashSet<>(
                adjacencyList.getOrDefault(u1, Collections.emptyList()));
        Set<Person> friends2 = new HashSet<>(
                adjacencyList.getOrDefault(u2, Collections.emptyList()));

        Set<Person> intersection = new HashSet<>(friends1);
        intersection.retainAll(friends2);

        Set<Person> union = new HashSet<>(friends1);
        union.addAll(friends2);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
}

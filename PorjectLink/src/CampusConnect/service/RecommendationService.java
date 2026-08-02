package CampusConnect.service;

import CampusConnect.algorithm.FriendRecommender;
import CampusConnect.algorithm.similarity.SimilarityEngine;
import CampusConnect.domain.Person;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Who should this person meet, and why.
 * <p>
 * A recommendation is <em>high affinity, no edge yet</em>. Affinity blends what two
 * profiles say (interests, bio, circumstance, intent, teach/learn) with what the friend
 * graph says (shared friends, weighted by Adamic-Adar), minus a penalty for people who
 * are already connected to everybody.
 * <p>
 * Three parts of this are load-bearing and easy to leave out:
 * <ul>
 *   <li><b>Cold-start reweighting.</b> A first-year with no friends has zero structural
 *       signal. Left alone, the structural term contributes nothing but still consumes
 *       its share of the weight, so every candidate scores low and the ranking turns to
 *       noise. Its weight is redistributed across the profile terms instead.</li>
 *   <li><b>Popularity penalty.</b> The best-connected person shares mutual friends with
 *       nearly everyone, so without a penalty they top every single list and the product
 *       feels broken.</li>
 *   <li><b>Diversity.</b> Five suggestions from one friend group is one suggestion shown
 *       five times.</li>
 * </ul>
 */
public final class RecommendationService {

    /**
     * Blend weights. Kept as data rather than constants so they can be tuned against the
     * harness, and so a learned model can drop straight in later (roadmap 5.3).
     */
    public record Weights(
            double interest,
            double bio,
            double context,
            double structural,
            double intent,
            double teachLearn,
            double popularityPenalty,
            double isolationBoost) {

        public static Weights defaults() {
            return new Weights(0.40, 0.12, 0.12, 0.22, 0.07, 0.07, 0.08, 0.05);
        }

        /** The profile-only terms, which is everything except the graph. */
        double contentTotal() { return interest + bio + context + intent + teachLearn; }
    }

    /** The individual signals behind one suggestion, kept for debugging and explanation. */
    public record Signals(
            double interest,
            double bio,
            double context,
            double structural,
            double intent,
            double teachLearn,
            double popularity) {}

    public record Suggestion(
            Person person,
            double score,
            Signals signals,
            List<Person> mutualFriends,
            String explanation) {}

    /** Below this many friends, the graph has nothing useful to say about you. */
    public static final int COLD_START_DEGREE = 3;

    private final NetworkService service;
    private final SimilarityEngine similarity;
    private final Weights weights;
    private final ExplanationBuilder explanations;
    private final int maxDegree;
    private final double averageDegree;

    public RecommendationService(NetworkService service) {
        this(service, Weights.defaults());
    }

    public RecommendationService(NetworkService service, Weights weights) {
        this.service = service;
        this.weights = weights;
        this.similarity = SimilarityEngine.build(service.getAllUsers());
        this.explanations = new ExplanationBuilder(similarity, weights);

        int max = 1;
        for (Person p : service.getAllUsers()) {
            max = Math.max(max, service.getConnections(p).size());
        }
        this.maxDegree = max;
        this.averageDegree = service.getAverageDegree();
    }

    /**
     * A nudge toward students the network is failing.
     * <p>
     * Every other term here asks "what does this user want". This one asks the opposite:
     * who is nobody finding. A recommender that only optimises for the person reading it
     * will quietly leave the isolated exactly where they are, because having no
     * connections is precisely what makes them score badly on the structural term. The
     * weight is small on purpose — enough to break ties toward someone who needs the
     * introduction, not enough to recommend a bad match out of pity.
     */
    private double isolationScore(Person candidate) {
        int degree = service.getConnections(candidate).size();
        if (degree == 0) return 1.0;
        if (degree < COLD_START_DEGREE) return 0.5;
        return 0.0;
    }

    /**
     * How much to damp a candidate for being over-connected, in [0,1].
     * <p>
     * Only <em>above-average</em> popularity is penalised, ramping to 1 at the most
     * connected person. A flat {@code degree/maxDegree} looks reasonable and is not: it
     * charges every ordinary person a fee for having friends, and hits the single most
     * connected person so hard they vanish from every list entirely — which is just the
     * original bias inverted, with isolated people dominating instead of hubs.
     */
    private double popularityPenalty(Person candidate) {
        double degree = service.getConnections(candidate).size();
        double headroom = Math.max(1.0, maxDegree - averageDegree);
        return Math.max(0.0, Math.min(1.0, (degree - averageDegree) / headroom));
    }

    public SimilarityEngine similarityEngine() { return similarity; }

    /** True when the graph cannot say anything useful about this person yet. */
    public boolean isColdStart(Person p) {
        return service.getConnections(p).size() < COLD_START_DEGREE;
    }

    // ================= the main entry point =================

    public List<Suggestion> recommend(Person target, int limit) {
        return recommend(target, limit, 0.0, Set.of());
    }

    /**
     * @param serendipity 0 = closest matches, 1 = people you would otherwise never meet.
     *                    See {@link #applySerendipity}.
     * @param excludeIds  person ids to skip, typically recently dismissed
     */
    public List<Suggestion> recommend(Person target, int limit,
                                      double serendipity, Set<String> excludeIds) {
        if (target == null) return List.of();

        Map<Person, List<Person>> adjacency = service.getAdjacencyList();
        Set<Person> alreadyFriends = new HashSet<>(service.getConnections(target));
        Weights w = isColdStart(target) ? redistributeForColdStart(weights) : weights;
        double s = Math.max(0.0, Math.min(1.0, serendipity));

        List<Suggestion> scored = new ArrayList<>();
        for (Person candidate : service.getAllUsers()) {
            if (candidate == target || alreadyFriends.contains(candidate)) continue;
            if (excludeIds != null && excludeIds.contains(candidate.getId())) continue;

            Scored raw = score(target, candidate, w);
            double finalScore = applySerendipity(raw, s);
            if (finalScore <= 0.01) continue; // nothing meaningful in common

            List<Person> mutual = FriendRecommender.commonNeighbours(target, candidate, adjacency);
            String why = explanations.build(target, candidate, raw.signals(), mutual);
            scored.add(new Suggestion(candidate, finalScore, raw.signals(), mutual, why));
        }

        scored.sort((a, b) -> Double.compare(b.score(), a.score()));
        return diversify(scored, limit);
    }

    /**
     * Trade closeness for reach.
     * <p>
     * The structural term is what keeps suggestions inside the circle you already move in:
     * shared friends score highly, so the safe pick is always a friend-of-a-friend. Turning
     * serendipity up damps that term and pays a bonus to candidates with <em>no</em>
     * overlap, which surfaces the person across campus who shares a rare interest and
     * knows nobody you know.
     * <p>
     * This is the explore/exploit dial stated plainly, and it is also where a contextual
     * bandit plugs in later — the axis is already defined and the choice already logged.
     */
    private static double applySerendipity(Scored raw, double serendipity) {
        if (serendipity <= 0) return raw.score();
        double structural = raw.signals().structural();
        double explorationBonus = serendipity * 0.25 * (1.0 - structural);
        double damped = raw.score() - serendipity * 0.5 * structural * 0.22;
        return damped + explorationBonus;
    }

    /** One scored pair, before it becomes a user-facing Suggestion. */
    private record Scored(double score, Signals signals) {}

    /**
     * The affinity function itself. Shared by {@link #recommend} and
     * {@link #affinityTo} so the heatmap can never disagree with the ranked list —
     * two implementations of the same formula would drift apart within a milestone.
     */
    private Scored score(Person target, Person candidate, Weights w) {
        Map<Person, List<Person>> adjacency = service.getAdjacencyList();

        double interest = similarity.interestSim(target, candidate);
        double bio = similarity.bioSim(target, candidate);
        double context = similarity.contextSim(target, candidate);
        double intent = similarity.intentMatch(target, candidate);
        double teachLearn = similarity.teachLearnMatch(target, candidate);

        // Adamic-Adar is unbounded, so squash it rather than normalising against the
        // best candidate — that would inflate a weak top match to a perfect score.
        double adamicAdar = FriendRecommender.adamicAdar(target, candidate, adjacency);
        double structural = adamicAdar / (adamicAdar + 1.5);

        double popularity = popularityPenalty(candidate);
        double isolation = isolationScore(candidate);

        double total = w.interest() * interest
                + w.bio() * bio
                + w.context() * context
                + w.structural() * structural
                + w.intent() * intent
                + w.teachLearn() * teachLearn
                + w.isolationBoost() * isolation
                - w.popularityPenalty() * popularity;

        return new Scored(total,
                new Signals(interest, bio, context, structural, intent, teachLearn, popularity));
    }

    /**
     * Affinity from one person to everybody else, rescaled to [0,1] for display.
     * <p>
     * Unlike {@link #recommend}, existing friends are <em>included</em> — this drives the
     * "similarity to me" heatmap, where the honest picture is the whole campus shaded by
     * how well each person fits you, not just the people you have yet to meet.
     * <p>
     * Rescaling is by the observed range rather than absolute, because raw affinity values
     * cluster in a narrow band (roughly 0.05–0.45) and would render as one flat colour.
     *
     * @return every other person mapped to [0,1]; the target itself is omitted
     */
    public Map<Person, Double> affinityTo(Person target) {
        Map<Person, Double> out = new LinkedHashMap<>();
        if (target == null) return out;

        Weights w = isColdStart(target) ? redistributeForColdStart(weights) : weights;

        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (Person other : service.getAllUsers()) {
            if (other == target) continue;
            double s = score(target, other, w).score();
            out.put(other, s);
            min = Math.min(min, s);
            max = Math.max(max, s);
        }

        double range = max - min;
        if (range <= 1e-9) {
            out.replaceAll((p, v) -> 0.5); // everyone equally alike — a flat map is honest
        } else {
            final double lo = min, r = range;
            out.replaceAll((p, v) -> (v - lo) / r);
        }
        return out;
    }

    // ================= cold start =================

    /**
     * Push the structural weight onto the profile terms, in proportion to what they
     * already carry, so the weights still sum to the same total.
     */
    private static Weights redistributeForColdStart(Weights w) {
        double content = w.contentTotal();
        if (content <= 0) return w;
        double boost = 1.0 + w.structural() / content;
        return new Weights(
                w.interest() * boost,
                w.bio() * boost,
                w.context() * boost,
                0.0,
                w.intent() * boost,
                w.teachLearn() * boost,
                w.popularityPenalty(),
                w.isolationBoost());
    }

    // ================= diversity =================

    /**
     * Avoid handing back five people who all know each other. A candidate already
     * connected to two chosen suggestions is held back and only used if we would
     * otherwise run short.
     */
    private List<Suggestion> diversify(List<Suggestion> ranked, int limit) {
        List<Suggestion> picked = new ArrayList<>();
        List<Suggestion> deferred = new ArrayList<>();

        for (Suggestion s : ranked) {
            if (picked.size() >= limit) break;
            int tiesToPicked = 0;
            for (Suggestion already : picked) {
                if (service.getConnections(s.person()).contains(already.person())) tiesToPicked++;
            }
            if (tiesToPicked >= 2) deferred.add(s);
            else picked.add(s);
        }

        for (Suggestion s : deferred) {
            if (picked.size() >= limit) break;
            picked.add(s);
        }
        return Collections.unmodifiableList(picked);
    }
}

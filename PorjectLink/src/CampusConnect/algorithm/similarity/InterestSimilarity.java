package CampusConnect.algorithm.similarity;

import CampusConnect.domain.InterestTag;
import CampusConnect.domain.Person;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How alike two people's interests are, weighted so that <em>rare</em> shared interests
 * count for more than common ones.
 * <p>
 * <b>Why plain overlap is not good enough.</b> Half of campus likes music, so "you both
 * like music" tells you almost nothing. Two people who both like Carnatic fusion, or both
 * do competitive programming, have told you something real. An unweighted Jaccard treats
 * those two facts identically and buries the signal — in the seeded campus it ranks three
 * people who merely also code alongside someone who shares two rare tags.
 * <p>
 * So each tag is weighted by inverse document frequency, exactly as in text search:
 * <pre>
 *   idf(t) = log( N / (1 + peopleWithTag(t)) )
 * </pre>
 * and similarity is the weighted (Ruzicka) Jaccard over those weights, with each person's
 * 1–5 intensity folded in so that "plays every day" outranks "tried it once":
 * <pre>
 *   sim(u,v) = Σ_t idf(t)·min(iu,iv)  /  Σ_t idf(t)·max(iu,iv)
 * </pre>
 * The result is in [0,1], and is 1 only for identical interests at identical intensities.
 */
public final class InterestSimilarity {

    private final Map<InterestTag, Double> idf;
    private final int population;

    private InterestSimilarity(Map<InterestTag, Double> idf, int population) {
        this.idf = idf;
        this.population = population;
    }

    /**
     * Measure tag rarity across the whole population. Must be rebuilt when people join
     * or edit their interests, since every weight depends on the corpus.
     */
    public static InterestSimilarity build(Collection<Person> people) {
        int n = Math.max(1, people.size());
        Map<InterestTag, Integer> docFreq = new HashMap<>();
        for (Person p : people) {
            for (InterestTag t : p.getInterests()) {
                docFreq.merge(t, 1, Integer::sum);
            }
        }
        Map<InterestTag, Double> idf = new HashMap<>();
        for (Map.Entry<InterestTag, Integer> e : docFreq.entrySet()) {
            idf.put(e.getKey(), Math.log((double) n / (1 + e.getValue())));
        }
        return new InterestSimilarity(idf, n);
    }

    /**
     * Rarity weight for a tag. Never returns zero or negative: a tag held by nearly
     * everyone still carries a little information, and a negative weight would let a
     * shared interest actively reduce similarity, which is nonsense.
     */
    public double idf(InterestTag tag) {
        double raw = idf.getOrDefault(tag, Math.log(population));
        return Math.max(0.05, raw);
    }

    /** Weighted Jaccard over interests, in [0,1]. */
    public double similarity(Person a, Person b) {
        Set<InterestTag> union = new LinkedHashSet<>(a.getInterests());
        union.addAll(b.getInterests());
        if (union.isEmpty()) return 0.0;

        double numerator = 0.0, denominator = 0.0;
        for (InterestTag t : union) {
            double w = idf(t);
            // Absent tags score 0, so min() is 0 unless both hold the tag.
            int ia = a.getIntensity(t);
            int ib = b.getIntensity(t);
            numerator += w * Math.min(ia, ib);
            denominator += w * Math.max(ia, ib);
        }
        return denominator == 0 ? 0.0 : numerator / denominator;
    }

    /**
     * Shared interests, rarest first. This is what the explanation sentence should lead
     * with — "you both do competitive programming" lands harder than "you both like music",
     * even when both are true.
     */
    public List<InterestTag> sharedByImportance(Person a, Person b, int limit) {
        List<InterestTag> shared = new ArrayList<>();
        for (InterestTag t : a.getInterests()) {
            if (b.hasInterest(t)) shared.add(t);
        }
        shared.sort((x, y) -> Double.compare(idf(y), idf(x)));
        return shared.size() <= limit ? shared : shared.subList(0, limit);
    }
}

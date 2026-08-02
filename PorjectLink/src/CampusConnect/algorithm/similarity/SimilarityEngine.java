package CampusConnect.algorithm.similarity;

import CampusConnect.domain.Intent;
import CampusConnect.domain.InterestTag;
import CampusConnect.domain.Person;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Every way two people can resemble each other, apart from the friend graph itself.
 * <p>
 * Built once per population — the interest and bio models both depend on corpus-wide
 * frequencies, so this must be rebuilt when people join or edit their profiles.
 * Structural similarity deliberately lives elsewhere ({@code RecommendationService}),
 * because it is a property of the graph rather than of the two profiles.
 */
public final class SimilarityEngine {

    private final InterestSimilarity interests;
    private final TfIdf bios;
    /** Rarity of each spoken language, so sharing English counts for far less than Konkani. */
    private final Map<String, Double> languageIdf;

    private SimilarityEngine(InterestSimilarity interests, TfIdf bios, Map<String, Double> languageIdf) {
        this.interests = interests;
        this.bios = bios;
        this.languageIdf = languageIdf;
    }

    public static SimilarityEngine build(Collection<Person> people) {
        int n = Math.max(1, people.size());
        Map<String, Integer> langFreq = new HashMap<>();
        for (Person p : people) {
            for (String l : p.getLanguages()) langFreq.merge(norm(l), 1, Integer::sum);
        }
        Map<String, Double> langIdf = new HashMap<>();
        for (Map.Entry<String, Integer> e : langFreq.entrySet()) {
            langIdf.put(e.getKey(), Math.max(0.05, Math.log((double) n / (1 + e.getValue()))));
        }
        return new SimilarityEngine(
                InterestSimilarity.build(people), TfIdf.build(people), langIdf);
    }

    public InterestSimilarity interestModel() { return interests; }
    public TfIdf bioModel() { return bios; }

    // ================= the individual signals =================

    public double interestSim(Person a, Person b) { return interests.similarity(a, b); }

    public double bioSim(Person a, Person b) { return bios.similarity(a, b); }

    /**
     * Shared circumstance: course, year, hostel, hometown, language.
     * <p>
     * These matter more than they look. A first-year far from home who finds someone from
     * the same town speaking the same language has found something the interest tags
     * cannot express.
     */
    public double contextSim(Person a, Person b) {
        double score = 0.0;

        // Weighted by how much each fact actually narrows the field. Hundreds of people
        // share your course; almost nobody shares your home town, so that is the far
        // stronger signal even though both are one line of the profile.
        if (!a.getMajor().isBlank() && norm(a.getMajor()).equals(norm(b.getMajor()))) {
            score += 0.20;
        }
        if (a.getYear() > 0 && b.getYear() > 0) {
            int gap = Math.abs(a.getYear() - b.getYear());
            if (gap == 0) score += 0.10;
            else if (gap == 1) score += 0.05;
        }
        if (!a.getHostel().isBlank() && norm(a.getHostel()).equals(norm(b.getHostel()))) {
            score += 0.15;
        }
        if (!a.getHometown().isBlank() && norm(a.getHometown()).equals(norm(b.getHometown()))) {
            score += 0.30;
        }
        score += 0.25 * languageOverlap(a, b);

        return Math.min(1.0, score);
    }

    /** IDF-weighted Jaccard over languages, so a shared English counts for very little. */
    private double languageOverlap(Person a, Person b) {
        Set<String> ua = normSet(a.getLanguages());
        Set<String> ub = normSet(b.getLanguages());
        if (ua.isEmpty() || ub.isEmpty()) return 0.0;

        Set<String> union = new LinkedHashSet<>(ua);
        union.addAll(ub);

        double shared = 0.0, total = 0.0;
        for (String l : union) {
            double w = languageIdf.getOrDefault(l, 1.0);
            total += w;
            if (ua.contains(l) && ub.contains(l)) shared += w;
        }
        return total == 0 ? 0.0 : shared / total;
    }

    /**
     * Do these two want compatible things?
     * <p>
     * Most intents are symmetric — two people both after a study partner match. Mentorship
     * is not: MENTOR ("I want a mentor") pairs with MENTEE ("I want to guide someone"),
     * never with another MENTOR. {@link Intent#complement()} encodes that.
     */
    public double intentMatch(Person a, Person b) {
        Set<Intent> wants = a.getLookingFor();
        if (wants.isEmpty() || b.getLookingFor().isEmpty()) return 0.0;

        int matched = 0;
        for (Intent i : wants) {
            if (b.isLookingFor(i.complement())) matched++;
        }
        return (double) matched / wants.size();
    }

    /**
     * Complementary rather than similar: does one of them teach what the other wants to
     * learn? This is the signal that gives two people with nothing in common a reason to
     * meet, which pure similarity can never produce.
     *
     * @return [0,1], counting both directions
     */
    public double teachLearnMatch(Person a, Person b) {
        int matches = countTeaches(b.getCanTeach(), a.getWantsToLearn())
                + countTeaches(a.getCanTeach(), b.getWantsToLearn());
        int wanted = a.getWantsToLearn().size() + b.getWantsToLearn().size();
        if (wanted == 0) return 0.0;
        return Math.min(1.0, (double) matches / wanted);
    }

    private static int countTeaches(Set<String> canTeach, Set<String> wantsToLearn) {
        int n = 0;
        for (String want : wantsToLearn) {
            String w = norm(want);
            if (w.isEmpty()) continue;
            for (String teach : canTeach) {
                String t = norm(teach);
                // Substring either way, so "Guitar" satisfies "acoustic guitar".
                if (!t.isEmpty() && (t.contains(w) || w.contains(t))) { n++; break; }
            }
        }
        return n;
    }

    /** The specific skills {@code b} could teach {@code a} — for the explanation sentence. */
    public List<String> whatTheyCanTeach(Person a, Person b) {
        List<String> out = new ArrayList<>();
        for (String want : a.getWantsToLearn()) {
            String w = norm(want);
            for (String teach : b.getCanTeach()) {
                String t = norm(teach);
                if (!t.isEmpty() && (t.contains(w) || w.contains(t))) { out.add(teach); break; }
            }
        }
        return out;
    }

    /** Shared interests, rarest first. */
    public List<InterestTag> sharedInterests(Person a, Person b, int limit) {
        return interests.sharedByImportance(a, b, limit);
    }

    // ================= helpers =================

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> normSet(Collection<String> in) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : in) {
            String n = norm(s);
            if (!n.isEmpty()) out.add(n);
        }
        return out;
    }
}

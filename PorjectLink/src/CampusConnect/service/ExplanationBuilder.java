package CampusConnect.service;

import CampusConnect.algorithm.similarity.SimilarityEngine;
import CampusConnect.domain.Intent;
import CampusConnect.domain.InterestTag;
import CampusConnect.domain.Person;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a score into a sentence a human would accept.
 * <p>
 * <b>Never show a bare number.</b> "Priya — 0.62" asks the user to trust an algorithm they
 * cannot inspect; "you both play basketball, and you both know Rahul" gives them a reason
 * to walk over and say hello. It costs almost nothing — every fact used here was already
 * computed to produce the score — and it makes the recommender debuggable by eye: a bad
 * suggestion with a stated reason immediately shows <em>which</em> signal misfired.
 * <p>
 * Phrases are ranked by the strength of the signal that produced them, so the sentence
 * always leads with whatever actually drove the match rather than a fixed running order.
 */
final class ExplanationBuilder {

    private static final int MAX_PHRASES = 2;

    private final SimilarityEngine similarity;
    private final RecommendationService.Weights weights;

    ExplanationBuilder(SimilarityEngine similarity, RecommendationService.Weights weights) {
        this.similarity = similarity;
        this.weights = weights;
    }

    private record Phrase(String text, double strength) {}

    String build(Person target, Person candidate,
                 RecommendationService.Signals signals, List<Person> mutualFriends) {

        List<Phrase> phrases = new ArrayList<>();

        // Strength is the signal's actual contribution to the score — weight times value
        // — not the raw signal. Raw values are not comparable across signals: intent is
        // coarse (0, 0.5, 1.0) while interest similarity rarely passes 0.5, so ranking on
        // raw values leads every sentence with the intent, however weak the match.

        // --- shared interests, rarest first ---
        List<InterestTag> shared = similarity.sharedInterests(target, candidate, 2);
        if (!shared.isEmpty()) {
            phrases.add(new Phrase("you're both into " + joinLabels(shared),
                    weights.interest() * signals.interest()));
        }

        // --- mutual friends ---
        if (!mutualFriends.isEmpty()) {
            phrases.add(new Phrase(mutualFriendPhrase(mutualFriends),
                    weights.structural() * signals.structural()));
        }

        // --- complementary skills ---
        // Nudged up: "they can teach you guitar" is a concrete reason to message someone,
        // which is worth more than its arithmetic share of the score.
        List<String> canTeach = similarity.whatTheyCanTeach(target, candidate);
        if (!canTeach.isEmpty()) {
            phrases.add(new Phrase("they can teach you " + lower(canTeach.get(0)),
                    weights.teachLearn() * signals.teachLearn() + 0.02));
        }

        // --- shared intent ---
        String intent = sharedIntentPhrase(target, candidate);
        if (intent != null) {
            phrases.add(new Phrase(intent, weights.intent() * signals.intent()));
        }

        // --- circumstance ---
        String context = contextPhrase(target, candidate);
        if (context != null) {
            phrases.add(new Phrase(context, weights.context() * signals.context()));
        }

        if (phrases.isEmpty()) {
            return "Suggested because your profiles line up.";
        }

        phrases.sort((a, b) -> Double.compare(b.strength(), a.strength()));

        StringBuilder sb = new StringBuilder("Suggested because ");
        int n = Math.min(MAX_PHRASES, phrases.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(i == n - 1 ? ", and " : ", ");
            sb.append(phrases.get(i).text());
        }
        return sb.append('.').toString();
    }

    // ---------- individual phrases ----------

    private static String mutualFriendPhrase(List<Person> mutual) {
        if (mutual.size() == 1) {
            return "you both know " + mutual.get(0).getName();
        }
        if (mutual.size() == 2) {
            return "you both know " + mutual.get(0).getName() + " and " + mutual.get(1).getName();
        }
        return "you have " + mutual.size() + " friends in common";
    }

    private static String sharedIntentPhrase(Person a, Person b) {
        // Mentorship reads backwards if phrased as a shared want, so handle it separately.
        if (a.isLookingFor(Intent.MENTOR) && b.isLookingFor(Intent.MENTEE)) {
            return "they're offering to mentor someone";
        }
        if (a.isLookingFor(Intent.MENTEE) && b.isLookingFor(Intent.MENTOR)) {
            return "they're looking for a mentor";
        }
        for (Intent i : a.getLookingFor()) {
            if (i == Intent.MENTOR || i == Intent.MENTEE) continue;
            if (b.isLookingFor(i)) {
                return "you're both looking for " + i.asObject();
            }
        }
        return null;
    }

    private static String contextPhrase(Person a, Person b) {
        if (!a.getHometown().isBlank() && a.getHometown().equalsIgnoreCase(b.getHometown())) {
            return "you're both from " + a.getHometown();
        }
        if (!a.getMajor().isBlank() && a.getMajor().equalsIgnoreCase(b.getMajor())) {
            return a.getYear() == b.getYear()
                    ? "you're both in year " + a.getYear() + " " + a.getMajor()
                    : "you're both in " + a.getMajor();
        }
        if (!a.getHostel().isBlank() && a.getHostel().equalsIgnoreCase(b.getHostel())) {
            return "you're both in " + a.getHostel();
        }
        for (String lang : a.getLanguages()) {
            // English is near-universal here, so claiming it as common ground is noise.
            if (lang.equalsIgnoreCase("English")) continue;
            for (String other : b.getLanguages()) {
                if (lang.equalsIgnoreCase(other)) return "you both speak " + lang;
            }
        }
        return null;
    }

    // ---------- helpers ----------

    private static String joinLabels(List<InterestTag> tags) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) sb.append(i == tags.size() - 1 ? " and " : ", ");
            sb.append(lower(tags.get(i).label()));
        }
        return sb.toString();
    }

    /** Lowercase unless it looks like an initialism — "EDM" and "D&D" should stay shouty. */
    private static String lower(String s) {
        if (s == null || s.isEmpty()) return "";
        String letters = s.replaceAll("[^A-Za-z]", "");
        if (!letters.isEmpty() && letters.equals(letters.toUpperCase(Locale.ROOT))) return s;
        return s.toLowerCase(Locale.ROOT);
    }
}

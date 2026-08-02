package CampusConnect.algorithm.similarity;

import CampusConnect.domain.Person;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Similarity between two people's free-text bios, as TF-IDF cosine.
 * <p>
 * Interests are a controlled vocabulary, so they only ever match when two people picked
 * the literal same tag. Bios catch what the tag list misses — someone who writes "my band
 * fell apart last sem" and someone who writes "looking for people to jam with" share no
 * tags for that sentiment, but they clearly belong together.
 * <p>
 * This is deliberately the simplest thing that works: bag of words, no stemming, no
 * embeddings. It is a <em>lexical</em> match, so "loves basketball" and "into sports"
 * still score zero — that gap is what sentence embeddings fix later (roadmap 5.6). Having
 * this baseline first means we can actually tell whether embeddings earn their weight.
 */
public final class TfIdf {

    /**
     * Function words carry no topical signal but appear in nearly every bio. IDF already
     * discounts them heavily; dropping them outright just keeps the vectors small and
     * stops them dominating short texts.
     */
    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "but", "for", "with", "that", "this", "there", "here",
            "you", "your", "not", "are", "was", "were", "have", "has", "had",
            "just", "really", "also", "still", "actually", "mostly", "about",
            "who", "what", "some", "all", "one", "two", "more", "most", "much",
            "very", "from", "into", "out", "own", "can", "cant", "will", "would",
            "been", "being", "does", "doing", "did", "get", "got", "getting",
            "him", "her", "his", "she", "they", "them", "their", "its", "our",
            "when", "where", "which", "while", "than", "then", "too", "any"
    );

    private final Map<String, Double> idf;
    /** L2-normalised vectors, so cosine similarity is a plain dot product. */
    private final Map<String, Map<String, Double>> vectors = new HashMap<>();

    private TfIdf(Map<String, Double> idf) {
        this.idf = idf;
    }

    public static TfIdf build(Collection<Person> people) {
        int n = Math.max(1, people.size());

        Map<String, Integer> docFreq = new HashMap<>();
        Map<String, List<String>> tokenised = new HashMap<>();
        for (Person p : people) {
            List<String> tokens = tokenise(p.getBio());
            tokenised.put(p.getId(), tokens);
            for (String term : new HashSet<>(tokens)) {
                docFreq.merge(term, 1, Integer::sum);
            }
        }

        Map<String, Double> idf = new HashMap<>();
        for (Map.Entry<String, Integer> e : docFreq.entrySet()) {
            idf.put(e.getKey(), Math.log((double) n / (1 + e.getValue())));
        }

        TfIdf model = new TfIdf(idf);
        for (Person p : people) {
            model.vectors.put(p.getId(), model.vectorise(tokenised.get(p.getId())));
        }
        return model;
    }

    /** Cosine similarity of the two bios, in [0,1]. Zero if either bio is empty. */
    public double similarity(Person a, Person b) {
        Map<String, Double> va = vectors.get(a.getId());
        Map<String, Double> vb = vectors.get(b.getId());
        if (va == null || vb == null || va.isEmpty() || vb.isEmpty()) return 0.0;

        // Iterate the smaller vector — the dot product only needs shared terms.
        if (va.size() > vb.size()) { Map<String, Double> t = va; va = vb; vb = t; }

        double dot = 0.0;
        for (Map.Entry<String, Double> e : va.entrySet()) {
            Double other = vb.get(e.getKey());
            if (other != null) dot += e.getValue() * other;
        }
        return Math.max(0.0, Math.min(1.0, dot));
    }

    /** Terms the two bios share, heaviest first — useful for explanations and debugging. */
    public List<String> sharedTerms(Person a, Person b, int limit) {
        Map<String, Double> va = vectors.get(a.getId());
        Map<String, Double> vb = vectors.get(b.getId());
        if (va == null || vb == null) return List.of();

        List<String> shared = new ArrayList<>();
        for (String term : va.keySet()) if (vb.containsKey(term)) shared.add(term);
        shared.sort((x, y) -> Double.compare(
                va.getOrDefault(y, 0.0) * vb.getOrDefault(y, 0.0),
                va.getOrDefault(x, 0.0) * vb.getOrDefault(x, 0.0)));
        return shared.size() <= limit ? shared : shared.subList(0, limit);
    }

    // ---------- internals ----------

    private Map<String, Double> vectorise(List<String> tokens) {
        Map<String, Double> vec = new HashMap<>();
        if (tokens == null || tokens.isEmpty()) return vec;

        Map<String, Integer> termFreq = new HashMap<>();
        for (String t : tokens) termFreq.merge(t, 1, Integer::sum);

        double sumSquares = 0.0;
        for (Map.Entry<String, Integer> e : termFreq.entrySet()) {
            // Sublinear tf: a word used five times is not five times as important.
            double tf = 1.0 + Math.log(e.getValue());
            double w = tf * Math.max(0.0, idf.getOrDefault(e.getKey(), 0.0));
            if (w > 0) {
                vec.put(e.getKey(), w);
                sumSquares += w * w;
            }
        }

        if (sumSquares > 0) {
            double norm = Math.sqrt(sumSquares);
            vec.replaceAll((k, v) -> v / norm);
        }
        return vec;
    }

    static List<String> tokenise(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;

        StringBuilder word = new StringBuilder();
        for (int i = 0; i <= text.length(); i++) {
            char c = i < text.length() ? Character.toLowerCase(text.charAt(i)) : ' ';
            if (Character.isLetter(c)) {
                word.append(c);
            } else {
                if (word.length() >= 3) {
                    String w = word.toString();
                    if (!STOPWORDS.contains(w)) out.add(w);
                }
                word.setLength(0);
            }
        }
        return out;
    }

    /** Exposed for the harness, so bad tokenisation is visible rather than inferred. */
    public static List<String> debugTokens(String bio) {
        return tokenise(bio == null ? "" : bio.toLowerCase(Locale.ROOT));
    }
}

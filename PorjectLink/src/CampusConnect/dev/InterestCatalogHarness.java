package CampusConnect.dev;

import CampusConnect.domain.Category;
import CampusConnect.domain.InterestCatalog;
import CampusConnect.domain.InterestTag;

import java.util.List;

/**
 * Headless check on the interest vocabulary and its resolver.
 * <p>
 * Run this after adding or editing any tag. Constructing the catalog alone is a real
 * test — it throws on duplicate ids and on aliases claimed by two different tags,
 * which is the failure mode you would never catch by reading a 190-row table.
 *
 * <pre>java -cp out CampusConnect.dev.InterestCatalogHarness</pre>
 */
public class InterestCatalogHarness {

    private static int checks = 0, failures = 0;

    public static void main(String[] args) {
        InterestCatalog cat = InterestCatalog.getDefault();

        stats(cat);
        resolverTable(cat);
        autocomplete(cat);

        System.out.println();
        System.out.println(failures == 0
                ? "=== ALL " + checks + " CHECKS PASSED ==="
                : "=== " + failures + " of " + checks + " CHECKS FAILED ===");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---------- catalog shape ----------

    private static void stats(InterestCatalog cat) {
        int aliases = 0;
        for (InterestTag t : cat.all()) aliases += t.aliases().size();

        System.out.println("\n=== CATALOG ===");
        System.out.println("  " + cat.size() + " tags, " + aliases + " aliases");
        for (Category c : Category.values()) {
            System.out.printf("    %-14s %3d%n", c.getLabel(), cat.byCategory(c).size());
        }
    }

    // ---------- resolution ----------

    private static void resolverTable(InterestCatalog cat) {
        System.out.println("\n=== RESOLVER ===");
        System.out.printf("  %-26s %-26s %-11s%n", "INPUT", "RESOLVED TO", "VIA");
        System.out.println("  " + "-".repeat(66));

        // canonical ids
        expect(cat, "basketball", "basketball");
        expect(cat, "machine-learning", "machine-learning");

        // nicknames and abbreviations
        expect(cat, "bball", "basketball");
        expect(cat, "hoops", "basketball");
        expect(cat, "ml", "machine-learning");
        expect(cat, "pubg", "bgmi");
        expect(cat, "tt", "table-tennis");
        expect(cat, "cp", "competitive-programming");
        expect(cat, "weeb", "anime");
        expect(cat, "chai", "tea");
        expect(cat, "mun", "model-un");
        expect(cat, "leetcode", "competitive-programming");

        // case, spacing and punctuation
        expect(cat, "Basket Ball", "basketball");
        expect(cat, "BASKETBALL", "basketball");
        expect(cat, "  basket-ball  ", "basketball");
        expect(cat, "Table Tennis", "table-tennis");
        expect(cat, "Hip Hop", "hip-hop");
        expect(cat, "K-Pop", "kpop");
        expect(cat, "UI/UX Design", "ui-ux");

        // typos
        expect(cat, "basketbal", "basketball");
        expect(cat, "photograpy", "photography");
        expect(cat, "programing", "programming");
        expect(cat, "badmintonn", "badminton");
        expect(cat, "cybersecuirty", "cybersecurity");
        expect(cat, "volleyball!!", "volleyball");

        // must NOT resolve
        expectNone(cat, "asdfghjkl");
        expectNone(cat, "quantum tunnelling");
        expectNone(cat, "");
        expectNone(cat, "!!!");

        // short-input guard: 5 chars gets tolerance 1, so near-misses on longer
        // words must still be rejected rather than silently mapped
        expectNone(cat, "drums nearby");
        expectNone(cat, "xyzzy");
    }

    private static void expect(InterestCatalog cat, String input, String expectedId) {
        InterestCatalog.Resolution r = cat.resolve(input);
        String got = r.found() ? r.tag().id() : "(none)";
        boolean ok = expectedId.equals(got);
        report(input, got, r.how().name(), ok);
    }

    private static void expectNone(InterestCatalog cat, String input) {
        InterestCatalog.Resolution r = cat.resolve(input);
        boolean ok = !r.found();
        report(input.isEmpty() ? "(empty)" : input,
                r.found() ? r.tag().id() : "(none)", r.how().name(), ok);
    }

    private static void report(String input, String got, String how, boolean ok) {
        checks++;
        if (!ok) failures++;
        System.out.printf("  %-26s %-26s %-11s %s%n",
                truncate(input, 26), truncate(got, 26), how, ok ? "" : "  <-- FAIL");
    }

    private static String truncate(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }

    // ---------- autocomplete ----------

    private static void autocomplete(InterestCatalog cat) {
        System.out.println("\n=== AUTOCOMPLETE ===");
        for (String q : new String[]{"foot", "mus", "danc", "ph", "cric"}) {
            List<InterestTag> hits = cat.search(q, 5);
            StringBuilder sb = new StringBuilder();
            for (InterestTag t : hits) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(t.label());
            }
            System.out.printf("  %-8s -> %s%n", "\"" + q + "\"", sb.length() == 0 ? "(nothing)" : sb);
            checks++;
            if (hits.isEmpty()) { failures++; System.out.println("      <-- FAIL: expected matches"); }
        }
    }
}

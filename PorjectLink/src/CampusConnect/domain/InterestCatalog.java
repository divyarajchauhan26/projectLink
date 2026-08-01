package CampusConnect.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The controlled vocabulary of interests, plus the resolver that maps messy human
 * input onto it.
 * <p>
 * <b>Why this class matters more than it looks.</b> If users type interests freely and
 * we store the raw strings, then "bball", "Basket Ball" and "BASKETBALL" are three
 * different interests, two people who both play basketball look like they have nothing
 * in common, and every similarity score — and therefore every recommendation, every
 * heatmap, and every model trained later — is computed over noise. Normalising at the
 * point of entry is the single cheapest thing we can do to keep the data meaningful.
 * <p>
 * Resolution runs in four stages, most confident first:
 * <ol>
 *   <li>{@code EXACT_ID}   — the input is already a canonical id</li>
 *   <li>{@code ALIAS}      — a known nickname or alternative spelling</li>
 *   <li>{@code NORMALIZED} — matches an id or label once case, spaces and punctuation are stripped</li>
 *   <li>{@code FUZZY}      — within a small edit distance, to absorb typos</li>
 * </ol>
 * If all four miss, the caller decides whether to offer "create a new interest".
 */
public final class InterestCatalog {

    private static final InterestCatalog DEFAULT = new InterestCatalog(seedTags());

    /** The shared catalog. */
    public static InterestCatalog getDefault() { return DEFAULT; }

    // ================= resolution result =================

    public enum MatchType { EXACT_ID, ALIAS, NORMALIZED, FUZZY, NONE }

    /**
     * Outcome of a resolve attempt. {@code tag} is null when nothing matched.
     */
    public record Resolution(String input, InterestTag tag, MatchType how) {
        public boolean found() { return tag != null; }
    }

    // ================= state =================

    private final List<InterestTag> all;
    private final Map<String, InterestTag> byId;          // canonical id -> tag
    private final Map<String, InterestTag> byNormalized;  // normalized id/label -> tag
    private final Map<String, InterestTag> byAlias;       // normalized alias -> tag

    InterestCatalog(List<InterestTag> tags) {
        this.all = List.copyOf(tags);
        this.byId = new LinkedHashMap<>();
        this.byNormalized = new LinkedHashMap<>();
        this.byAlias = new LinkedHashMap<>();

        for (InterestTag t : tags) {
            if (byId.putIfAbsent(t.id(), t) != null) {
                throw new IllegalStateException("Duplicate interest id: " + t.id());
            }
            byNormalized.putIfAbsent(normalize(t.id()), t);
            byNormalized.putIfAbsent(normalize(t.label()), t);
        }

        // Aliases are checked against everything already claimed. A silently
        // overwritten alias would send an interest to the wrong tag forever, and
        // it is almost impossible to notice by eye in a 190-entry table.
        for (InterestTag t : tags) {
            for (String alias : t.aliases()) {
                String key = normalize(alias);
                if (key.isEmpty()) {
                    throw new IllegalStateException("Empty alias on tag: " + t.id());
                }
                InterestTag clashId = byNormalized.get(key);
                if (clashId != null && !clashId.equals(t)) {
                    throw new IllegalStateException(
                            "Alias '" + alias + "' on '" + t.id() + "' collides with tag '" + clashId.id() + "'");
                }
                InterestTag clashAlias = byAlias.putIfAbsent(key, t);
                if (clashAlias != null && !clashAlias.equals(t)) {
                    throw new IllegalStateException(
                            "Alias '" + alias + "' claimed by both '" + clashAlias.id() + "' and '" + t.id() + "'");
                }
            }
        }
    }

    // ================= lookup =================

    public List<InterestTag> all() { return all; }

    public int size() { return all.size(); }

    public InterestTag byId(String id) {
        return id == null ? null : byId.get(id.trim().toLowerCase(Locale.ROOT));
    }

    public List<InterestTag> byCategory(Category category) {
        List<InterestTag> out = new ArrayList<>();
        for (InterestTag t : all) if (t.category() == category) out.add(t);
        return out;
    }

    /**
     * Map free text onto a canonical tag.
     */
    public Resolution resolve(String raw) {
        if (raw == null || raw.isBlank()) return new Resolution(raw, null, MatchType.NONE);

        String trimmed = raw.trim();

        // 1. already canonical
        InterestTag exact = byId.get(trimmed.toLowerCase(Locale.ROOT));
        if (exact != null) return new Resolution(raw, exact, MatchType.EXACT_ID);

        String key = normalize(trimmed);
        if (key.isEmpty()) return new Resolution(raw, null, MatchType.NONE);

        // 2. known nickname
        InterestTag alias = byAlias.get(key);
        if (alias != null) return new Resolution(raw, alias, MatchType.ALIAS);

        // 3. same thing modulo case / spaces / punctuation
        InterestTag norm = byNormalized.get(key);
        if (norm != null) return new Resolution(raw, norm, MatchType.NORMALIZED);

        // 4. typo tolerance
        InterestTag fuzzy = fuzzyMatch(key);
        if (fuzzy != null) return new Resolution(raw, fuzzy, MatchType.FUZZY);

        return new Resolution(raw, null, MatchType.NONE);
    }

    /**
     * Autocomplete. Prefix matches rank above substring matches.
     */
    public List<InterestTag> search(String query, int limit) {
        if (query == null || query.isBlank()) return all.size() <= limit ? all : all.subList(0, limit);
        String key = normalize(query);
        if (key.isEmpty()) return Collections.emptyList();

        List<InterestTag> prefix = new ArrayList<>();
        List<InterestTag> contains = new ArrayList<>();
        for (InterestTag t : all) {
            String label = normalize(t.label());
            String id = normalize(t.id());
            if (label.startsWith(key) || id.startsWith(key)) {
                prefix.add(t);
            } else if (label.contains(key) || id.contains(key) || aliasContains(t, key)) {
                contains.add(t);
            }
        }
        prefix.addAll(contains);
        return prefix.size() <= limit ? prefix : prefix.subList(0, limit);
    }

    private boolean aliasContains(InterestTag t, String key) {
        for (String a : t.aliases()) if (normalize(a).contains(key)) return true;
        return false;
    }

    // ================= matching internals =================

    /**
     * Strip everything that is not a letter or digit, and lowercase.
     * "Table Tennis", "table-tennis" and "TableTennis" all collapse to "tabletennis".
     */
    public static String normalize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Edit-distance tolerance scaled to word length. Short words get no tolerance at
     * all: "chess" and "chest" are one edit apart and mean entirely different things,
     * so being generous on short input does more harm than good.
     */
    private static int toleranceFor(int length) {
        if (length <= 4) return 0;
        if (length <= 7) return 1;
        return 2;
    }

    private InterestTag fuzzyMatch(String key) {
        int tolerance = toleranceFor(key.length());
        if (tolerance == 0) return null;

        InterestTag best = null;
        int bestDist = Integer.MAX_VALUE;

        for (Map.Entry<String, InterestTag> e : byNormalized.entrySet()) {
            int d = boundedLevenshtein(key, e.getKey(), tolerance);
            if (d < bestDist) { bestDist = d; best = e.getValue(); }
        }
        for (Map.Entry<String, InterestTag> e : byAlias.entrySet()) {
            int d = boundedLevenshtein(key, e.getKey(), tolerance);
            if (d < bestDist) { bestDist = d; best = e.getValue(); }
        }
        return bestDist <= tolerance ? best : null;
    }

    /**
     * Levenshtein distance, abandoning early once every cell in a row exceeds the
     * tolerance. Returns {@code max + 1} to signal "further away than we care about".
     */
    static int boundedLevenshtein(String a, String b, int max) {
        if (Math.abs(a.length() - b.length()) > max) return max + 1;

        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            int rowBest = curr[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                rowBest = Math.min(rowBest, curr[j]);
            }
            if (rowBest > max) return max + 1;
            int[] swap = prev; prev = curr; curr = swap;
        }
        return prev[b.length()];
    }

    // ================= the vocabulary =================

    private static void t(List<InterestTag> out, Category c, String id, String label, String... aliases) {
        out.add(new InterestTag(id, label, c, java.util.Set.of(aliases)));
    }

    private static List<InterestTag> seedTags() {
        List<InterestTag> x = new ArrayList<>();

        // ---------- SPORTS ----------
        t(x, Category.SPORTS, "cricket", "Cricket", "gully cricket", "ipl");
        t(x, Category.SPORTS, "football", "Football", "soccer", "footy");
        t(x, Category.SPORTS, "basketball", "Basketball", "bball", "hoops");
        t(x, Category.SPORTS, "badminton", "Badminton", "shuttle", "shuttlecock");
        t(x, Category.SPORTS, "tennis", "Tennis", "lawn tennis");
        t(x, Category.SPORTS, "table-tennis", "Table Tennis", "tt", "ping pong");
        t(x, Category.SPORTS, "volleyball", "Volleyball");
        t(x, Category.SPORTS, "hockey", "Hockey", "field hockey");
        t(x, Category.SPORTS, "kabaddi", "Kabaddi");
        t(x, Category.SPORTS, "chess", "Chess");
        t(x, Category.SPORTS, "carrom", "Carrom");
        t(x, Category.SPORTS, "athletics", "Athletics", "track and field", "sprinting");
        t(x, Category.SPORTS, "boxing", "Boxing");
        t(x, Category.SPORTS, "martial-arts", "Martial Arts", "karate", "taekwondo", "judo", "mma");
        t(x, Category.SPORTS, "skating", "Skating", "skateboarding", "roller skating");
        t(x, Category.SPORTS, "frisbee", "Frisbee", "ultimate frisbee");
        t(x, Category.SPORTS, "squash", "Squash");
        t(x, Category.SPORTS, "throwball", "Throwball");
        t(x, Category.SPORTS, "snooker", "Snooker", "pool", "billiards");
        t(x, Category.SPORTS, "archery", "Archery");

        // ---------- MUSIC ----------
        t(x, Category.MUSIC, "guitar", "Guitar", "acoustic guitar", "electric guitar");
        t(x, Category.MUSIC, "piano", "Piano", "keyboard");
        t(x, Category.MUSIC, "singing", "Singing", "vocals", "karaoke");
        t(x, Category.MUSIC, "drums", "Drums", "percussion", "drumming");
        t(x, Category.MUSIC, "violin", "Violin");
        t(x, Category.MUSIC, "flute", "Flute", "bansuri");
        t(x, Category.MUSIC, "tabla", "Tabla");
        t(x, Category.MUSIC, "sitar", "Sitar");
        t(x, Category.MUSIC, "music-production", "Music Production", "beat making", "fl studio", "producing");
        t(x, Category.MUSIC, "dj", "DJing", "deejay", "djing");
        t(x, Category.MUSIC, "hip-hop", "Hip Hop", "rap", "rapping");
        t(x, Category.MUSIC, "rock-music", "Rock", "rock music");
        t(x, Category.MUSIC, "metal-music", "Metal", "heavy metal", "metal music");
        t(x, Category.MUSIC, "indie-music", "Indie", "indie music");
        t(x, Category.MUSIC, "classical-music", "Western Classical", "classical music");
        t(x, Category.MUSIC, "carnatic", "Carnatic Music", "carnatic music");
        t(x, Category.MUSIC, "hindustani", "Hindustani Classical", "hindustani classical");
        t(x, Category.MUSIC, "bollywood-music", "Bollywood Music", "hindi songs", "bollywood songs");
        t(x, Category.MUSIC, "kpop", "K-Pop", "korean pop");
        t(x, Category.MUSIC, "jazz", "Jazz");
        t(x, Category.MUSIC, "edm", "EDM", "electronic music", "techno", "house music");
        t(x, Category.MUSIC, "lofi", "Lo-fi", "lofi beats");
        t(x, Category.MUSIC, "ukulele", "Ukulele");

        // ---------- GAMING ----------
        t(x, Category.GAMING, "valorant", "Valorant", "valo");
        t(x, Category.GAMING, "bgmi", "BGMI", "pubg", "pubg mobile", "battlegrounds mobile india");
        t(x, Category.GAMING, "minecraft", "Minecraft");
        t(x, Category.GAMING, "call-of-duty", "Call of Duty", "cod", "warzone");
        t(x, Category.GAMING, "fifa", "FIFA", "ea fc", "efootball");
        t(x, Category.GAMING, "gta", "GTA", "grand theft auto");
        t(x, Category.GAMING, "league-of-legends", "League of Legends", "lol", "league");
        t(x, Category.GAMING, "dota", "Dota", "dota 2");
        t(x, Category.GAMING, "apex-legends", "Apex Legends", "apex");
        t(x, Category.GAMING, "fortnite", "Fortnite");
        t(x, Category.GAMING, "genshin-impact", "Genshin Impact", "genshin");
        t(x, Category.GAMING, "board-games", "Board Games", "catan", "monopoly");
        t(x, Category.GAMING, "dnd", "D&D", "dungeons and dragons", "tabletop rpg");
        t(x, Category.GAMING, "retro-gaming", "Retro Gaming", "retro games");
        t(x, Category.GAMING, "esports", "Esports", "competitive gaming");
        t(x, Category.GAMING, "among-us", "Among Us");
        t(x, Category.GAMING, "roblox", "Roblox");

        // ---------- ACADEMICS ----------
        t(x, Category.ACADEMICS, "mathematics", "Mathematics", "maths", "math");
        t(x, Category.ACADEMICS, "physics", "Physics");
        t(x, Category.ACADEMICS, "chemistry", "Chemistry");
        t(x, Category.ACADEMICS, "biology", "Biology", "bio");
        t(x, Category.ACADEMICS, "economics", "Economics", "econ");
        t(x, Category.ACADEMICS, "psychology", "Psychology", "psych");
        t(x, Category.ACADEMICS, "philosophy", "Philosophy");
        t(x, Category.ACADEMICS, "history", "History");
        t(x, Category.ACADEMICS, "literature", "Literature", "english literature");
        t(x, Category.ACADEMICS, "debate", "Debate", "debating");
        t(x, Category.ACADEMICS, "model-un", "Model UN", "mun", "model united nations");
        t(x, Category.ACADEMICS, "quizzing", "Quizzing", "quiz");
        t(x, Category.ACADEMICS, "research", "Research", "academic research");
        t(x, Category.ACADEMICS, "case-competitions", "Case Competitions", "case comps", "case study");
        t(x, Category.ACADEMICS, "public-speaking", "Public Speaking", "oratory", "elocution");
        t(x, Category.ACADEMICS, "astronomy", "Astronomy", "astrophysics");

        // ---------- TECH ----------
        t(x, Category.TECH, "programming", "Programming", "coding", "software development", "dev");
        t(x, Category.TECH, "web-development", "Web Development", "web dev", "frontend", "backend", "full stack");
        t(x, Category.TECH, "app-development", "App Development", "app dev", "android development", "ios development");
        t(x, Category.TECH, "machine-learning", "Machine Learning", "ml", "deep learning");
        t(x, Category.TECH, "artificial-intelligence", "Artificial Intelligence", "ai", "gen ai", "llm");
        t(x, Category.TECH, "data-science", "Data Science", "data analytics", "data analysis");
        t(x, Category.TECH, "cybersecurity", "Cybersecurity", "ethical hacking", "infosec", "hacking");
        t(x, Category.TECH, "blockchain", "Blockchain", "crypto", "web3");
        t(x, Category.TECH, "robotics", "Robotics", "robots");
        t(x, Category.TECH, "iot", "IoT", "internet of things");
        t(x, Category.TECH, "cloud-computing", "Cloud Computing", "aws", "azure", "cloud");
        t(x, Category.TECH, "devops", "DevOps", "kubernetes", "ci cd");
        t(x, Category.TECH, "game-development", "Game Development", "game dev", "unity", "unreal");
        t(x, Category.TECH, "ui-ux", "UI/UX Design", "ui ux", "product design", "figma");
        t(x, Category.TECH, "competitive-programming", "Competitive Programming", "cp", "codeforces", "leetcode", "codechef");
        t(x, Category.TECH, "open-source", "Open Source", "oss", "github");
        t(x, Category.TECH, "hackathons", "Hackathons", "hackathon");
        t(x, Category.TECH, "linux", "Linux", "ubuntu", "arch linux");
        t(x, Category.TECH, "pc-building", "PC Building", "custom pc", "pc builds");
        t(x, Category.TECH, "3d-printing", "3D Printing", "3d printer");
        t(x, Category.TECH, "electronics", "Electronics", "circuits");
        t(x, Category.TECH, "arduino", "Arduino", "raspberry pi", "microcontrollers");
        t(x, Category.TECH, "product-management", "Product Management", "product manager");

        // ---------- ARTS ----------
        t(x, Category.ARTS, "drawing", "Drawing", "sketching", "doodling");
        t(x, Category.ARTS, "painting", "Painting", "acrylic", "watercolour", "watercolor");
        t(x, Category.ARTS, "digital-art", "Digital Art", "digital painting", "procreate");
        t(x, Category.ARTS, "photography", "Photography", "photographer");
        t(x, Category.ARTS, "videography", "Videography", "video editing");
        t(x, Category.ARTS, "graphic-design", "Graphic Design", "photoshop", "illustrator");
        t(x, Category.ARTS, "animation", "Animation", "motion graphics", "2d animation", "3d animation");
        t(x, Category.ARTS, "calligraphy", "Calligraphy", "hand lettering");
        t(x, Category.ARTS, "poetry", "Poetry", "poems", "shayari", "spoken word");
        t(x, Category.ARTS, "creative-writing", "Creative Writing", "writing", "storytelling");
        t(x, Category.ARTS, "dance", "Dance", "dancing");
        t(x, Category.ARTS, "classical-dance", "Classical Dance", "bharatanatyam", "kathak", "odissi");
        t(x, Category.ARTS, "hip-hop-dance", "Hip Hop Dance", "street dance", "breaking");
        t(x, Category.ARTS, "theatre", "Theatre", "drama", "theater", "acting");
        t(x, Category.ARTS, "standup-comedy", "Stand-up Comedy", "standup", "comedy");
        t(x, Category.ARTS, "fashion", "Fashion", "styling", "fashion design");
        t(x, Category.ARTS, "origami", "Origami");
        t(x, Category.ARTS, "pottery", "Pottery", "ceramics");
        t(x, Category.ARTS, "film-making", "Film Making", "short films", "cinematography");
        t(x, Category.ARTS, "sculpture", "Sculpture");

        // ---------- FOOD ----------
        t(x, Category.FOOD, "cooking", "Cooking", "home cooking");
        t(x, Category.FOOD, "baking", "Baking", "cakes", "pastry");
        t(x, Category.FOOD, "street-food", "Street Food", "chaat");
        t(x, Category.FOOD, "coffee", "Coffee", "cafe hopping", "espresso");
        t(x, Category.FOOD, "tea", "Tea", "chai");
        t(x, Category.FOOD, "veganism", "Veganism", "vegan", "plant based");
        t(x, Category.FOOD, "food-blogging", "Food Blogging", "foodie", "food photography");
        t(x, Category.FOOD, "barbecue", "Barbecue", "bbq", "grilling");
        t(x, Category.FOOD, "desserts", "Desserts", "sweets", "chocolate");
        t(x, Category.FOOD, "mixology", "Mixology", "cocktails");
        t(x, Category.FOOD, "biryani", "Biryani");

        // ---------- FILM & TV ----------
        t(x, Category.FILM_TV, "anime", "Anime", "weeb");
        t(x, Category.FILM_TV, "manga", "Manga", "manhwa");
        t(x, Category.FILM_TV, "marvel", "Marvel", "mcu");
        t(x, Category.FILM_TV, "dc-comics", "DC", "dceu");
        t(x, Category.FILM_TV, "kdrama", "K-Drama", "korean drama");
        t(x, Category.FILM_TV, "bollywood", "Bollywood", "hindi cinema");
        t(x, Category.FILM_TV, "hollywood", "Hollywood");
        t(x, Category.FILM_TV, "documentaries", "Documentaries", "documentary", "docs");
        t(x, Category.FILM_TV, "sci-fi", "Sci-Fi", "science fiction");
        t(x, Category.FILM_TV, "horror", "Horror", "horror movies");
        t(x, Category.FILM_TV, "sitcoms", "Sitcoms", "the office", "friends");
        t(x, Category.FILM_TV, "thriller", "Thrillers", "crime shows");
        t(x, Category.FILM_TV, "indie-films", "Indie Films", "arthouse", "world cinema");
        t(x, Category.FILM_TV, "web-series", "Web Series");
        t(x, Category.FILM_TV, "regional-cinema", "Regional Cinema", "tollywood", "kollywood", "mollywood");

        // ---------- FITNESS ----------
        t(x, Category.FITNESS, "gym", "Gym", "working out", "lifting", "weight training", "workout");
        t(x, Category.FITNESS, "weightlifting", "Weightlifting", "powerlifting", "strength training");
        t(x, Category.FITNESS, "running", "Running", "jogging");
        t(x, Category.FITNESS, "yoga", "Yoga");
        t(x, Category.FITNESS, "meditation", "Meditation", "mindfulness");
        t(x, Category.FITNESS, "calisthenics", "Calisthenics", "bodyweight training");
        t(x, Category.FITNESS, "crossfit", "CrossFit");
        t(x, Category.FITNESS, "pilates", "Pilates");
        t(x, Category.FITNESS, "nutrition", "Nutrition", "macros", "dieting");
        t(x, Category.FITNESS, "marathon", "Marathon", "half marathon");
        t(x, Category.FITNESS, "zumba", "Zumba", "aerobics");
        t(x, Category.FITNESS, "cycling", "Cycling", "bicycling");

        // ---------- VOLUNTEERING ----------
        t(x, Category.VOLUNTEERING, "ngo-work", "NGO Work", "ngo", "social work");
        t(x, Category.VOLUNTEERING, "teaching", "Teaching", "tutoring");
        t(x, Category.VOLUNTEERING, "environment", "Environment", "sustainability", "climate action");
        t(x, Category.VOLUNTEERING, "animal-welfare", "Animal Welfare", "animal rescue", "stray feeding");
        t(x, Category.VOLUNTEERING, "blood-donation", "Blood Donation");
        t(x, Category.VOLUNTEERING, "community-service", "Community Service", "social service");
        t(x, Category.VOLUNTEERING, "mental-health-advocacy", "Mental Health Advocacy", "mental health");
        t(x, Category.VOLUNTEERING, "women-empowerment", "Women Empowerment");
        t(x, Category.VOLUNTEERING, "rural-development", "Rural Development");
        t(x, Category.VOLUNTEERING, "disaster-relief", "Disaster Relief");

        // ---------- OUTDOORS ----------
        t(x, Category.OUTDOORS, "trekking", "Trekking", "hiking", "trek");
        t(x, Category.OUTDOORS, "camping", "Camping");
        t(x, Category.OUTDOORS, "travel", "Travel", "travelling", "traveling", "wanderlust");
        t(x, Category.OUTDOORS, "backpacking", "Backpacking");
        t(x, Category.OUTDOORS, "road-trips", "Road Trips", "road trip");
        t(x, Category.OUTDOORS, "stargazing", "Stargazing");
        t(x, Category.OUTDOORS, "birdwatching", "Birdwatching", "birding");
        t(x, Category.OUTDOORS, "scuba-diving", "Scuba Diving", "diving", "snorkelling");
        t(x, Category.OUTDOORS, "rock-climbing", "Rock Climbing", "climbing", "bouldering");
        t(x, Category.OUTDOORS, "surfing", "Surfing");
        t(x, Category.OUTDOORS, "adventure-sports", "Adventure Sports", "bungee jumping", "paragliding", "rafting");

        // ---------- OTHER ----------
        t(x, Category.OTHER, "entrepreneurship", "Entrepreneurship", "startup", "startups");
        t(x, Category.OTHER, "investing", "Investing", "stock market", "stocks", "trading");
        t(x, Category.OTHER, "journaling", "Journaling", "bullet journal");
        t(x, Category.OTHER, "reading", "Reading", "books", "novels", "bookworm");
        t(x, Category.OTHER, "podcasts", "Podcasts", "podcasting");
        t(x, Category.OTHER, "astrology", "Astrology", "horoscope", "zodiac");
        t(x, Category.OTHER, "languages", "Languages", "language learning", "polyglot");
        t(x, Category.OTHER, "politics", "Politics", "current affairs", "geopolitics");
        t(x, Category.OTHER, "self-improvement", "Self Improvement", "productivity", "self help");
        t(x, Category.OTHER, "gardening", "Gardening", "plants", "plant parent");
        t(x, Category.OTHER, "sneakers", "Sneakers", "sneakerhead");
        t(x, Category.OTHER, "cars", "Cars", "automobiles", "motorsport", "formula 1");
        t(x, Category.OTHER, "motorcycles", "Motorcycles", "motorbikes", "superbikes");
        t(x, Category.OTHER, "personal-finance", "Personal Finance", "budgeting");

        return x;
    }
}

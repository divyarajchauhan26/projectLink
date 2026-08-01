package CampusConnect.domain;

import java.awt.Point;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A student.
 * <p>
 * In V1 this was a labelled dot with coordinates. It is now a profile: the thing the
 * matching engine actually reasons over. Connections are no longer arbitrary lines a
 * user drew — they are suggested from what these fields say, and accepted by a person.
 * <p>
 * Three kinds of state live here, deliberately kept distinct:
 * <ul>
 *   <li><b>Identity &amp; profile</b> — entered by the student, persisted</li>
 *   <li><b>Layout</b> — {@code x, y, dx, dy}, owned by the physics loop, persisted (position only)</li>
 *   <li><b>{@link NodeMetrics}</b> — everything computed, never persisted</li>
 * </ul>
 */
public class UserNode {

    // ================= identity =================

    private final String id;
    private String name;
    private String handle = "";
    private String avatarEmoji = "";
    private Instant joinedAt = Instant.now();

    // ================= layout / physics =================

    public double x, y;
    public double dx, dy;

    // ================= computed =================

    private final NodeMetrics metrics = new NodeMetrics();

    // ================= profile: academic =================

    private String major = "";
    private String minor = "";
    private int year = 0;
    private final List<String> courses = new ArrayList<>();

    // ================= profile: context =================

    private String hometown = "";
    private String hostel = "";
    private final Set<String> languages = new LinkedHashSet<>();

    // ================= profile: the human part =================

    private String bio = "";

    /**
     * Interests mapped to how much the student cares (1–5).
     * <p>
     * Stored as a single map rather than a Set plus a parallel intensity Map so the two
     * cannot drift out of sync — a bug that would silently skew every similarity score.
     */
    private final Map<InterestTag, Integer> interests = new LinkedHashMap<>();

    private final Set<String> clubs = new LinkedHashSet<>();
    private final Set<String> skills = new LinkedHashSet<>();

    /** Skills this person is happy to teach — powers complementary matching. */
    private final Set<String> canTeach = new LinkedHashSet<>();
    /** Skills this person wants to pick up. Pairs against another person's canTeach. */
    private final Set<String> wantsToLearn = new LinkedHashSet<>();

    private final Set<Intent> lookingFor = EnumSet.noneOf(Intent.class);

    public static final int DEFAULT_INTENSITY = 3;
    public static final int MIN_INTENSITY = 1;
    public static final int MAX_INTENSITY = 5;

    // ================= construction =================

    public UserNode(String name, int x, int y) {
        this(UUID.randomUUID().toString(), name, x, y);
    }

    /** Used when the id is already known — deserialization. */
    public UserNode(String id, String name, int x, int y) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
        this.dx = 0;
        this.dy = 0;
    }

    // ================= identity =================

    public String getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getHandle() { return handle; }
    public void setHandle(String handle) { this.handle = handle == null ? "" : handle; }

    public String getAvatarEmoji() { return avatarEmoji; }
    public void setAvatarEmoji(String e) { this.avatarEmoji = e == null ? "" : e; }

    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant t) { this.joinedAt = t == null ? Instant.now() : t; }

    // ================= layout =================

    public int getX() { return (int) x; }
    public int getY() { return (int) y; }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        // A dragged node should stay put, not fly off with its old velocity.
        this.dx = 0;
        this.dy = 0;
    }

    public boolean contains(Point p) {
        return p.distance(x, y) <= 20;
    }

    // ================= computed =================

    public NodeMetrics getMetrics() { return metrics; }

    // ================= academic =================

    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major == null ? "" : major; }

    public String getMinor() { return minor; }
    public void setMinor(String minor) { this.minor = minor == null ? "" : minor; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public List<String> getCourses() { return Collections.unmodifiableList(courses); }
    public void setCourses(Collection<String> c) { replace(courses, c); }
    public void addCourse(String c) { if (notBlank(c)) courses.add(c.trim()); }

    // ================= context =================

    public String getHometown() { return hometown; }
    public void setHometown(String h) { this.hometown = h == null ? "" : h; }

    public String getHostel() { return hostel; }
    public void setHostel(String h) { this.hostel = h == null ? "" : h; }

    public Set<String> getLanguages() { return Collections.unmodifiableSet(languages); }
    public void setLanguages(Collection<String> l) { replace(languages, l); }
    public void addLanguage(String l) { if (notBlank(l)) languages.add(l.trim()); }

    // ================= bio =================

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio == null ? "" : bio; }

    // ================= interests =================

    public Set<InterestTag> getInterests() {
        return Collections.unmodifiableSet(interests.keySet());
    }

    /** Interest to intensity, 1–5. */
    public Map<InterestTag, Integer> getInterestIntensities() {
        return Collections.unmodifiableMap(interests);
    }

    public void addInterest(InterestTag tag) { addInterest(tag, DEFAULT_INTENSITY); }

    public void addInterest(InterestTag tag, int intensity) {
        if (tag == null) return;
        interests.put(tag, clampIntensity(intensity));
    }

    public void removeInterest(InterestTag tag) { interests.remove(tag); }

    public boolean hasInterest(InterestTag tag) { return interests.containsKey(tag); }

    /** Intensity for a tag, or 0 if this person does not have it. */
    public int getIntensity(InterestTag tag) { return interests.getOrDefault(tag, 0); }

    public void clearInterests() { interests.clear(); }

    private static int clampIntensity(int i) {
        return Math.max(MIN_INTENSITY, Math.min(MAX_INTENSITY, i));
    }

    /**
     * The category this person has the most interests in — used to colour their node.
     * Ties break toward the first category declared in {@link Category}.
     *
     * @return the dominant category, or null when no interests are set
     */
    public Category getDominantCategory() {
        if (interests.isEmpty()) return null;
        Map<Category, Integer> weight = new EnumMap<>(Category.class);
        for (Map.Entry<InterestTag, Integer> e : interests.entrySet()) {
            weight.merge(e.getKey().category(), e.getValue(), Integer::sum);
        }
        Category best = null;
        int bestWeight = -1;
        for (Category c : Category.values()) {
            int w = weight.getOrDefault(c, 0);
            if (w > bestWeight) { bestWeight = w; best = c; }
        }
        return best;
    }

    // ================= clubs, skills, exchange =================

    public Set<String> getClubs() { return Collections.unmodifiableSet(clubs); }
    public void setClubs(Collection<String> c) { replace(clubs, c); }
    public void addClub(String c) { if (notBlank(c)) clubs.add(c.trim()); }

    public Set<String> getSkills() { return Collections.unmodifiableSet(skills); }
    public void setSkills(Collection<String> s) { replace(skills, s); }
    public void addSkill(String s) { if (notBlank(s)) skills.add(s.trim()); }

    public Set<String> getCanTeach() { return Collections.unmodifiableSet(canTeach); }
    public void setCanTeach(Collection<String> s) { replace(canTeach, s); }
    public void addCanTeach(String s) { if (notBlank(s)) canTeach.add(s.trim()); }

    public Set<String> getWantsToLearn() { return Collections.unmodifiableSet(wantsToLearn); }
    public void setWantsToLearn(Collection<String> s) { replace(wantsToLearn, s); }
    public void addWantsToLearn(String s) { if (notBlank(s)) wantsToLearn.add(s.trim()); }

    // ================= intent =================

    public Set<Intent> getLookingFor() { return Collections.unmodifiableSet(lookingFor); }

    public void setLookingFor(Collection<Intent> intents) {
        lookingFor.clear();
        if (intents != null) lookingFor.addAll(intents);
    }

    public void addIntent(Intent i) { if (i != null) lookingFor.add(i); }
    public boolean isLookingFor(Intent i) { return lookingFor.contains(i); }

    // ================= profile quality =================

    /**
     * How filled-in this profile is, 0.0–1.0. Drives the onboarding completeness meter,
     * and flags profiles too sparse for matching to say anything useful about.
     */
    public double profileCompleteness() {
        int filled = 0;
        if (notBlank(name)) filled++;
        if (notBlank(bio)) filled++;
        if (notBlank(major)) filled++;
        if (year > 0) filled++;
        if (notBlank(hometown)) filled++;
        if (interests.size() >= 3) filled++;
        if (!languages.isEmpty()) filled++;
        if (!lookingFor.isEmpty()) filled++;
        return filled / 8.0;
    }

    // ================= helpers =================

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static void replace(Collection<String> target, Collection<String> source) {
        target.clear();
        if (source == null) return;
        for (String s : source) if (notBlank(s)) target.add(s.trim());
    }

    @Override
    public String toString() { return name; }
}

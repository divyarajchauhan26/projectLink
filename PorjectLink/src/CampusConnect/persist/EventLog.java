package CampusConnect.persist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every suggestion shown, accepted or rejected.
 * <p>
 * <b>This is the training data.</b> Phase 5 replaces the hand-tuned affinity weights with
 * a learned ranker, and the only thing that can train one is a record of which suggestions
 * people actually acted on. That record cannot be reconstructed later — if it is not being
 * written from the first day the feed exists, the first model has nothing to learn from.
 * So it is collected now, long before anything reads it.
 * <p>
 * Dismissal reasons are the valuable part. "Not interested" alone says a suggestion was
 * bad; "I already know them" says the graph is missing an edge, and "not my thing" says
 * the interest weighting misfired. Those are different failures with different fixes.
 */
public final class EventLog {

    public enum Action { SHOWN, CONNECTED, DISMISSED }

    /**
     * @param actorId   who was being advised
     * @param subjectId who was suggested to them
     * @param score     the affinity at the time, so a later model can learn from what the
     *                  ranker believed rather than from what it believes now
     */
    public record Event(String at, String actorId, String subjectId,
                        Action action, String reason, double score) {

        public Instant instant() {
            try { return Instant.parse(at); }
            catch (Exception e) { return Instant.EPOCH; }
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<Event> events = new ArrayList<>();

    public void record(String actorId, String subjectId, Action action, String reason, double score) {
        events.add(new Event(Instant.now().toString(), actorId, subjectId, action, reason, score));
    }

    public List<Event> all() { return List.copyOf(events); }

    public int size() { return events.size(); }

    public void clear() { events.clear(); }

    /**
     * People this actor dismissed recently.
     * <p>
     * Re-showing someone a face they just rejected is the fastest way to make a feed feel
     * broken, so dismissals suppress a candidate for a while. It is a cooldown rather than
     * a permanent block because people and profiles change.
     */
    public Set<String> recentlyDismissedBy(String actorId, Duration cooldown) {
        Set<String> out = new HashSet<>();
        if (actorId == null) return out;
        Instant cutoff = Instant.now().minus(cooldown);
        for (Event e : events) {
            if (e.action() == Action.DISMISSED
                    && actorId.equals(e.actorId())
                    && e.instant().isAfter(cutoff)) {
                out.add(e.subjectId());
            }
        }
        return out;
    }

    /** How many of the connections that were made came from a suggestion. */
    public long acceptedCount() {
        return events.stream().filter(e -> e.action() == Action.CONNECTED).count();
    }

    public long dismissedCount() {
        return events.stream().filter(e -> e.action() == Action.DISMISSED).count();
    }

    /**
     * Acceptance rate over suggestions that got a decision either way. The single number
     * that says whether the recommender is any good.
     *
     * @return [0,1], or -1 when nothing has been decided yet
     */
    public double acceptanceRate() {
        long decided = acceptedCount() + dismissedCount();
        return decided == 0 ? -1 : (double) acceptedCount() / decided;
    }

    // ================= persistence =================

    public void save(File file) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(events, w);
        }
    }

    public void load(File file) throws IOException {
        if (!file.isFile()) return;
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            List<Event> loaded = GSON.fromJson(r, new TypeToken<List<Event>>() {}.getType());
            events.clear();
            if (loaded != null) events.addAll(loaded);
        } catch (Exception e) {
            throw new IOException("Could not read the event log: " + e.getMessage(), e);
        }
    }
}

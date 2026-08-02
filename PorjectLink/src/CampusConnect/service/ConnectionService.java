package CampusConnect.service;

import CampusConnect.domain.Edge;
import CampusConnect.domain.InterestTag;
import CampusConnect.domain.Person;
import CampusConnect.algorithm.similarity.SimilarityEngine;

import java.time.Instant;
import java.util.*;

/**
 * Connection requests, and where an accepted connection came from.
 * <p>
 * In V1 clicking two nodes created an edge instantly, which is fine for a graph editor
 * and wrong for a social app — a friendship is not something one party declares. Requests
 * make it two-sided: one person asks, the other agrees, and only then does the edge exist.
 * <p>
 * <b>Deliberately layered on top of the existing graph rather than replacing it.</b>
 * {@link NetworkService}'s adjacency list stays exactly as it is and continues to mean
 * "accepted connections" — which is what every algorithm in the project already assumes.
 * Rebuilding the core graph around a Connection object would have touched every algorithm,
 * every metric and the persistence layer, to express something that fits perfectly well
 * beside it.
 */
public final class ConnectionService {

    /** How two people know each other. */
    public enum Kind {
        FRIEND("Friend"), CLASSMATE("Classmate"), TEAMMATE("Teammate"),
        ROOMMATE("Roommate"), MENTOR("Mentor");

        private final String label;
        Kind(String label) { this.label = label; }
        public String getLabel() { return label; }
        @Override public String toString() { return label; }
    }

    /** Whether a connection was made by hand or came from a suggestion. */
    public enum Origin { MANUAL, SUGGESTED, IMPORTED }

    public record Request(String id, Person from, Person to, String message, Instant sentAt) {}

    /** What we know about an accepted connection, beyond that it exists. */
    public record Meta(Kind kind, Origin origin, Instant since) {}

    private final NetworkService service;
    private final List<Request> pending = new ArrayList<>();
    private final Map<String, Meta> metadata = new HashMap<>();

    public ConnectionService(NetworkService service) {
        this.service = service;
    }

    // ================= requests =================

    /**
     * @return the created request, or null if these two are already connected, a request
     *         is already open, or it is somebody requesting themselves
     */
    public Request request(Person from, Person to, String message) {
        if (from == null || to == null || from == to) return null;
        if (service.getConnections(from).contains(to)) return null;
        if (findPending(from, to) != null) return null;

        Request r = new Request(UUID.randomUUID().toString(), from, to,
                message == null ? "" : message.trim(), Instant.now());
        pending.add(r);
        return r;
    }

    /** An open request between two people, in either direction. */
    public Request findPending(Person a, Person b) {
        for (Request r : pending) {
            if ((r.from() == a && r.to() == b) || (r.from() == b && r.to() == a)) return r;
        }
        return null;
    }

    /** Requests waiting on this person to answer. */
    public List<Request> incoming(Person person) {
        List<Request> out = new ArrayList<>();
        for (Request r : pending) if (r.to() == person) out.add(r);
        return out;
    }

    /** Requests this person has sent and not yet heard back on. */
    public List<Request> outgoing(Person person) {
        List<Request> out = new ArrayList<>();
        for (Request r : pending) if (r.from() == person) out.add(r);
        return out;
    }

    public List<Request> allPending() { return List.copyOf(pending); }

    public boolean accept(Request request, Kind kind, Origin origin) throws Exception {
        if (request == null || !pending.remove(request)) return false;
        service.addConnection(request.from(), request.to());
        setMeta(request.from(), request.to(), kind, origin);
        return true;
    }

    public boolean decline(Request request) { return pending.remove(request); }

    // ================= metadata =================

    public void setMeta(Person a, Person b, Kind kind, Origin origin) {
        metadata.put(Edge.makeKey(a, b),
                new Meta(kind == null ? Kind.FRIEND : kind,
                         origin == null ? Origin.MANUAL : origin,
                         Instant.now()));
    }

    public Meta metaFor(Person a, Person b) {
        return metadata.getOrDefault(Edge.makeKey(a, b),
                new Meta(Kind.FRIEND, Origin.MANUAL, null));
    }

    /**
     * What share of connections came from a suggestion.
     * <p>
     * The one number that says whether the recommender is earning its place. Without the
     * origin recorded at creation time it cannot be reconstructed afterwards.
     *
     * @return [0,1], or -1 when nothing has been recorded
     */
    public double suggestedShare() {
        if (metadata.isEmpty()) return -1;
        long suggested = metadata.values().stream()
                .filter(m -> m.origin() == Origin.SUGGESTED).count();
        return (double) suggested / metadata.size();
    }

    // ================= icebreakers =================

    /**
     * An opening line built from what two people actually share.
     * <p>
     * The hardest part of a suggestion is not deciding to accept it, it is the first
     * message. Everything needed for one was already computed to produce the match.
     */
    public String icebreaker(Person from, Person to, SimilarityEngine similarity) {
        List<InterestTag> shared = similarity.sharedInterests(from, to, 2);
        if (!shared.isEmpty()) {
            return "Hey " + firstName(to) + " — saw you're into "
                    + shared.get(0).label().toLowerCase(Locale.ROOT)
                    + (shared.size() > 1
                        ? " and " + shared.get(1).label().toLowerCase(Locale.ROOT) : "")
                    + " too. What got you into it?";
        }

        List<String> teach = similarity.whatTheyCanTeach(from, to);
        if (!teach.isEmpty()) {
            return "Hey " + firstName(to) + " — I've been wanting to learn "
                    + teach.get(0).toLowerCase(Locale.ROOT) + ". Any advice for a beginner?";
        }

        if (!from.getHometown().isBlank() && from.getHometown().equalsIgnoreCase(to.getHometown())) {
            return "Hey " + firstName(to) + " — spotted you're from " + to.getHometown()
                    + " as well. Small world. How are you finding it here?";
        }

        if (!from.getMajor().isBlank() && from.getMajor().equalsIgnoreCase(to.getMajor())) {
            return "Hey " + firstName(to) + " — we're both in " + to.getMajor()
                    + ". How are you finding this sem?";
        }

        return "Hey " + firstName(to) + " — we keep turning up in the same circles. "
                + "Thought I'd say hello.";
    }

    private static String firstName(Person p) {
        String[] parts = p.getName().trim().split("\\s+");
        return parts.length > 0 ? parts[0] : p.getName();
    }
}

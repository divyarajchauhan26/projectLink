package CampusConnect.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A named set of people — a club, a squad, or an interest crowd.
 * <p>
 * Groups are the third layer of the app, alongside real friendships and suggested
 * connections. They exist because a lot of campus life is not pairwise: a band, a study
 * group and a football team are all single things with several members, and modelling
 * them as a mesh of individual friendships loses the fact that the <em>group</em> is what
 * people belong to.
 * <p>
 * Structurally this makes the graph bipartite — people connect to groups, and two people
 * who share a group are related through it without needing a direct edge between them.
 */
public class Group {

    /** Where a group came from, which decides how much the app should trust it. */
    public enum Origin {
        /** Proposed from a clique — everybody in it already knows everybody. */
        SQUAD,
        /** Everyone who holds a given interest. Derived, never edited by hand. */
        INTEREST,
        /** Made by a person. */
        USER
    }

    private final String id;
    private String name;
    private String description;
    private final Origin origin;
    private final Instant createdAt;

    private final Set<String> memberIds = new LinkedHashSet<>();
    /** Interests this group is about. Empty for a purely social group. */
    private final Set<InterestTag> tags = new LinkedHashSet<>();

    public Group(String name, Origin origin) {
        this(UUID.randomUUID().toString(), name, origin);
    }

    public Group(String id, String name, Origin origin) {
        this.id = Objects.requireNonNull(id);
        this.name = name == null ? "Untitled" : name;
        this.origin = origin == null ? Origin.USER : origin;
        this.description = "";
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name == null ? "Untitled" : name; }

    public String getDescription() { return description; }
    public void setDescription(String d) { this.description = d == null ? "" : d; }

    public Origin getOrigin() { return origin; }
    public Instant getCreatedAt() { return createdAt; }

    public Set<String> getMemberIds() { return Collections.unmodifiableSet(memberIds); }
    public int size() { return memberIds.size(); }

    public void addMember(Person p) { if (p != null) memberIds.add(p.getId()); }
    public void addMemberId(String id) { if (id != null && !id.isBlank()) memberIds.add(id); }
    public void removeMember(Person p) { if (p != null) memberIds.remove(p.getId()); }
    public boolean contains(Person p) { return p != null && memberIds.contains(p.getId()); }

    public void setMemberIds(Collection<String> ids) {
        memberIds.clear();
        if (ids != null) for (String i : ids) addMemberId(i);
    }

    public Set<InterestTag> getTags() { return Collections.unmodifiableSet(tags); }
    public void addTag(InterestTag t) { if (t != null) tags.add(t); }
    public void setTags(Collection<InterestTag> ts) {
        tags.clear();
        if (ts != null) tags.addAll(ts);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Group g && id.equals(g.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() { return name + " (" + memberIds.size() + ")"; }
}

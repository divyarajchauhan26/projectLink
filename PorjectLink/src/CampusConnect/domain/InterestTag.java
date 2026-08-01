package CampusConnect.domain;

import java.util.Objects;
import java.util.Set;

/**
 * A single canonical interest.
 * <p>
 * The whole point of this type is that <em>there is exactly one of it per interest</em>.
 * "bball", "Basket Ball", "hoops" and "BASKETBALL" are four strings and one interest;
 * {@link InterestCatalog} collapses them all to the same {@code InterestTag}. Without
 * that guarantee every similarity score downstream is noise.
 *
 * @param id       canonical, lowercase, hyphenated, stable forever — this is what gets persisted
 * @param label    human-facing display text
 * @param category top-level grouping, used for node colouring and browsing
 * @param aliases  alternative spellings and nicknames that resolve to this tag
 */
public record InterestTag(String id, String label, Category category, Set<String> aliases) {

    public InterestTag {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(category, "category");
        aliases = aliases == null ? Set.of() : Set.copyOf(aliases);
    }

    /**
     * Identity is the id alone. The record default would compare aliases too, which
     * would mean editing an alias silently breaks equality with already-persisted tags.
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof InterestTag other && id.equals(other.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() { return label; }
}

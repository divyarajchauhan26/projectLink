package CampusConnect.persist;

import CampusConnect.domain.Edge;
import CampusConnect.domain.Intent;
import CampusConnect.domain.InterestCatalog;
import CampusConnect.domain.InterestTag;
import CampusConnect.domain.Person;
import CampusConnect.service.NetworkService;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Reads and writes the campus graph as JSON, and exports an adjacency matrix as CSV.
 * <p>
 * <b>Why DTOs instead of serializing {@link Person} directly.</b> Gson reflecting over the
 * domain object would drag in things that must never be persisted — {@code NodeMetrics}
 * is derived and would go stale the moment the graph changed, and {@code dx/dy} is
 * physics velocity, meaningless across sessions. It would also write each interest as a
 * full nested object including every alias, so the file would balloon and a single edit
 * to the catalog would invalidate saved files. An explicit wire format keeps the two free
 * to evolve independently: interests persist as {@code {tagId: intensity}} and nothing
 * computed is written at all.
 */
public final class GraphIO {

    /** Bump when the wire format changes shape. */
    public static final int SCHEMA_VERSION = 2;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private GraphIO() {}

    // ================= wire format =================

    private static final class GraphDto {
        int schemaVersion = SCHEMA_VERSION;
        String savedAt;
        List<PersonDto> people = new ArrayList<>();
        List<ConnectionDto> connections = new ArrayList<>();
    }

    private static final class PersonDto {
        String id, name, handle, avatarEmoji;
        int x, y;
        String major, minor;
        int year;
        List<String> courses;
        String hometown, hostel;
        List<String> languages;
        String bio;
        /** tag id -> intensity 1..5 */
        Map<String, Integer> interests;
        List<String> clubs, skills, canTeach, wantsToLearn;
        /** {@link Intent} enum names */
        List<String> lookingFor;
        String joinedAt;
    }

    private static final class ConnectionDto {
        String source, target;
        double weight;
    }

    // ================= load reporting =================

    /**
     * What happened during a load. Surfaced to the user rather than swallowed — silently
     * dropping half a file is far worse than saying so.
     */
    public record LoadReport(int people, int connections, List<String> warnings) {
        public boolean clean() { return warnings.isEmpty(); }

        public String summary() {
            String s = people + " people, " + connections + " connections";
            return warnings.isEmpty() ? s : s + " (" + warnings.size() + " warning(s))";
        }
    }

    // ================= save =================

    public static void save(NetworkService service, File file) throws IOException {
        GraphDto dto = new GraphDto();
        dto.savedAt = Instant.now().toString();

        for (Person p : service.getAllUsers()) {
            PersonDto d = new PersonDto();
            d.id = p.getId();
            d.name = p.getName();
            d.handle = emptyToNull(p.getHandle());
            d.avatarEmoji = emptyToNull(p.getAvatarEmoji());
            d.x = p.getX();
            d.y = p.getY();
            d.major = emptyToNull(p.getMajor());
            d.minor = emptyToNull(p.getMinor());
            d.year = p.getYear();
            d.courses = nullIfEmpty(p.getCourses());
            d.hometown = emptyToNull(p.getHometown());
            d.hostel = emptyToNull(p.getHostel());
            d.languages = nullIfEmpty(p.getLanguages());
            d.bio = emptyToNull(p.getBio());

            if (!p.getInterests().isEmpty()) {
                d.interests = new LinkedHashMap<>();
                // Canonical ids only. Labels are display text and get reworded;
                // ids are the stable contract with the file.
                p.getInterestIntensities().forEach((tag, i) -> d.interests.put(tag.id(), i));
            }

            d.clubs = nullIfEmpty(p.getClubs());
            d.skills = nullIfEmpty(p.getSkills());
            d.canTeach = nullIfEmpty(p.getCanTeach());
            d.wantsToLearn = nullIfEmpty(p.getWantsToLearn());

            if (!p.getLookingFor().isEmpty()) {
                d.lookingFor = new ArrayList<>();
                for (Intent i : p.getLookingFor()) d.lookingFor.add(i.name());
            }

            d.joinedAt = p.getJoinedAt().toString();
            dto.people.add(d);
        }

        Set<String> written = new HashSet<>();
        for (Person p : service.getAllUsers()) {
            for (Person friend : service.getConnections(p)) {
                String key = Edge.makeKey(p, friend);
                if (!written.add(key)) continue;
                ConnectionDto c = new ConnectionDto();
                c.source = p.getId();
                c.target = friend.getId();
                c.weight = service.getEdgeWeight(p, friend);
                dto.connections.add(c);
            }
        }

        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(dto, w);
        }
    }

    // ================= load =================

    public static LoadReport load(NetworkService service, File file) throws IOException {
        GraphDto dto;
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            dto = GSON.fromJson(r, GraphDto.class);
        } catch (JsonSyntaxException e) {
            throw new IOException("Not valid JSON: " + e.getMessage(), e);
        }

        if (dto == null) throw new IOException("File is empty.");
        if (dto.people == null) {
            // v1 wrote "nodes"/"edges"; say so plainly instead of loading an empty graph.
            throw new IOException("No \"people\" array found. This may be a v1 save file "
                    + "(which used \"nodes\"/\"edges\") — those cannot be loaded.");
        }
        if (dto.schemaVersion > SCHEMA_VERSION) {
            throw new IOException("File uses schema v" + dto.schemaVersion
                    + " but this build understands v" + SCHEMA_VERSION + ". Update the app.");
        }

        List<String> warnings = new ArrayList<>();
        InterestCatalog catalog = InterestCatalog.getDefault();

        service.clear();

        for (PersonDto d : dto.people) {
            if (d == null || d.name == null || d.name.isBlank()) {
                warnings.add("Skipped a person with no name.");
                continue;
            }
            String id = (d.id == null || d.id.isBlank()) ? UUID.randomUUID().toString() : d.id;
            service.addUserWithId(id, d.name, d.x, d.y);
            Person p = service.findUserById(id);
            if (p == null) continue;

            p.setHandle(d.handle);
            p.setAvatarEmoji(d.avatarEmoji);
            p.setMajor(d.major);
            p.setMinor(d.minor);
            p.setYear(d.year);
            p.setCourses(d.courses);
            p.setHometown(d.hometown);
            p.setHostel(d.hostel);
            p.setLanguages(d.languages);
            p.setBio(d.bio);
            p.setClubs(d.clubs);
            p.setSkills(d.skills);
            p.setCanTeach(d.canTeach);
            p.setWantsToLearn(d.wantsToLearn);

            if (d.interests != null) {
                for (Map.Entry<String, Integer> e : d.interests.entrySet()) {
                    // Resolve rather than trust: hand-edited files and older exports
                    // may hold labels or aliases instead of canonical ids.
                    InterestCatalog.Resolution res = catalog.resolve(e.getKey());
                    if (res.found()) {
                        InterestTag tag = res.tag();
                        p.addInterest(tag, e.getValue() == null ? Person.DEFAULT_INTENSITY : e.getValue());
                    } else {
                        warnings.add("Unknown interest '" + e.getKey() + "' on " + d.name);
                    }
                }
            }

            if (d.lookingFor != null) {
                for (String s : d.lookingFor) {
                    try {
                        p.addIntent(Intent.valueOf(s.trim().toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ex) {
                        warnings.add("Unknown intent '" + s + "' on " + d.name);
                    }
                }
            }

            if (d.joinedAt != null) {
                try { p.setJoinedAt(Instant.parse(d.joinedAt)); }
                catch (DateTimeParseException ex) { warnings.add("Bad joinedAt on " + d.name); }
            }
        }

        int edges = 0;
        if (dto.connections != null) {
            for (ConnectionDto c : dto.connections) {
                if (c == null) continue;
                Person a = service.findUserById(c.source);
                Person b = service.findUserById(c.target);
                if (a == null || b == null) {
                    warnings.add("Connection references a missing person; skipped.");
                    continue;
                }
                try {
                    service.addConnection(a, b);
                    service.setEdgeWeight(a, b, c.weight <= 0 ? 1.0 : c.weight);
                    edges++;
                } catch (Exception e) {
                    warnings.add("Duplicate connection " + a.getName() + " - " + b.getName() + "; skipped.");
                }
            }
        }

        return new LoadReport(service.getAllUsers().size(), edges, warnings);
    }

    // ================= CSV export =================

    /** Weighted adjacency matrix, for opening in a spreadsheet or pandas. */
    public static void exportCsv(NetworkService service, File file) throws IOException {
        List<Person> people = service.getAllUsers();
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {

            w.print("");
            for (Person p : people) { w.print(","); w.print(csv(p.getName())); }
            w.println();

            for (Person row : people) {
                w.print(csv(row.getName()));
                List<Person> friends = service.getConnections(row);
                for (Person col : people) {
                    w.print(",");
                    w.print(friends.contains(col)
                            ? String.format(Locale.ROOT, "%.1f", service.getEdgeWeight(row, col))
                            : "0");
                }
                w.println();
            }
        }
    }

    private static String csv(String s) {
        if (s == null) return "";
        return s.contains(",") || s.contains("\"")
                ? "\"" + s.replace("\"", "\"\"") + "\""
                : s;
    }

    // ================= helpers =================

    /** Keeps the JSON tidy — Gson omits nulls, so empty fields simply vanish. */
    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static List<String> nullIfEmpty(Collection<String> c) {
        return (c == null || c.isEmpty()) ? null : new ArrayList<>(c);
    }
}

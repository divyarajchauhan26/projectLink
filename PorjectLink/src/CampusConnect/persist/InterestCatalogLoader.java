package CampusConnect.persist;

import CampusConnect.domain.Category;
import CampusConnect.domain.InterestCatalog;
import CampusConnect.domain.InterestTag;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Optional local extension to the built-in interest vocabulary.
 * <p>
 * The 192 seed tags in {@link InterestCatalog} are compiled in, so a typo in a category
 * name fails at build time rather than silently at runtime. But every campus has its own
 * clubs, fests and in-jokes, and recompiling to add "Rangoli" is a bad experience — so a
 * file named {@value #DEFAULT_FILENAME} beside the app is merged at startup if present.
 *
 * <pre>
 * {
 *   "tags": [
 *     { "id": "rangoli", "label": "Rangoli", "category": "ARTS", "aliases": ["kolam"] }
 *   ]
 * }
 * </pre>
 *
 * Collisions with existing ids or aliases are rejected loudly rather than silently
 * overwriting — a hijacked alias would route an interest to the wrong tag permanently.
 */
public final class InterestCatalogLoader {

    public static final String DEFAULT_FILENAME = "interests-custom.json";

    private InterestCatalogLoader() {}

    private static final class TagFile {
        List<TagDto> tags;
    }

    private static final class TagDto {
        String id, label, category;
        List<String> aliases;
    }

    /**
     * Merge {@value #DEFAULT_FILENAME} from the working directory, when it exists.
     *
     * @return a human-readable note about what happened, or null if there was no file
     */
    public static String installIfPresent() {
        File f = new File(DEFAULT_FILENAME);
        if (!f.isFile()) return null;
        try {
            int n = install(f);
            return "Loaded " + n + " custom interest(s) from " + f.getName();
        } catch (Exception e) {
            return "Could not load " + f.getName() + ": " + e.getMessage();
        }
    }

    /**
     * @return how many tags were added
     * @throws IOException on unreadable or malformed input, or on a collision
     */
    public static int install(File file) throws IOException {
        TagFile parsed;
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            parsed = new Gson().fromJson(r, TagFile.class);
        } catch (JsonSyntaxException e) {
            throw new IOException("Not valid JSON: " + e.getMessage(), e);
        }
        if (parsed == null || parsed.tags == null || parsed.tags.isEmpty()) return 0;

        List<InterestTag> extras = new ArrayList<>();
        for (TagDto d : parsed.tags) {
            if (d == null || d.id == null || d.id.isBlank()) {
                throw new IOException("A tag entry is missing its \"id\".");
            }
            String id = d.id.trim().toLowerCase(Locale.ROOT);
            String label = (d.label == null || d.label.isBlank()) ? d.id.trim() : d.label.trim();

            Category category;
            try {
                category = d.category == null || d.category.isBlank()
                        ? Category.OTHER
                        : Category.valueOf(d.category.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IOException("Tag '" + id + "' has unknown category '" + d.category + "'.");
            }

            Set<String> aliases = new LinkedHashSet<>();
            if (d.aliases != null) {
                for (String a : d.aliases) if (a != null && !a.isBlank()) aliases.add(a.trim());
            }
            extras.add(new InterestTag(id, label, category, aliases));
        }

        try {
            InterestCatalog.installExtras(extras);
        } catch (IllegalStateException e) {
            throw new IOException(e.getMessage(), e);
        }
        return extras.size();
    }
}

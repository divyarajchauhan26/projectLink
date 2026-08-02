package CampusConnect.domain;

/**
 * What a student is currently looking for.
 * <p>
 * Intent is a strong matching signal in its own right: two people who both want a
 * STUDY_PARTNER should rank above two people who merely share interests. Some
 * intents are <em>complementary</em> rather than symmetric — a MENTOR pairs with a
 * MENTEE, not with another MENTOR — see {@link #complement()}.
 */
public enum Intent {
    STUDY_PARTNER ("Study partner",   "Someone to study with"),
    PROJECT_TEAM  ("Project team",    "Teammates for projects or hackathons"),
    ROOMMATE      ("Roommate",        "Someone to share a room with"),
    SPORTS_BUDDY  ("Sports buddy",    "Someone to play with"),
    JAM_SESSION   ("Jam session",     "Musicians to play with"),
    MENTOR        ("A mentor",        "A senior to learn from"),
    MENTEE        ("Someone to guide","A junior to help out"),
    JUST_FRIENDS  ("Just friends",    "Good people to hang out with");

    private final String label;
    private final String description;

    Intent(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }

    /**
     * The intent that pairs with this one. Most intents are symmetric (two people
     * both wanting a study partner match each other), but mentorship is directional.
     *
     * @return the complementary intent — for symmetric intents, this itself
     */
    public Intent complement() {
        return switch (this) {
            case MENTOR -> MENTEE;
            case MENTEE -> MENTOR;
            default     -> this;
        };
    }

    /**
     * The label as it reads inside a sentence — "looking for <b>a jam session</b>".
     * The display labels alone produce "looking for jam session", which is why this
     * lives on the enum rather than being patched together at the call site.
     */
    public String asObject() {
        return switch (this) {
            case STUDY_PARTNER -> "a study partner";
            case PROJECT_TEAM  -> "a project team";
            case ROOMMATE      -> "a roommate";
            case SPORTS_BUDDY  -> "a sports buddy";
            case JAM_SESSION   -> "a jam session";
            case MENTOR        -> "a mentor";
            case MENTEE        -> "someone to guide";
            case JUST_FRIENDS  -> "just friends";
        };
    }

    @Override
    public String toString() { return label; }
}

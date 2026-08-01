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

    @Override
    public String toString() { return label; }
}

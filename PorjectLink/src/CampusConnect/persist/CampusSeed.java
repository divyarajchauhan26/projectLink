package CampusConnect.persist;

import CampusConnect.domain.Intent;
import CampusConnect.domain.InterestCatalog;
import CampusConnect.domain.Person;
import CampusConnect.service.NetworkService;

import java.util.Locale;
import java.util.Random;

/**
 * A hand-authored campus of 24 students with full profiles.
 * <p>
 * <b>Why this is hand-written rather than randomly generated.</b> A recommender cannot be
 * evaluated against 25 nodes named Alice..Yara with no profiles — every suggestion looks
 * equally arbitrary, so you cannot tell a working engine from a broken one. This seed is
 * shaped deliberately so that specific answers are <em>checkable by eye</em>:
 * <ul>
 *   <li>Five interest clusters (tech, music, sports, arts, outdoors) with realistic
 *       internal density and a handful of cross-cluster bridges. Sized so the whole
 *       campus is readable at a glance — an earlier 40-person version was structurally
 *       richer and visually a mess, and nobody can evaluate a suggestion they cannot
 *       find on screen.</li>
 *   <li><b>Three first-years with almost no connections</b> — Aarav, Ira and Tanvi. These
 *       are the cold-start case the whole product exists for, and each has an obvious
 *       right answer: Aarav (guitar, indie, poetry, zero friends) should surface Kabir
 *       and Meera on content alone.</li>
 *   <li>One deliberate hub, Rahul, with a high degree — if he shows up in everybody's
 *       recommendations, the popularity penalty is not working.</li>
 *   <li>Complementary teach/learn pairs, e.g. Kabir teaches guitar while Zoya and Meera
 *       want to learn it.</li>
 *   <li>Overlapping-but-not-identical interests, so scores spread out instead of
 *       collapsing into ties.</li>
 * </ul>
 */
public final class CampusSeed {

    private CampusSeed() {}

    /** Fixed seed so the demo campus looks the same every launch. */
    private static final Random JITTER = new Random(42);

    public static void load(NetworkService svc, int width, int height) {
        svc.clear();

        int w = Math.max(900, width);
        int h = Math.max(650, height);

        // Cluster anchors, as fractions of the canvas. Pushed toward the corners and
        // spread further apart than the groups are wide, so the six clusters read as six
        // things on opening rather than one tangle that only resolves once physics runs.
        int techX  = (int) (w * 0.17), techY  = (int) (h * 0.32);
        int musicX = (int) (w * 0.83), musicY = (int) (h * 0.30);
        int sportX = (int) (w * 0.17), sportY = (int) (h * 0.74);
        int artX   = (int) (w * 0.83), artY   = (int) (h * 0.72);
        int outX   = (int) (w * 0.50), outY   = (int) (h * 0.16);
        int newX   = (int) (w * 0.50), newY   = (int) (h * 0.88);

        // ---------------- TECH ----------------
        p(svc, techX, techY, "Aditya Menon", "💻", "Computer Science", 3, "Kochi", "H4",
          "Malayalam, English, Hindi",
          "Third year CSE. Mostly living in the library or on Codeforces. Trying to get good at system design before placements hit.",
          "programming:5, competitive-programming:5, machine-learning:4, linux:3, valorant:3",
          "PROJECT_TEAM, MENTEE", "DSA, Java", "System design, Kubernetes");

        p(svc, techX, techY, "Rhea Sharma", "🤖", "Computer Science", 3, "Delhi", "H2",
          "Hindi, English",
          "ML nerd. Currently training models that mostly overfit. Ask me about transformers or where to get decent filter coffee near campus.",
          "machine-learning:5, artificial-intelligence:5, data-science:4, programming:4, coffee:3",
          "PROJECT_TEAM, STUDY_PARTNER", "Python, PyTorch", "MLOps");

        p(svc, techX, techY, "Karthik Iyer", "🐧", "Information Technology", 2, "Chennai", "H1",
          "Tamil, English",
          "Arch user, btw. I rice my desktop more than I do my assignments. Contribute to a couple of open source projects.",
          "linux:5, open-source:5, programming:4, cybersecurity:3, pc-building:3",
          "PROJECT_TEAM", "Linux, Git", "Rust");

        p(svc, techX, techY, "Ishaan Gupta", "🎮", "Computer Science", 2, "Lucknow", "H4",
          "Hindi, English",
          "Game dev in the making. Unity by day, Valorant by night. Immortal 2, if anyone's asking.",
          "game-development:5, valorant:5, programming:4, esports:3, digital-art:3",
          "PROJECT_TEAM, JUST_FRIENDS", "Unity", "Blender, 3D modelling");



        p(svc, techX, techY, "Sneha Nair", "🚀", "Computer Science", 2, "Thiruvananthapuram", "H1",
          "Malayalam, English",
          "Hackathon addict. Six this year, two wins. Always looking for a team that actually ships.",
          "hackathons:5, web-development:5, programming:4, ui-ux:3, entrepreneurship:3",
          "PROJECT_TEAM", "React", "Backend, System design");


        // ---------------- MUSIC ----------------
        p(svc, musicX, musicY, "Kabir Khan", "🎸", "Design", 3, "Mumbai", "H5",
          "Hindi, English, Urdu",
          "Guitarist. Mostly indie and blues. Looking for people to jam with — my band fell apart last sem.",
          "guitar:5, indie-music:5, music-production:4, singing:3, poetry:3",
          "JAM_SESSION, JUST_FRIENDS", "Guitar", "Music production, Ableton");

        p(svc, musicX, musicY, "Meera Joshi", "🎤", "Literature", 2, "Bhopal", "H6",
          "Hindi, English",
          "I sing. Hindustani training since I was six, but secretly I just want to do indie covers.",
          "singing:5, hindustani:5, indie-music:4, poetry:4, creative-writing:3",
          "JAM_SESSION", "Vocals, Hindustani basics", "Guitar");

        p(svc, musicX, musicY, "Arjun Pillai", "🥁", "Mechanical", 4, "Kochi", "H5",
          "Malayalam, English",
          "Drummer. Final year mech but honestly I'm here for the music room.",
          "drums:5, rock-music:5, metal-music:4, guitar:3, gym:3",
          "JAM_SESSION", "Drums", "");

        p(svc, musicX, musicY, "Tara Bose", "🎹", "Psychology", 3, "Kolkata", "H6",
          "Bengali, English, Hindi",
          "Piano since forever. Also studying how music affects the brain, which is convenient.",
          "piano:5, classical-music:4, psychology:4, lofi:3, reading:3",
          "JAM_SESSION, STUDY_PARTNER", "Piano, Music theory", "Music production");

        p(svc, musicX, musicY, "Zoya Ahmed", "🎧", "Computer Science", 2, "Delhi", "H6",
          "Hindi, English, Urdu",
          "I make beats at 2am instead of sleeping. FL Studio is my entire personality at this point.",
          "music-production:5, edm:5, lofi:4, dj:4, programming:3",
          "JAM_SESSION, PROJECT_TEAM", "FL Studio, Mixing", "Guitar, Music theory");


        // ---------------- SPORTS ----------------
        p(svc, sportX, sportY, "Rahul Verma", "🏏", "Sports Science", 4, "Kanpur", "H3",
          "Hindi, English",
          "College cricket captain. If there's a match happening anywhere on campus I'm probably in it.",
          "cricket:5, football:4, gym:4, athletics:3, fifa:3",
          "SPORTS_BUDDY, MENTEE", "Cricket, Fitness basics", "");

        p(svc, sportX, sportY, "Priya Menon", "⚽", "Physiotherapy", 3, "Kozhikode", "H1",
          "Malayalam, English, Hindi",
          "Football, left wing. Studying physio so I'm also the one taping everyone's ankles back together.",
          "football:5, athletics:4, gym:3, running:4, nutrition:3",
          "SPORTS_BUDDY", "Injury prevention", "");



        p(svc, sportX, sportY, "Divya Reddy", "🏃", "Biotechnology", 2, "Vijayawada", "H1",
          "Telugu, English, Hindi",
          "Long distance runner. 5am runs — ask me and I will absolutely drag you along.",
          "running:5, marathon:5, athletics:4, nutrition:3, yoga:3",
          "SPORTS_BUDDY", "Running form", "");

        p(svc, sportX, sportY, "Aryan Chauhan", "💪", "Mechanical", 3, "Indore", "H3",
          "Hindi, English",
          "Gym rat, powerlifting mostly. Currently arguing with everyone in the hostel about protein intake.",
          "gym:5, weightlifting:5, nutrition:4, cricket:3, motorcycles:3",
          "SPORTS_BUDDY", "Powerlifting", "");


        // ---------------- ARTS ----------------
        p(svc, artX, artY, "Anika Bhatt", "📷", "Design", 3, "Chandigarh", "H6",
          "Hindi, English, Punjabi",
          "Photographer, street and portrait mostly. I will make you pose, apologies in advance.",
          "photography:5, digital-art:4, graphic-design:4, travel:4, film-making:3",
          "PROJECT_TEAM, JUST_FRIENDS", "Photography, Lightroom", "Videography");

        p(svc, artX, artY, "Farhan Sheikh", "🎬", "Mass Communication", 4, "Hyderabad", "H5",
          "Urdu, Hindi, English",
          "Filmmaker. Four short films, one of them was actually decent. Currently deep in a Wong Kar-wai phase.",
          "film-making:5, videography:5, indie-films:5, photography:4, documentaries:4",
          "PROJECT_TEAM", "Video editing, Premiere", "Colour grading");


        p(svc, artX, artY, "Neel Chatterjee", "🎭", "Literature", 3, "Kolkata", "H5",
          "Bengali, English, Hindi",
          "Theatre society. I have played a tree, a king, and one very unconvincing detective.",
          "theatre:5, creative-writing:4, poetry:4, literature:4, standup-comedy:3",
          "PROJECT_TEAM, JUST_FRIENDS", "Acting, Stage presence", "Screenwriting");

        p(svc, artX, artY, "Sara DSouza", "✍️", "Journalism", 2, "Goa", "H6",
          "Konkani, English, Hindi",
          "I write for the campus magazine, and poetry when nobody's looking. Big fan of long walks and longer sentences.",
          "creative-writing:5, poetry:5, literature:4, journaling:4, reading:5",
          "JUST_FRIENDS", "Writing, Editing", "Photography");


        // ---------------- ACADEMICS ----------------





        // ---------------- OUTDOORS ----------------
        p(svc, outX, outY, "Varun Nambiar", "🏔️", "Environmental Science", 3, "Coorg", "H4",
          "Kannada, Malayalam, English",
          "Trekking club. Eight Himalayan treks done. Weekends are for mountains, not textbooks.",
          "trekking:5, camping:5, environment:5, photography:3, travel:4",
          "SPORTS_BUDDY, JUST_FRIENDS", "Trekking, Camping", "Photography");

        p(svc, outX, outY, "Naina Kapoor", "🧘", "Nutrition", 2, "Dehradun", "H1",
          "Hindi, English",
          "Got yoga-certified last summer. Teaching free classes on the lawn most mornings, come by.",
          "yoga:5, meditation:5, nutrition:5, running:3, veganism:4",
          "JUST_FRIENDS, MENTEE", "Yoga, Meditation", "");

        p(svc, outX, outY, "Siddharth Menon", "🚴", "Mechanical", 4, "Kochi", "H4",
          "Malayalam, English",
          "Cycled Manali to Leh last year. Bikes are the entire personality now, sorry.",
          "cycling:5, adventure-sports:4, trekking:4, travel:5, motorcycles:4",
          "SPORTS_BUDDY", "Cycling, Bike maintenance", "");



        // ---------------- FIRST-YEARS: the cold-start cases ----------------
        // Barely connected on purpose. Every structural algorithm returns nothing useful
        // for these three, so they are the honest test of content-based matching.
        p(svc, newX, newY, "Aarav Jain", "🎸", "Computer Science", 1, "Udaipur", "H7",
          "Hindi, English",
          "Just joined. I play guitar and write fairly bad poetry. Don't really know anyone here yet — would love to find people to jam with.",
          "guitar:4, indie-music:5, poetry:4, programming:3, lofi:4",
          "JAM_SESSION, JUST_FRIENDS", "", "Music production");

        p(svc, newX, newY, "Ira Bhattacharya", "📖", "Literature", 1, "Siliguri", "H7",
          "Bengali, Hindi, English",
          "First year, still figuring things out. I read a lot and I've been writing since school. Looking for people who like books.",
          "reading:5, creative-writing:5, poetry:4, literature:5, journaling:3",
          "JUST_FRIENDS, STUDY_PARTNER", "", "");

        p(svc, newX, newY, "Tanvi Deshmukh", "💻", "Computer Science", 1, "Nashik", "H7",
          "Marathi, Hindi, English",
          "First year CSE. Did some competitive programming in school, want to get into ML. Looking for seniors who can guide me a bit.",
          "programming:4, competitive-programming:4, machine-learning:3, mathematics:4, badminton:3",
          "MENTOR, STUDY_PARTNER", "", "Machine learning");

        wireConnections(svc);
        settle(svc, w, h);
    }

    // ================= the friendship graph =================

    private static void wireConnections(NetworkService svc) {
        // Tech
        link(svc, "Aditya Menon", "Rhea Sharma", 3.0);
        link(svc, "Aditya Menon", "Karthik Iyer", 2.0);
        link(svc, "Rhea Sharma", "Karthik Iyer", 1.0);
        link(svc, "Karthik Iyer", "Ishaan Gupta", 1.0);
        link(svc, "Sneha Nair", "Aditya Menon", 1.0);
        link(svc, "Sneha Nair", "Rhea Sharma", 2.0);

        // Music
        link(svc, "Kabir Khan", "Meera Joshi", 3.0);
        link(svc, "Kabir Khan", "Arjun Pillai", 2.5);
        link(svc, "Meera Joshi", "Tara Bose", 2.0);
        link(svc, "Tara Bose", "Zoya Ahmed", 1.0);
        link(svc, "Zoya Ahmed", "Kabir Khan", 2.0);

        // Sports. Rahul is the deliberate hub -- if he tops everybody's
        // recommendations, the popularity penalty is not working.
        link(svc, "Rahul Verma", "Priya Menon", 2.5);
        link(svc, "Rahul Verma", "Aryan Chauhan", 2.0);
        link(svc, "Rahul Verma", "Divya Reddy", 1.0);
        link(svc, "Rahul Verma", "Arjun Pillai", 1.0);
        link(svc, "Rahul Verma", "Varun Nambiar", 1.0);
        link(svc, "Rahul Verma", "Ishaan Gupta", 1.0);
        link(svc, "Priya Menon", "Divya Reddy", 2.0);

        // Arts
        link(svc, "Anika Bhatt", "Farhan Sheikh", 3.0);
        link(svc, "Farhan Sheikh", "Neel Chatterjee", 2.0);
        link(svc, "Neel Chatterjee", "Sara DSouza", 2.5);
        link(svc, "Anika Bhatt", "Neel Chatterjee", 1.0);

        // Outdoors
        link(svc, "Varun Nambiar", "Naina Kapoor", 2.0);
        link(svc, "Varun Nambiar", "Siddharth Menon", 3.0);
        link(svc, "Siddharth Menon", "Arjun Pillai", 1.0);

        // Cross-cluster bridges -- what makes betweenness and Louvain interesting
        link(svc, "Zoya Ahmed", "Ishaan Gupta", 1.0);      // music  <-> tech
        link(svc, "Anika Bhatt", "Varun Nambiar", 1.0);    // arts   <-> outdoors
        link(svc, "Arjun Pillai", "Aryan Chauhan", 1.0);   // music  <-> sports
        link(svc, "Priya Menon", "Naina Kapoor", 1.0);     // sports <-> outdoors
        link(svc, "Sara DSouza", "Meera Joshi", 1.0);      // arts   <-> music
        link(svc, "Tara Bose", "Sneha Nair", 1.0);         // music  <-> tech

        // The first-years know each other and nobody else. Aarav knows literally no one,
        // which is the cold-start case the whole product exists for.
        link(svc, "Ira Bhattacharya", "Tanvi Deshmukh", 1.0);
    }

    /**
     * Run the layout to rest before anyone sees it.
     * <p>
     * The physics engine has always existed but is off by default, so the graph opened in
     * whatever positions the seed happened to assign and stayed there — overlapping labels
     * and edges crossing for no reason, which reads as a mess rather than as a network.
     * Stepping the simulation a few hundred times here costs a few milliseconds and means
     * the first thing on screen is an already-untangled graph. Turning Physics on
     * afterwards then does what a user expects: it responds to <em>their</em> changes.
     */
    private static void settle(NetworkService svc, int width, int height) {
        for (int i = 0; i < 400; i++) svc.updatePhysics(width, height);
        // Leave every node at rest, or the first Physics toggle would jolt the layout
        // with velocity left over from a simulation the user never saw.
        for (Person p : svc.getAllUsers()) { p.dx = 0; p.dy = 0; }
    }

    // ================= builders =================

    /**
     * @param interests comma-separated {@code tagId:intensity}
     * @param intents   comma-separated {@link Intent} names
     */
    private static void p(NetworkService svc, int cx, int cy,
                          String name, String emoji, String major, int year,
                          String hometown, String hostel, String languages,
                          String bio, String interests, String intents,
                          String canTeach, String wantsToLearn) {

        // Tighter than the gap between anchors, so clusters stay visually distinct.
        int x = cx + JITTER.nextInt(130) - 65;
        int y = cy + JITTER.nextInt(110) - 55;
        svc.addUserAtPosition(name, x, y);

        Person person = svc.findUserByName(name);
        if (person == null) throw new IllegalStateException("Seed failed to add: " + name);

        person.setAvatarEmoji(emoji);
        person.setMajor(major);
        person.setYear(year);
        person.setHometown(hometown);
        person.setHostel(hostel);
        person.setBio(bio);
        for (String l : split(languages)) person.addLanguage(l);
        for (String s : split(canTeach)) person.addCanTeach(s);
        for (String s : split(wantsToLearn)) person.addWantsToLearn(s);

        InterestCatalog catalog = InterestCatalog.getDefault();
        for (String chunk : split(interests)) {
            String[] parts = chunk.split(":");
            String tagId = parts[0].trim();
            int intensity = parts.length > 1 ? parseIntOr(parts[1], Person.DEFAULT_INTENSITY)
                                             : Person.DEFAULT_INTENSITY;
            InterestCatalog.Resolution r = catalog.resolve(tagId);
            // Fail loudly: an unresolvable id here is a typo in this file, and silently
            // dropping it would quietly degrade every match involving this person.
            if (!r.found()) {
                throw new IllegalStateException("Seed uses unknown interest '" + tagId + "' for " + name);
            }
            person.addInterest(r.tag(), intensity);
        }

        for (String i : split(intents)) {
            person.addIntent(Intent.valueOf(i.trim().toUpperCase(Locale.ROOT)));
        }
    }

    private static void link(NetworkService svc, String a, String b, double weight) {
        Person pa = svc.findUserByName(a);
        Person pb = svc.findUserByName(b);
        if (pa == null || pb == null) {
            throw new IllegalStateException("Seed link references unknown person: " + a + " / " + b);
        }
        try {
            svc.addConnection(pa, pb);
            svc.setEdgeWeight(pa, pb, weight);
        } catch (Exception e) {
            throw new IllegalStateException("Duplicate seed link: " + a + " - " + b, e);
        }
    }

    private static String[] split(String csv) {
        if (csv == null || csv.isBlank()) return new String[0];
        String[] parts = csv.split(",");
        int n = 0;
        for (String s : parts) if (!s.isBlank()) n++;
        String[] out = new String[n];
        int i = 0;
        for (String s : parts) if (!s.isBlank()) out[i++] = s.trim();
        return out;
    }

    private static int parseIntOr(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return fallback; }
    }
}

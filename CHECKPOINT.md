# Campus Connect V2 — Checkpoint

**Last updated:** 2 August 2026
**Branch:** `v2/foundation` (6 commits ahead of `main`, working tree clean)
**Status:** Part A (Foundation) complete — M0 through M3. **Next up: M4, the matching engine.**

---

## 1. Read these in this order

| Doc | What it is |
|---|---|
| **CHECKPOINT.md** ← you are here | Where we are right now, and how to pick up |
| [implementation_plan.md](implementation_plan.md) | The build plan — 12 milestones, what each delivers |
| [campus_connect_v2_roadmap.md](campus_connect_v2_roadmap.md) | The vision — what we're building and why |
| [feature_roadmap.md](feature_roadmap.md) | The original 87-feature list (historical; §9 of the V2 roadmap maps it forward) |
| [README.md](README.md) | Build and run commands |

---

## 2. The one-paragraph version

We are turning a graph-drawing toy into a social platform. In V1 the graph *was* the
product: you placed dots and wired them together by hand. In V2 the **person** is the
product and the graph is what emerges when students describe themselves and the app helps
them find each other. The manual friend-graph stays exactly as it was — it is the trusted
substrate. Discovery and groups are layered on top of it.

The engine idea: keep **two graphs** over the same people. The **social graph** (real,
accepted connections) and the **affinity graph** (latent, computed from profiles, never
stored). A recommendation is simply *high affinity, no social edge yet.* That framing is
also the ML on-ramp — "add ML" later means "replace the affinity function with a learned
one" and nothing else changes.

---

## 3. What works right now

Run it and you get a **40-student campus** with real profiles instead of Alice..Yara.

| Layer | State |
|---|---|
| **Interest vocabulary** | 192 tags, 273 aliases, 4-stage resolver (`bball` → `basketball`) |
| **Person model** | bio, interests + 1–5 intensity, intents, clubs, skills, teach/learn, languages, hometown, hostel, courses, year, major |
| **Metrics** | PageRank, betweenness, closeness, degree — each in its own field, all normalised to [0,1] |
| **Persistence** | Gson, explicit DTOs, `schemaVersion: 2`, `LoadReport` for anything skipped |
| **Seed campus** | 40 students, 62 connections, 6 clusters, 3 cold-start first-years, 1 hub |
| **Physics** | Stable — speed-capped and lossy walls |
| **Everything from V1** | Still there: Dijkstra, BFS, Louvain, betweenness, bridges, cliques, heatmap, drag, physics |

**Not built yet:** matching engine, onboarding, profile UI, discovery feed, groups,
connection requests. That's M4 onward.

---

## 4. How to run

```bash
cd PorjectLink

# compile (bash / Git Bash)
javac -d out -cp "lib/flatlaf-3.5.jar;lib/flatlaf-intellij-themes-3.5.jar;lib/gson-2.11.0.jar" \
  $(find src -name "*.java")

# run the app
java -cp "out;lib/flatlaf-3.5.jar;lib/flatlaf-intellij-themes-3.5.jar;lib/gson-2.11.0.jar" \
  CampusConnect.main.Main
```

In VS Code, F5 works — `.vscode/launch.json` is already configured and the
`referencedLibraries` glob picks up the Gson jar automatically.

### The three harnesses — run these after any change

```bash
java -cp out CampusConnect.dev.InterestCatalogHarness              # 36 checks
java -cp "out;lib/gson-2.11.0.jar" CampusConnect.dev.SeedHarness   # 32 checks
java -cp "out;lib/gson-2.11.0.jar" CampusConnect.dev.PhysicsHarness #  8 checks
```

**All 76 should pass.** These are the regression net — there is no JUnit in this project,
and these have already caught three real bugs that reading the code did not.

---

## 5. Where things live

```
PorjectLink/src/CampusConnect/
├── domain/       Person, InterestTag, InterestCatalog, Category, Intent,
│                 NodeMetrics, Edge
├── service/      NetworkService          ← graph + physics + stats (still doing a lot)
├── persist/      GraphIO, CampusSeed, InterestCatalogLoader
├── algorithm/    PageRank, CentralityMetrics, CommunityDetection, GraphAnalyzer,
│                 DijkstraAlgorithm, FriendRecommender, GraphGenerator
├── ui/           MainFrame, NetworkCanvas, StatsPanel, AlgorithmVisualizer
├── dev/          InterestCatalogHarness, SeedHarness, PhysicsHarness
└── main/         Main
```

**Hard rule, in force since M1:** nothing in `domain/`, `service/`, `algorithm/` or
`persist/` may import `javax.swing`. That single constraint is what keeps a web front-end
or a Python ML sidecar a *port* rather than a rewrite.

**Files worth knowing:**
- `domain/InterestCatalog.java` — the 192-tag vocabulary and resolver. Highest-leverage
  file in the project.
- `persist/CampusSeed.java` — the 40 students. Shaped deliberately, see §7.
- `service/NetworkService.java` — graph store *and* physics *and* stats. Will need
  splitting eventually; not urgent.

---

## 6. Decisions made, and why

Keep these — the reasoning is not recoverable from the code alone.

| Decision | Why |
|---|---|
| **Interest taxonomy is compiled Java, not `resources/interests.json`** | No build system, so nothing copies resources onto the classpath and `getResourceAsStream` fails. Compiled-in also means a bad category name fails at build time. `interests-custom.json` covers runtime extension. |
| **Dropped `LayoutState`** (planned as M2b) | Moving `x/y/dx/dy` out of `Person` touches ~25 call sites and unblocks no V2 feature. Revisit only for a non-Swing front-end. |
| **Interests are one `Map<InterestTag,Integer>`** | Not a `Set` plus a parallel intensity map. Two structures that must agree eventually won't, and the resulting skew in similarity would be invisible. |
| **Persistence uses explicit DTOs, not Gson reflection over `Person`** | Reflection would persist `NodeMetrics` (stale immediately) and `dx/dy` (meaningless across sessions), and inline every alias so one catalog edit invalidates every save. |
| **Canonical tag *ids* persist, never labels** | Labels get reworded; ids are the stable contract with the file. |
| **The seed throws on an unknown tag id** | That's a typo in the seed file. Skipping it silently would degrade every match involving that person. |
| **`InterestCatalog` throws on alias collisions** | A hijacked alias routes an interest to the wrong tag *permanently* and is invisible in a 192-row table. Better to refuse to start. |
| **Gson over hand-rolled JSON** | Your call at the M3 planning step. Nested profiles would have broken the old parser. |

---

## 7. The seed campus is shaped on purpose

`CampusSeed` is hand-authored rather than random so that **specific answers are checkable
by eye** at M4. Do not "clean it up" — the messiness is the point.

- **Aarav Jain — zero connections.** Guitar, indie, poetry, programming, lo-fi. Every
  structural algorithm returns nothing for him. He is the honest test of content-based
  matching, and the right answer is **Kabir Khan** (guitar, indie, poetry).
- **Ira Bhattacharya, Tanvi Deshmukh** — one connection each, to each other. First-years
  who know nobody else.
- **Rahul Verma — degree 7, the hub.** If he appears in *everyone's* recommendations at
  M4, the popularity penalty is not working.
- **Complementary pairs** — Kabir teaches guitar; Meera and Zoya want to learn it.
- **Cross-cluster bridges** — twelve of them, so betweenness and Louvain have something
  real to find.

Where the naive baseline already lands, from `SeedHarness`:

```
Aarav (0 friends): Guitar, Indie, Poetry, Programming, Lo-fi
   3 shared  Kabir Khan      Guitar, Indie, Poetry
   2 shared  Meera Joshi     Indie, Poetry
   2 shared  Zoya Ahmed      Programming, Lo-fi
   1 shared  Aditya Menon    Programming
   1 shared  Rhea Sharma     Programming
```

Right answer on top, with no structural signal at all. It also shows exactly why M4 needs
**IDF weighting**: Aditya and Rhea only tie because *Programming* is common across the
tech cluster. Once rare tags outweigh common ones, Meera (indie + poetry, both rare)
should pull clearly ahead of three people who merely also code.

---

## 8. Bugs found and fixed so far

All three were found by harnesses, not by reading code.

1. **Load from JSON silently discarded the file.** `loadGraph()` called `resetView()`,
   which cleared the graph and reloaded the demo. Save worked; load never did.
2. **Nodes vanished permanently.** Divide-by-zero → `NaN` in the spring force. The first
   fix was incomplete — clamping the distance stopped the NaN but left a `(0,0)` force
   direction, so coincident nodes stayed fused. Repulsion now breaks the tie with a
   random unit vector.
3. **The layout diverged on 40 nodes.** Uncapped `1/dist²` repulsion plus perfectly
   elastic wall bounces; energy reached 2.3×10¹². Latent in V1, triggered by the denser
   seed. Fixed with a speed cap and lossy walls.

Plus a UX bug fixed along the way: all four centrality metrics wrote to one shared `rank`
field, so the heatmap showed whichever ran last while still being labelled PageRank.

---

## 9. Pick up here → M4, the matching engine

**Build it headless. No UI.** This is deliberate and it is the most important checkpoint
in the plan: we read the console output together and ask, person by person, *"would I
actually want to meet them?"* Wrong weights get fixed in minutes. Build the UI first and
you are debugging Swing layout while trying to judge match quality — two hard problems
tangled into one.

**Files to create:**

```
algorithm/similarity/TfIdf.java              bio text vectorizer + cosine
algorithm/similarity/InterestSimilarity.java IDF-weighted Jaccard over tags
algorithm/similarity/SimilarityEngine.java   context, intent, teach/learn
service/RecommendationService.java           the blend, cold start, popularity penalty
service/ExplanationBuilder.java              the human sentence
dev/MatchingHarness.java                     top-5 for all 40 students
```

**The score:**

```
affinity(u,v) = w1·interestSim + w2·bioSim + w3·contextSim
              + w4·structuralSim + w5·intentMatch − w6·popularityPenalty
```

**IDF weighting** — "we both like Carnatic fusion" must outrank "we both like music":

```
idf(t)           = log( N / (1 + peopleWithTag(t)) )
interestSim(u,v) = Σ_{t ∈ u∩v} idf(t)  /  Σ_{t ∈ u∪v} idf(t)
```

**Three things that are not optional:**

- **Cold-start reweighting.** When `degree(u) < 3`, `structuralSim` is 0 and carries no
  information. Renormalise so the content terms carry the whole score. This is Aarav, and
  he is the user the product exists for.
- **Popularity penalty.** Without it every recommendation converges on the same three
  hubs. Rahul is in the seed specifically to catch this.
- **Explanations.** Never show a bare score. *"Suggested because you both play guitar and
  are into indie music."* ~30 lines over data already computed, and it makes the
  recommender debuggable.

**Reuse, don't rewrite:** `algorithm/FriendRecommender.java` already implements
Adamic-Adar and Jaccard over the friend graph. That is `structuralSim` — wire it in.

**Definition of done:** `MatchingHarness` prints top-5 with explanations for all 40
students, Kabir is Aarav's top match, and Rahul does *not* dominate everyone's list.

---

## 10. After M4

| Milestone | Delivers |
|---|---|
| **M5** | `AppSession` (the concept of *you*), onboarding wizard, profile card |
| **M6** | **Similarity-to-me heatmap** — the whole campus coloured by how well they match you |
| **M7** | Discovery feed, connect/dismiss, ghost edges, serendipity slider |
| **M8** | Menu reframe — "Your Circles", "Squads", "Who's Isolated" |
| **M9–M11** | Groups, connection requests, archetypes, warm intro paths |
| **Later** | ML (link prediction → learned ranking → embeddings), then RL bandits |

Roughly **6 days from here to the full product loop** (M4–M7).

---

## 11. Git

```
main
 └── v2/foundation   ← you are here, 6 commits, clean
      57b0931  Fix force-directed layout diverging on the 40-student campus
      8434035  M3: Gson persistence and a 40-student seeded campus
      747532c  M2d: rename UserNode to Person
      ddc895e  M2a/M2c: separate computed metrics, add the profile
      b5e8511  M1: interest taxonomy and resolver
      5ee1d6b  M0: stabilize foundation before V2 work
```

Nothing is pushed to `origin` yet. One branch per part, one commit per milestone, working
app at every commit — so a bad milestone costs a milestone, never the project.

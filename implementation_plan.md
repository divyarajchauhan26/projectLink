# Campus Connect V2 — Implementation Plan

> Companion to [campus_connect_v2_roadmap.md](campus_connect_v2_roadmap.md).
> That doc is the **what & why**. This one is the **how & when**.

**Decisions locked:** Gson for serialization · Claude writes code, you review and steer at each checkpoint.

---

## The Prime Directive

> **The app must compile and run after every single milestone.**

No big-bang rewrite. We are changing the core domain model of a working application, and the
only safe way to do that is in small atomic steps, each ending with a runnable app you can
click around in. If a milestone can't end in a working app, it's too big and gets split.

Every milestone ends with a **✅ Checkpoint** — you run it, you eyeball it, you tell me
go/no-go before I move on.

---

## Target Package Structure

We grow into this — we don't create it all upfront.

```
CampusConnect/
├── main/         Main.java
├── app/          AppSession           ← who am I, what mode
├── domain/       Person, Connection, InterestTag, Category,
│                 Intent, NodeMetrics, LayoutState
├── service/      GraphStore, ProfileService, PhysicsEngine,
│                 MetricsService, RecommendationService
├── algorithm/
│   ├── graph/      ← existing 7 files move here, unchanged
│   ├── similarity/ TfIdf, InterestSimilarity, SimilarityEngine
│   └── ml/         ← Phase 5, empty for now
├── persist/      GraphIO, InterestCatalog, EventLog
├── ui/           MainFrame, NetworkCanvas, StatsPanel,
│                 OnboardingWizard, ProfileCard, DiscoveryPanel,
│                 InterestChipPicker
├── dev/          MatchingHarness   ← headless test rigs
└── resources/    interests.json, campus_seed.json
```

**Hard rule, enforced from day one:** nothing in `domain/`, `service/`, `algorithm/`, or
`persist/` may import `javax.swing`. That single constraint is what makes a web front-end
or a Python ML sidecar a *port* rather than a *rewrite* later.

---

## Part A — Foundation (Milestones 0–3)

Boring, unglamorous, and every single thing after it depends on getting this right.

### ✅ M0 — Stabilize *(~30 min)*

Fix the bugs before building on them.

| # | Fix | File |
|---|---|---|
| 0.1 | `loadGraph()` calls `resetView()`, which wipes the file you just loaded | `ui/MainFrame.java` |
| 0.2 | Divide-by-zero → `NaN` in spring force; nodes vanish permanently | `service/NetworkService.java` |
| 0.3 | `VIEW` mode unreachable — add an "Inspect" toggle, selected by default | `ui/MainFrame.java` |
| 0.4 | Rename "Reset View" → "Reset to Demo Graph" (it's destructive — label it honestly) | `ui/MainFrame.java` |
| 0.5 | `loadDefaultGraph` swallows exceptions into `printStackTrace` → surface them | `ui/MainFrame.java` |

**Checkpoint:** app runs · Save→Load actually round-trips · nodes never vanish under physics.

---

### M1 — Interest Taxonomy *(~1 day)*

The highest-leverage work in the entire project. Free-text interests make every downstream
feature — matching, heatmap, groups, all of ML — statistically worthless.

**New files**
- `domain/Category.java` — enum, 12 values (SPORTS, MUSIC, GAMING, ACADEMICS, TECH, ARTS, FOOD, FILM_TV, FITNESS, VOLUNTEERING, OUTDOORS, OTHER)
- `domain/InterestTag.java` — `id` · `label` · `category` · `aliases`
- `domain/Intent.java` — enum, 8 values (STUDY_PARTNER, PROJECT_TEAM, ROOMMATE, SPORTS_BUDDY, JAM_SESSION, MENTOR, MENTEE, JUST_FRIENDS)
- `persist/InterestCatalog.java` — load, index by id + alias, 4-stage resolver
- `resources/interests.json` — **~180 curated tags**

**The resolver, in order:** exact id → alias hit → normalized (lowercase, strip
punctuation/spaces) → fuzzy (Levenshtein ≤ 2) → offer "create new tag".

So `"bball"` · `"Basket Ball"` · `"hoops"` · `"BASKETBALL"` all collapse to one tag.

**Checkpoint:** I run a throwaway `main()` throwing 30 messy strings at the resolver; we
read the output together and confirm it collapses correctly.

---

### M2 — The Person Model *(~1 day)*

Done as **four atomic sub-steps**, each recompiling clean, so we're never mid-rewrite.

| Step | Change | Risk |
|---|---|---|
| 2a | Add `NodeMetrics` (rank, 3 centralities, communityId, archetype) — keyed by person id | none, additive |
| 2b | Add `LayoutState` (x, y, dx, dy) owned by `PhysicsEngine`; physics leaves the domain | low |
| 2c | Expand `UserNode` with profile fields (bio, interests, intensity, intent, clubs, skills, languages, hometown, hostel, courses, availability) | none, additive |
| 2d | **Rename `UserNode` → `Person`** across all 16 files — one atomic mechanical pass | medium, but compiler-verified |

> **2a fixes a live bug for free:** all four centrality metrics currently overwrite the same
> `rank` field, so "Top Influencers (PageRank)" silently shows whatever you ran last.
> Separate fields, separate truths — and it's what makes the multi-mode heatmap possible in M6.

**Checkpoint:** app looks and behaves *identically* to M0 — but the model underneath is V2-ready.

---

### M3 — Gson + Real Seed Data *(~1 day)*

- Add `gson-2.11.0.jar` → `PorjectLink/lib/`, update `.vscode/settings.json` + README
- New `persist/GraphIO.java` replaces `GraphPersistence.java` (delete ~180 lines of hand-rolled parser)
- Schema versioning (`"schemaVersion": 2`) from the start — you *will* change the shape later
- **Generate `resources/campus_seed.json`: 40 students with genuinely realistic profiles** —
  varied majors, years, hometowns, languages, overlapping-but-not-identical interests,
  believable bios, and a friendship graph with real community structure

**Why the seed data matters more than it sounds:** you cannot evaluate a recommender against
25 nodes named Alice..Yara with no profiles. M4 is meaningless without this. I'll hand-author
it so the clusters are *deliberately* interesting — some obvious matches, some subtle ones,
a couple of isolated students, and a few cross-cluster bridges.

**Checkpoint:** save → quit → relaunch → load → every profile field survives intact.

---

## Part B — The Product (Milestones 4–7)

This is where your idea becomes real.

### 🎯 M4 — Matching Engine, Headless *(~1 day)* ← **THE PROOF POINT**

Built and validated with **zero UI**. This is deliberate.

**New files**
- `algorithm/similarity/TfIdf.java` — bio text vectorizer + cosine
- `algorithm/similarity/InterestSimilarity.java` — IDF-weighted Jaccard
- `algorithm/similarity/SimilarityEngine.java` — context, intent, complementary (teach/learn)
- `service/RecommendationService.java` — the hybrid blend, cold-start reweighting, popularity penalty, diversity constraint
- `service/ExplanationBuilder.java` — the human sentence
- `dev/MatchingHarness.java` — headless `main()`, prints top-5 for all 40 seeded students

```
affinity(u,v) = w₁·interestSim + w₂·bioSim + w₃·contextSim
              + w₄·structuralSim + w₅·intentMatch − w₆·popularityPenalty
```

with `interestSim` IDF-weighted so *"we both like Carnatic fusion"* outranks *"we both like music"*,
and cold-start reweighting when `degree(u) < 3` so a brand-new student still gets great matches.

> **Why headless first:** we read the console output side by side and ask *"would I actually
> want to meet this person?"* If the answer is no, we tune weights in minutes. If we'd built
> the UI first, we'd be debugging Swing layout while trying to judge match quality — two hard
> problems tangled together. **This is the single most important checkpoint in the plan.**

**Checkpoint:** we read 40 students × top-5 matches together. Go/no-go on the whole thesis.

---

### M5 — Session, Onboarding & Profile UI *(~2 days)*

- `app/AppSession.java` — the concept of **you**. V1 has no notion of a current user; a social app fundamentally requires one.
- `ui/OnboardingWizard.java` — 4 steps: *Basics → Interests → About You → Looking For*
- `ui/InterestChipPicker.java` — autocomplete + chips, wired to `InterestCatalog`
- `ui/ProfileCard.java` — replaces the raw `JTextArea` dump with a real card
- Profile editor (reuses the wizard panels)

**Design targets:** under 90 seconds, everything skippable, autocomplete everywhere — and the
instant they hit Finish, **show them 5 people they should meet.** Never drop a new user onto
an empty canvas.

**Checkpoint:** create yourself as a real profile, see your card, see your first 5 suggestions.

---

### 🔥 M6 — The Similarity Heatmap *(~half day)*

Deliberately scheduled early because it's your favourite feature and by M6 it's nearly free.

- `service/MetricsService.java` — multi-mode metric provider
- Heatmap becomes a **mode dropdown**, not a boolean:

| Mode | Colours campus by |
|---|---|
| **Similarity to Me** ⭐ | how well each person matches *you* |
| Influence | PageRank (the current behaviour) |
| Mutual Friends | shared connections with you |
| Isolation Risk | who the network is failing |

Plus a proper colour legend — the current heatmap has none, so the gradient is unreadable.

**Checkpoint:** your entire college, coloured by compatibility with you. This is the money shot.

---

### M7 — Discovery Feed *(~2 days)*

- `ui/DiscoveryPanel.java` — scrollable cards: avatar, name, year/major, interest chips, **the explanation sentence**, `[Connect] [Not interested]`
- Dismiss-with-reason → `persist/EventLog.java` *(this is your Phase-5 training data — start collecting on day one)*
- Ghost edges: dashed lines from you to your top-N matches
- **Serendipity slider**: *Similar to me ←→ People I'd never meet*
- Daily cap of 3 suggestions — scarcity produces considered decisions and clean feedback
- 30-day cooldown on dismissed people

**Checkpoint:** the complete core loop — onboard → get suggestions → connect → graph grows.

---

## Part C — Depth (Milestones 8–11)

### M8 — Menu Reframe *(~1 day)*
Cut waypoint routing, A*, cycle detection from the UI. Then rewrite what remains in human
language: **Your Circles** (Louvain + auto-naming by top-IDF shared interest) · **Squads**
(cliques) · **Who's Isolated** (connected components — currently written but unused) ·
**Your Bridges** (betweenness as a sentence). Almost no code deleted; the menu goes from
8 academic items to 4 human ones.

### M9 — Groups *(~2–3 days)*
Auto-detected squads from Bron-Kerbosch · bipartite person↔interest projection ·
"groups you'd fit into" · group health metric (induced-subgraph density = cliquey vs welcoming).

### M10 — Connection Requests *(~1–2 days)*
`PENDING → ACCEPTED/DECLINED` state machine · typed connections · derived strength ·
icebreakers · the `origin` field that measures whether the recommender actually works.

### M11 — Insight Features *(~2 days)*
Network archetypes (Connector/Specialist/Explorer/Bridge/Newcomer/Anchor) · warm intro paths
(finally makes Dijkstra meaningful) · isolation intervention (reverse-suggestion — the
pro-social feature) · personal analytics dashboard.

---

## Part D — Intelligence (Phase 5–6 of the roadmap)

Not scheduled until M7 has been live long enough to collect real accept/dismiss data.
Feature pipeline → hand-written logistic regression link prediction → learned ranking weights
→ Node2Vec → contextual bandit on the serendipity axis.

**Non-negotiable:** M4's heuristic is the baseline. A model that can't beat it doesn't ship.

---

## Timeline

| Part | Milestones | Effort | Delivers |
|---|---|---|---|
| **A — Foundation** | M0–M3 | ~3–4 days | Nothing visible. Everything depends on it. |
| **B — The Product** | M4–M7 | ~6 days | **Your entire idea, working** |
| **C — Depth** | M8–M11 | ~7 days | The features that make it memorable |
| **D — Intelligence** | Phase 5–6 | ~4–6 weeks | The features that make it *impressive* |

**~10 working days to a demoable product.** Part A feels slow and produces nothing you can
show — that's expected and it's the correct trade. Part B moves fast *because* Part A was done
properly.

---

## Risk Register

| Risk | Mitigation |
|---|---|
| M2d rename touches 16 files | One atomic pass, compiler-verified, committed alone so it's trivially revertable |
| Match quality is bad | **That's what M4's headless checkpoint is for** — found in an hour, not after 3 days of UI work |
| Swing fights card-based UI | Accepted cost. Clean service layer keeps a web port open. |
| Scope creep | Milestones are ordered by dependency. We don't skip ahead — M9's groups need M4's similarity. |
| Seed data too clean/unrealistic | Hand-authored with deliberate messiness: isolated students, ambiguous matches, cross-cluster bridges |

---

## Git Strategy

One branch per part, one commit per milestone, with a working app at every commit.

```
main
 └── v2/foundation   M0 → M1 → M2 → M3
 └── v2/product      M4 → M5 → M6 → M7
 └── v2/depth        M8 → M11
```

If a milestone goes wrong, you lose one milestone — never the project.

---

## Starting Now

**M0 — five small fixes, ~30 minutes.** Then you run it and confirm before I touch M1.

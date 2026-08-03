# Talking about this project

Preparation notes. An interviewer will not read the source — they will pick something from
the README and push on it until you either explain it well or run out of answer. The
questions below are the ones most likely to come up, ordered roughly by how often.

**Two things worth doing before an interview:**

1. Run `./run.ps1` and click through everything. Being able to say "let me show you" beats
   any explanation.
2. Open `RecommendationService.score()` and `InterestSimilarity.similarity()`. Those two
   methods are the heart of the project and the most likely target for follow-ups.

---

## The 60-second version

> It started as a graph visualiser — nodes and edges, BFS between them. The problem was
> that every node was interchangeable, so every edge was arbitrary, so the graph didn't
> represent anything.
>
> I rebuilt it around the idea that a node is a *person* with interests, a bio and a
> history. Once that's true, the algorithms mean something: a shortest path becomes a chain
> of introductions, a community becomes a friend group, an isolated node becomes a student
> nobody has found.
>
> The core is a recommender. It maintains two graphs over the same people — the real social
> graph, and a computed "affinity" graph of how well any two people would get on. A
> suggestion is just: high affinity, no edge yet. And every suggestion explains itself in a
> sentence, which is both a product feature and how I debug the thing.

Then stop. Let them pick where to go.

---

## About the recommender

### "How does the matching actually work?"

Six weighted signals, blended:

| Signal | What it measures |
|---|---|
| `interestSim` | Rarity-weighted overlap of interest tags |
| `bioSim` | TF-IDF cosine over free-text bios |
| `contextSim` | Same course, year, hostel, home town, language |
| `structuralSim` | Adamic-Adar over mutual friends |
| `intentMatch` | Both looking for the same thing |
| `teachLearn` | One can teach what the other wants to learn |

Minus a popularity penalty. Weights live in `RecommendationService.Weights.defaults()`.

### "Why IDF weighting? Why not just count shared interests?" ⭐

*The single most likely follow-up. Know this cold.*

> Because half of campus likes music. Two people both liking "music" tells you almost
> nothing; two people who both do competitive programming have told you something real.
> Plain overlap treats those identically.
>
> So each tag is weighted by inverse document frequency — `log(N / (1 + peopleWithTag))` —
> the same idea search engines use for words. Rare shared interests dominate the score.
> I also fold in a 1–5 intensity per person, so "plays every day" outranks "tried it once".
> The result is a weighted Jaccard.

**If they push:** "What if everyone has completely unique interests?" Then every tag has
high IDF and the scores compress — the weighting stops discriminating. On a real campus
that doesn't happen, but the honest answer is that IDF assumes a reasonably shared
vocabulary, which is exactly why the interest list is a controlled 192-tag catalog rather
than free text.

### "What happens to a brand-new user with no connections?" ⭐

*The second most likely. This is the cold-start problem and they'll want the name.*

> That's the case the product exists for, and it's the case where most of the algorithms
> are useless — a first-year has no edges, so every structural metric returns zero.
>
> The subtle part is that leaving the structural term in place makes it *actively harmful*.
> It contributes nothing but still consumes its share of the weight, so every candidate
> scores low and the ranking turns to noise. Below three connections I redistribute that
> weight across the profile terms instead.
>
> In the demo, Aarav has literally zero friends and four of his top five suggestions are
> musicians — reached from a bio and five interest tags.

### "How do you stop the most popular person topping everyone's list?"

> A popularity penalty — but the first version overcorrected, which is the more interesting
> half of the answer.
>
> I started with `degree / maxDegree`. That charged every ordinary person a fee for having
> friends, and hit the most-connected person so hard he vanished from all 24 lists. That's
> the same failure inverted — instead of hubs dominating, isolated people did. Now only
> *above-average* degree is penalised, ramping to 1 at the most connected person.

### "How do you know the recommendations are any good?"

Be honest here — this is a weak spot and pretending otherwise reads badly.

> Right now I can't measure quality properly, because there are no real users. What I have
> is:
>
> - **Checkable answers.** The demo campus is hand-authored so specific results are
>   verifiable by eye — the guitarist with no friends should surface the other guitarist.
> - **Property tests.** Nobody is suggested to themselves or to existing friends, everyone
>   gets suggestions, no single person dominates.
> - **Effect measurements.** The popularity penalty is verified by A/B — mean degree of
>   everybody suggested, penalty on versus off.
>
> The real measure would be acceptance rate, which is why `EventLog` has been recording
> every suggestion shown, accepted and dismissed from the day the feed existed. That's also
> the training data for a learned ranker.

---

## About the graph algorithms

### "Why Louvain for communities and not k-means?"

> k-means needs you to pick k and works on points in a metric space. I have a graph and no
> idea how many friend groups exist. Louvain optimises modularity — how much denser the
> connections inside groups are than you'd expect by chance — and finds the number of
> communities itself.

**If they push on the implementation:** it's the local-moving phase only, not the full
multi-level version with graph aggregation. On 24 people that's fine; at scale it would
produce more, smaller communities than the real algorithm. Say so.

### "What does betweenness give you that degree doesn't?" ⭐

> Degree tells you who has the most friends. Betweenness tells you who sits *between*
> groups — how often someone lies on the shortest path between two other people.
>
> They're different people. The cricket captain has the highest degree, but the person who
> holds the network together is whoever connects the music crowd to the sports crowd, and
> they might only have four friends. That's what "The Bridge" archetype is.

Brandes' algorithm, O(VE) — worth naming.

### "Why is Dijkstra there? Isn't BFS enough for a social graph?" ⭐

*Best question to get, because the answer is a bug you found.*

> Because edges are weighted by friendship strength, and I wanted the *warmest* chain of
> introductions rather than the shortest.
>
> The catch is that strength means higher-is-closer, and shortest-path wants a cost, where
> lower-is-better. The original code ran Dijkstra directly on the raw weights — which means
> it was systematically preferring the *weakest* links in the graph and calling that a
> shortest path.
>
> Traversing an edge now costs `1/strength`. A four-step chain through close friends is a
> far better route to an introduction than a two-step chain through people who barely
> speak.

### "Why Adamic-Adar rather than just counting mutual friends?"

> Same rarity argument as IDF. A mutual friend who knows everybody is weak evidence — they
> know everybody. A mutual friend who knows six people is strong evidence. Adamic-Adar sums
> `1/log(degree)` over the common neighbours, so it discounts the socially ubiquitous.

### "What's Bron-Kerbosch doing here?"

> Finding maximal cliques — sets where everybody knows everybody. It's the "squads" feature.
> A clique of five isn't an abstraction, it's five people who genuinely all know each other,
> which is what a friend group is. It's also how the app proposes groups instead of asking
> someone to build one from nothing.

---

## About the architecture

### "Walk me through the structure."

> Five layers. `domain` is the data — Person, InterestTag, Group. `service` is the logic —
> the graph store, the recommender, the insight layer. `algorithm` is the pure maths, split
> into graph algorithms and similarity. `persist` handles JSON and the seed data. `ui` is
> Swing.
>
> The rule I enforced is that nothing below `ui` imports `javax.swing`. The engine has no
> idea a UI exists.

### "Why does that matter?"

> Two reasons. It's why I could build and validate the entire matching engine with no
> interface at all — which mattered, because judging match quality and debugging Swing
> layout at the same time means never knowing which one is broken. And it's what would make
> a web front-end a port rather than a rewrite.

### "Why a controlled vocabulary for interests instead of letting people type?" ⭐

> Because `bball`, `Basket Ball`, `hoops` and `BASKETBALL` are four strings and one
> interest. Store them raw and two people who both play basketball look like they have
> nothing in common — every similarity score is computed over noise, and any model trained
> on it later learns nothing.
>
> So there's a 192-tag catalog with 273 aliases and a four-stage resolver: exact id, alias,
> normalised, then bounded fuzzy match. The data is clean by construction rather than by
> cleanup.

**Nice detail to have ready:** the fuzzy tolerance scales with word length and is zero
below five characters, because "chess" and "chest" are one edit apart and mean entirely
different things.

### "Why hand-write the seed data?"

> Because a recommender can't be evaluated against 25 nodes named Alice..Yara with no
> profiles — every suggestion looks equally arbitrary, so you can't tell a working engine
> from a broken one. The 24 students are shaped so specific answers are checkable: one with
> zero connections, one deliberate hub, complementary teach/learn pairs.

---

## About testing

### "How did you test it? I don't see JUnit."

> Ten headless harnesses, 255 checks, one command. No JUnit because there's no build system
> — I wanted the project to have no setup step — so the harnesses are plain `main` methods
> that assert and exit non-zero.
>
> The more interesting part is *what* they check.

### "Give me an example of a test that wasn't useful." ⭐

*Have this ready. It's the strongest thing you can say about your own testing.*

> I had a check that the most-connected person doesn't dominate everyone's suggestions. It
> passed — but for entirely the wrong reason. He was already friends with everyone he'd
> match with, so he was filtered out as an existing friend whether or not the popularity
> penalty existed. The check would have passed with the feature deleted.
>
> I replaced it with an A/B measurement: mean degree of everybody suggested, penalty on
> versus off. 3.085 down to 2.810. That tests the mechanism instead of a coincidence.
>
> Same category: I had thresholds like "25+ distinct people appear across all lists", which
> silently encoded that the campus had 40 people and broke the moment it didn't. Those are
> relative to population now.

### "What bugs did the tests actually catch?"

> Four that reading the code didn't:
>
> - A force-directed layout that diverged to 10¹² kinetic energy on a denser graph — the
>   repulsion term was uncapped and wall bounces were perfectly elastic.
> - A divide-by-zero when two connected nodes landed on identical coordinates, producing NaN
>   coordinates and permanently invisible nodes. My first fix was incomplete — clamping the
>   distance stopped the NaN but left a zero-length direction vector, so they stayed fused.
> - Save-then-load silently discarding the file, because load called a reset that reloaded
>   the demo graph.
> - Both recommender biases described above.

---

## Scale and complexity

### "What's the complexity of a recommendation?"

> O(n) per user against the campus, with each pair costing O(|tags| + |bio terms| +
> degree). Computing everyone's suggestions is O(n²).
>
> Betweenness is the expensive one — Brandes is O(VE). Bron-Kerbosch is worst-case
> exponential, which is fine at this size and would need a degeneracy ordering beyond it.

### "What breaks at 10,000 students?" ⭐

Be specific. Vague answers here are obvious.

> Three things, in order:
>
> 1. **The O(n²) scoring.** Fix is an inverted index — bucket candidates by shared interest
>    tag and only score people who share at least one, instead of scoring the whole campus.
> 2. **Betweenness at O(VE).** Either sample sources for an approximation, or compute it
>    offline rather than on a menu click.
> 3. **The physics loop is O(n²) per frame.** That's Barnes-Hut territory — a quadtree
>    approximating distant clusters as single bodies, O(n log n).
>
> Also the similarity model is rebuilt from scratch whenever the graph changes, which is
> fine at 24 people and wasteful at 10,000. It'd need incremental updates.

---

## The uncomfortable questions

Prepare these. Being caught without an answer is worse than the weakness itself.

### "Isn't this just a weighted sum? Where's the machine learning?"

> Correct, and deliberately so. The weights are hand-tuned.
>
> That's the baseline, not the destination. The reason it's hand-tuned first is that a
> learned model needs something to beat — without a baseline, "we added machine learning"
> is unfalsifiable. And it needs labelled data, which is why the event log records every
> suggestion shown, accepted and dismissed with a reason.
>
> The next step is logistic regression for link prediction over pair features, then using
> it to replace the weights. The interface doesn't change — it's one method.

### "How would you validate the recommendations with real users?"

> Acceptance rate as the headline metric — the event log already computes it. Then
> precision@k against connections that actually formed. For anything causal I'd need a
> holdout: withhold some existing edges, see whether the model predicts them.
>
> I'd also want to watch for filter-bubble effects, which is partly why the serendipity
> slider exists — it's the explore/exploit tradeoff exposed as a user control.

### "Why Swing? Why not a web app?"

Don't be defensive.

> It's what the original project used, and rewriting the front-end wasn't where the
> interesting problems were. The trade-off I made instead was keeping the engine completely
> free of UI dependencies, so a web front-end would be a port.
>
> If I were starting fresh it'd be a web app — the graph rendering alone would be far
> better in D3 or Canvas, and the card-based UI is genuinely painful in Swing.

### "What would you do differently?"

> `NetworkService` does too much — it's the graph store, the physics engine and the stats
> calculator in one class. That should have been split early; it got harder the longer I
> left it.
>
> And I'd have built the interest catalog first. I originally stored interests as free
> strings and the matching was meaningless until I fixed it. Everything downstream depends
> on that data being clean, so it should have been the first thing, not a correction.

### "What are you most pleased with?"

> The explanations. "Suggested because you're both into guitar and indie" costs almost
> nothing — every fact in it was already computed to produce the score — but it does two
> jobs. Users get a reason to actually go and say hello, and the recommender becomes
> debuggable by eye: a bad suggestion with a stated reason tells you immediately which
> signal misfired.

---

## Quick reference

Numbers worth having ready:

| | |
|---|---|
| Interest catalog | 192 tags, 273 aliases, 12 categories |
| Demo campus | 24 students, 32 connections, 5 clusters |
| Verification | 10 harnesses, 255 checks |
| Source | ~7,000 lines across 35 files |
| Dependencies | FlatLaf (theming), Gson (JSON). That's it. |

Formulas worth being able to write on a whiteboard:

```
idf(t)        = log( N / (1 + peopleWithTag(t)) )
interestSim   = Σ idf(t)·min(iᵤ,iᵥ) / Σ idf(t)·max(iᵤ,iᵥ)
adamicAdar    = Σ 1/log(degree(z))  over common neighbours z
introCost     = Σ 1/strength(edge)  along the path
modularity Q  = (1/2m) Σ [Aᵢⱼ − kᵢkⱼ/2m] δ(cᵢ,cⱼ)
```

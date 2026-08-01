package CampusConnect.domain;

/**
 * Everything about a person that is <em>computed</em> rather than entered.
 * <p>
 * These live in one object so it is obvious at a glance which parts of a person are
 * identity and which parts are derived — and so that recomputing one metric cannot
 * quietly destroy another. Previously PageRank, betweenness, closeness and degree
 * centrality all wrote to a single {@code rank} field, so running any one of them
 * overwrote the others and the "Top Influencers (PageRank)" list silently displayed
 * whichever metric happened to run last.
 * <p>
 * Every score is normalised to {@code [0, 1]} so they are directly comparable and can
 * drive the heatmap interchangeably.
 */
public class NodeMetrics {

    /**
     * The metrics a heatmap can be coloured by. Labels are user-facing.
     */
    public enum Metric {
        PAGE_RANK        ("Influence"),
        BETWEENNESS      ("Bridge Score"),
        CLOSENESS        ("Reach"),
        DEGREE           ("Connections"),
        SIMILARITY_TO_ME ("Similarity to Me");

        private final String label;
        Metric(String label) { this.label = label; }
        public String getLabel() { return label; }
        @Override public String toString() { return label; }
    }

    private double pageRank;
    private double betweenness;
    private double closeness;
    private double degree;

    /** Per-viewer, not a property of the network: how well this person matches the current user. */
    private double similarityToMe;

    /** -1 means "no community detection has been run". */
    private int communityId = -1;

    // ---------- typed access ----------

    public double getPageRank()       { return pageRank; }
    public double getBetweenness()    { return betweenness; }
    public double getCloseness()      { return closeness; }
    public double getDegree()         { return degree; }
    public double getSimilarityToMe() { return similarityToMe; }
    public int    getCommunityId()    { return communityId; }

    public void setPageRank(double v)       { this.pageRank = v; }
    public void setBetweenness(double v)    { this.betweenness = v; }
    public void setCloseness(double v)      { this.closeness = v; }
    public void setDegree(double v)         { this.degree = v; }
    public void setSimilarityToMe(double v) { this.similarityToMe = v; }
    public void setCommunityId(int v)       { this.communityId = v; }

    // ---------- generic access, for the heatmap ----------

    public double get(Metric metric) {
        return switch (metric) {
            case PAGE_RANK        -> pageRank;
            case BETWEENNESS      -> betweenness;
            case CLOSENESS        -> closeness;
            case DEGREE           -> degree;
            case SIMILARITY_TO_ME -> similarityToMe;
        };
    }

    public void set(Metric metric, double value) {
        switch (metric) {
            case PAGE_RANK        -> pageRank = value;
            case BETWEENNESS      -> betweenness = value;
            case CLOSENESS        -> closeness = value;
            case DEGREE           -> degree = value;
            case SIMILARITY_TO_ME -> similarityToMe = value;
        }
    }

    /** Reset everything derived. Called when the graph changes shape. */
    public void clear() {
        pageRank = betweenness = closeness = degree = similarityToMe = 0.0;
        communityId = -1;
    }
}

package CampusConnect.domain;

/**
 * Represents a weighted edge between two UserNodes.
 */
public class Edge {
    private final UserNode source;
    private final UserNode target;
    private double weight;

    public Edge(UserNode source, UserNode target, double weight) {
        this.source = source;
        this.target = target;
        this.weight = weight;
    }

    public Edge(UserNode source, UserNode target) {
        this(source, target, 1.0);
    }

    public UserNode getSource() { return source; }
    public UserNode getTarget() { return target; }
    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }

    /**
     * Creates a canonical key for this edge (order-independent).
     */
    public static String makeKey(UserNode u1, UserNode u2) {
        String id1 = u1.getId();
        String id2 = u2.getId();
        return id1.compareTo(id2) < 0 ? id1 + "|" + id2 : id2 + "|" + id1;
    }

    @Override
    public String toString() {
        return source.getName() + " --(" + weight + ")--> " + target.getName();
    }
}

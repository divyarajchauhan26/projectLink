package CampusConnect.domain;

/**
 * Represents a weighted edge between two people.
 */
public class Edge {
    private final Person source;
    private final Person target;
    private double weight;

    public Edge(Person source, Person target, double weight) {
        this.source = source;
        this.target = target;
        this.weight = weight;
    }

    public Edge(Person source, Person target) {
        this(source, target, 1.0);
    }

    public Person getSource() { return source; }
    public Person getTarget() { return target; }
    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }

    /**
     * Creates a canonical key for this edge (order-independent).
     */
    public static String makeKey(Person u1, Person u2) {
        String id1 = u1.getId();
        String id2 = u2.getId();
        return id1.compareTo(id2) < 0 ? id1 + "|" + id2 : id2 + "|" + id1;
    }

    @Override
    public String toString() {
        return source.getName() + " --(" + weight + ")--> " + target.getName();
    }
}

package CampusConnect.service;

import CampusConnect.domain.Edge;
import CampusConnect.domain.Person;

import java.awt.Point;
import java.util.*;

/**
 * Core graph service managing users, connections, edge weights, and physics.
 */
public class NetworkService {
    private List<Person> users;
    private Map<Person, List<Person>> adjacencyList;
    private Map<String, Double> edgeWeights; // Key: sorted node-id pair
    private static final int MIN_DISTANCE = 60;

    // PHYSICS CONSTANTS
    private static final double REPULSION_FORCE = 60000;
    private static final double SPRING_LENGTH = 150;
    private static final double SPRING_FORCE = 0.05;
    private static final double DAMPING = 0.85;
    /** Ceiling on how far a node may travel in one tick, in pixels. */
    private static final double MAX_SPEED = 30.0;
    /** Fraction of speed kept when bouncing off a canvas edge. */
    private static final double WALL_RESTITUTION = 0.5;

    // Used only to break the tie when two nodes occupy the exact same point.
    private final Random jitter = new Random();

    public NetworkService() {
        this.users = new ArrayList<>();
        this.adjacencyList = new HashMap<>();
        this.edgeWeights = new HashMap<>();
    }

    // ========== NODE MANAGEMENT ==========

    public void addRandomUser(String name, int maxX, int maxY) throws Exception {
        Random rand = new Random();
        int attempts = 0;
        while (attempts < 100) {
            int x = 50 + rand.nextInt(Math.max(1, maxX - 100));
            int y = 50 + rand.nextInt(Math.max(1, maxY - 100));
            if (isLocationValid(x, y)) {
                Person newUser = new Person(name, x, y);
                users.add(newUser);
                adjacencyList.put(newUser, new ArrayList<>());
                return;
            }
            attempts++;
        }
        throw new Exception("Canvas is too crowded!");
    }

    /**
     * Add a user at a specific position (used by Watts-Strogatz generator).
     */
    public void addUserAtPosition(String name, int x, int y) {
        Person newUser = new Person(name, x, y);
        users.add(newUser);
        adjacencyList.put(newUser, new ArrayList<>());
    }

    /**
     * Add a user with a known ID (used by deserialization).
     */
    public void addUserWithId(String id, String name, int x, int y) {
        Person newUser = new Person(id, name, x, y);
        users.add(newUser);
        adjacencyList.put(newUser, new ArrayList<>());
    }

    public void removeUser(Person userToRemove) {
        // Remove all edge weights involving this user
        for (Person other : users) {
            List<Person> friends = adjacencyList.get(other);
            if (friends != null) {
                if (friends.remove(userToRemove)) {
                    edgeWeights.remove(Edge.makeKey(other, userToRemove));
                }
            }
        }
        adjacencyList.remove(userToRemove);
        users.remove(userToRemove);
    }

    // ========== CONNECTION MANAGEMENT ==========

    public void addConnection(Person u1, Person u2) throws Exception {
        if (u1.equals(u2)) return;
        List<Person> friends1 = adjacencyList.get(u1);
        List<Person> friends2 = adjacencyList.get(u2);
        if (friends1.contains(u2)) throw new Exception("Already connected!");
        friends1.add(u2);
        friends2.add(u1);
        // Default weight 1.0
        edgeWeights.put(Edge.makeKey(u1, u2), 1.0);
    }

    public void removeConnection(Person u1, Person u2) throws Exception {
        if (u1.equals(u2)) return;
        List<Person> friends1 = adjacencyList.get(u1);
        List<Person> friends2 = adjacencyList.get(u2);
        if (!friends1.contains(u2)) throw new Exception("Not connected!");
        friends1.remove(u2);
        friends2.remove(u1);
        edgeWeights.remove(Edge.makeKey(u1, u2));
    }

    // ========== EDGE WEIGHT MANAGEMENT ==========

    public double getEdgeWeight(Person u1, Person u2) {
        return edgeWeights.getOrDefault(Edge.makeKey(u1, u2), 1.0);
    }

    public void setEdgeWeight(Person u1, Person u2, double weight) {
        String key = Edge.makeKey(u1, u2);
        if (edgeWeights.containsKey(key)) {
            edgeWeights.put(key, weight);
        }
    }

    // ========== PATHFINDING ==========

    public List<Person> findShortestPath(Person start, Person end) {
        if (start.equals(end)) return Collections.singletonList(start);
        Queue<Person> queue = new LinkedList<>();
        Map<Person, Person> predecessors = new HashMap<>();
        Set<Person> visited = new HashSet<>();
        queue.add(start); visited.add(start);

        while (!queue.isEmpty()) {
            Person current = queue.poll();
            if (current.equals(end)) break;
            for (Person neighbor : getConnections(current)) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor); predecessors.put(neighbor, current); queue.add(neighbor);
                }
            }
        }
        if (!predecessors.containsKey(end)) return Collections.emptyList();
        List<Person> path = new ArrayList<>();
        Person curr = end;
        while (curr != null) { path.add(curr); curr = predecessors.get(curr); }
        Collections.reverse(path);
        return path;
    }

    public List<Person> findPathThroughWaypoints(List<Person> sequence) {
        if (sequence.size() < 2) return new ArrayList<>(sequence);
        List<Person> fullPath = new ArrayList<>();
        fullPath.add(sequence.get(0));
        for (int i = 0; i < sequence.size() - 1; i++) {
            List<Person> leg = findShortestPath(sequence.get(i), sequence.get(i+1));
            if (leg.isEmpty()) return Collections.emptyList();
            for (int j = 1; j < leg.size(); j++) fullPath.add(leg.get(j));
        }
        return fullPath;
    }

    // ========== UTILITY ==========

    public boolean isLocationValid(int x, int y) {
        Point p = new Point(x, y);
        for (Person u : users) if (p.distance(u.getX(), u.getY()) < MIN_DISTANCE) return false;
        return true;
    }

    /**
     * Find a user by name (first match).
     */
    public Person findUserByName(String name) {
        for (Person u : users) {
            if (u.getName().equalsIgnoreCase(name)) return u;
        }
        return null;
    }

    /**
     * Find a user by ID.
     */
    public Person findUserById(String id) {
        for (Person u : users) {
            if (u.getId().equals(id)) return u;
        }
        return null;
    }

    /**
     * Clear the entire graph.
     */
    public void clear() {
        users.clear();
        adjacencyList.clear();
        edgeWeights.clear();
    }

    /**
     * Get total edge count (undirected — each edge counted once).
     */
    public int getEdgeCount() {
        int total = 0;
        for (List<Person> friends : adjacencyList.values()) {
            total += friends.size();
        }
        return total / 2;
    }

    /**
     * Get graph density: 2E / (V * (V-1)).
     */
    public double getDensity() {
        int v = users.size();
        if (v <= 1) return 0.0;
        return (2.0 * getEdgeCount()) / (v * (v - 1));
    }

    /**
     * Get average degree.
     */
    public double getAverageDegree() {
        if (users.isEmpty()) return 0.0;
        return (2.0 * getEdgeCount()) / users.size();
    }

    /**
     * Compute clustering coefficient for a single node.
     */
    public double clusteringCoefficient(Person node) {
        List<Person> neighbors = adjacencyList.getOrDefault(node, Collections.emptyList());
        int k = neighbors.size();
        if (k < 2) return 0.0;

        int triangles = 0;
        for (int i = 0; i < neighbors.size(); i++) {
            for (int j = i + 1; j < neighbors.size(); j++) {
                if (adjacencyList.getOrDefault(neighbors.get(i), Collections.emptyList())
                        .contains(neighbors.get(j))) {
                    triangles++;
                }
            }
        }
        return (2.0 * triangles) / (k * (k - 1));
    }

    /**
     * Average clustering coefficient across the whole graph.
     */
    public double averageClusteringCoefficient() {
        if (users.isEmpty()) return 0.0;
        double sum = 0;
        for (Person u : users) {
            sum += clusteringCoefficient(u);
        }
        return sum / users.size();
    }

    // ========== PHYSICS ENGINE ==========

    public void updatePhysics(int width, int height) {
        // 1. Repulsion
        for (int i = 0; i < users.size(); i++) {
            Person u1 = users.get(i);
            for (int j = i + 1; j < users.size(); j++) {
                Person u2 = users.get(j);
                double dx = u1.x - u2.x;
                double dy = u1.y - u2.y;
                double dist = Math.sqrt(dx*dx + dy*dy);
                if (dist < 0.01) {
                    // Exactly coincident. Clamping dist alone is not enough: the direction
                    // (dx/dist, dy/dist) would be (0,0), so the nodes would stay fused
                    // forever. Push them apart along an arbitrary direction instead.
                    double angle = jitter.nextDouble() * 2 * Math.PI;
                    dx = Math.cos(angle);
                    dy = Math.sin(angle);
                    dist = 1;
                } else if (dist < 1) {
                    dist = 1;
                }

                double force = REPULSION_FORCE / (dist * dist);
                double fx = (dx / dist) * force;
                double fy = (dy / dist) * force;

                u1.dx += fx; u1.dy += fy;
                u2.dx -= fx; u2.dy -= fy;
            }
        }
        // 2. Attraction (Springs)
        for (Person u1 : users) {
            for (Person u2 : adjacencyList.getOrDefault(u1, Collections.emptyList())) {
                double dx = u1.x - u2.x;
                double dy = u1.y - u2.y;
                double dist = Math.sqrt(dx*dx + dy*dy);
                // Guard against two nodes landing on the exact same spot:
                // dist == 0 would make fx/fy NaN and the nodes would vanish permanently.
                if (dist < 1) dist = 1;

                double force = (dist - SPRING_LENGTH) * SPRING_FORCE;
                double fx = (dx / dist) * force;
                double fy = (dy / dist) * force;

                u1.dx -= fx; u1.dy -= fy;
            }
            // Center Gravity
            u1.dx += (width/2.0 - u1.x) * 0.005;
            u1.dy += (height/2.0 - u1.y) * 0.005;
        }
        // 3. Update Position
        for (Person u : users) {
            u.dx *= DAMPING; u.dy *= DAMPING;

            // Cap the per-tick step. Repulsion grows as 1/dist^2, so a pair that starts
            // close enough produces an enormous force, which throws a node hundreds of
            // pixels, which lands it on top of another node, which repels harder still.
            // Without this ceiling the whole simulation diverges within a few frames —
            // it only looked stable in V1 because 25 scattered nodes never got close
            // enough to trigger it.
            double speed = Math.sqrt(u.dx * u.dx + u.dy * u.dy);
            if (speed > MAX_SPEED) {
                double scale = MAX_SPEED / speed;
                u.dx *= scale; u.dy *= scale;
            }

            u.x += u.dx; u.y += u.dy;

            // Boundaries. Reflect with loss — a perfectly elastic bounce (dx *= -1)
            // returns all the energy and lets nodes rattle along the walls forever.
            if (u.x < 20)          { u.x = 20;          u.dx *= -WALL_RESTITUTION; }
            if (u.y < 20)          { u.y = 20;          u.dy *= -WALL_RESTITUTION; }
            if (u.x > width - 20)  { u.x = width - 20;  u.dx *= -WALL_RESTITUTION; }
            if (u.y > height - 20) { u.y = height - 20; u.dy *= -WALL_RESTITUTION; }
        }
    }

    // ========== ACCESSORS ==========

    public List<Person> getAllUsers() { return users; }
    public List<Person> getConnections(Person user) { return adjacencyList.getOrDefault(user, Collections.emptyList()); }
    public Map<Person, List<Person>> getAdjacencyList() { return adjacencyList; }
    public Map<String, Double> getEdgeWeights() { return edgeWeights; }
}
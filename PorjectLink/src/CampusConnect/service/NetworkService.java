package CampusConnect.service;

import CampusConnect.domain.UserNode;
import java.awt.Point;
import java.util.*;

public class NetworkService {
    private List<UserNode> users;
    private Map<UserNode, List<UserNode>> adjacencyList;
    private static final int MIN_DISTANCE = 60;

    // PHYSICS CONSTANTS
    private static final double REPULSION_FORCE = 60000;
    private static final double SPRING_LENGTH = 150;
    private static final double SPRING_FORCE = 0.05;
    private static final double DAMPING = 0.85;

    public NetworkService() {
        this.users = new ArrayList<>();
        this.adjacencyList = new HashMap<>();
    }

    public void addRandomUser(String name, int maxX, int maxY) throws Exception {
        Random rand = new Random();
        int attempts = 0;
        while (attempts < 100) {
            int x = 50 + rand.nextInt(Math.max(1, maxX - 100));
            int y = 50 + rand.nextInt(Math.max(1, maxY - 100));
            if (isLocationValid(x, y)) {
                UserNode newUser = new UserNode(name, x, y);
                users.add(newUser);
                adjacencyList.put(newUser, new ArrayList<>());
                return;
            }
            attempts++;
        }
        throw new Exception("Canvas is too crowded!");
    }

    public void addConnection(UserNode u1, UserNode u2) throws Exception {
        if (u1.equals(u2)) return;
        List<UserNode> friends1 = adjacencyList.get(u1);
        List<UserNode> friends2 = adjacencyList.get(u2);
        if (friends1.contains(u2)) throw new Exception("Already connected!");
        friends1.add(u2);
        friends2.add(u1);
    }

    public void removeUser(UserNode userToRemove) {
        for (UserNode other : users) {
            List<UserNode> friends = adjacencyList.get(other);
            if (friends != null) friends.remove(userToRemove);
        }
        adjacencyList.remove(userToRemove);
        users.remove(userToRemove);
    }

    public List<UserNode> findShortestPath(UserNode start, UserNode end) {
        if (start.equals(end)) return Collections.singletonList(start);
        Queue<UserNode> queue = new LinkedList<>();
        Map<UserNode, UserNode> predecessors = new HashMap<>();
        Set<UserNode> visited = new HashSet<>();
        queue.add(start); visited.add(start);

        while (!queue.isEmpty()) {
            UserNode current = queue.poll();
            if (current.equals(end)) break;
            for (UserNode neighbor : getConnections(current)) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor); predecessors.put(neighbor, current); queue.add(neighbor);
                }
            }
        }
        if (!predecessors.containsKey(end)) return Collections.emptyList();
        List<UserNode> path = new ArrayList<>();
        UserNode curr = end;
        while (curr != null) { path.add(curr); curr = predecessors.get(curr); }
        Collections.reverse(path);
        return path;
    }

    public List<UserNode> findPathThroughWaypoints(List<UserNode> sequence) {
        if (sequence.size() < 2) return new ArrayList<>(sequence);
        List<UserNode> fullPath = new ArrayList<>();
        fullPath.add(sequence.get(0));
        for (int i = 0; i < sequence.size() - 1; i++) {
            List<UserNode> leg = findShortestPath(sequence.get(i), sequence.get(i+1));
            if (leg.isEmpty()) return Collections.emptyList();
            for (int j = 1; j < leg.size(); j++) fullPath.add(leg.get(j));
        }
        return fullPath;
    }

    public boolean isLocationValid(int x, int y) {
        Point p = new Point(x, y);
        for (UserNode u : users) if (p.distance(u.getX(), u.getY()) < MIN_DISTANCE) return false;
        return true;
    }

    // --- PHYSICS ENGINE LOGIC ---
    public void updatePhysics(int width, int height) {
        // 1. Repulsion
        for (int i = 0; i < users.size(); i++) {
            UserNode u1 = users.get(i);
            for (int j = i + 1; j < users.size(); j++) {
                UserNode u2 = users.get(j);
                double dx = u1.x - u2.x;
                double dy = u1.y - u2.y;
                double dist = Math.sqrt(dx*dx + dy*dy);
                if (dist < 1) dist = 1;

                double force = REPULSION_FORCE / (dist * dist);
                double fx = (dx / dist) * force;
                double fy = (dy / dist) * force;

                u1.dx += fx; u1.dy += fy;
                u2.dx -= fx; u2.dy -= fy;
            }
        }
        // 2. Attraction (Springs)
        for (UserNode u1 : users) {
            for (UserNode u2 : adjacencyList.get(u1)) {
                double dx = u1.x - u2.x;
                double dy = u1.y - u2.y;
                double dist = Math.sqrt(dx*dx + dy*dy);

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
        for (UserNode u : users) {
            u.dx *= DAMPING; u.dy *= DAMPING;
            u.x += u.dx; u.y += u.dy;

            // Boundaries
            if (u.x < 20) { u.x = 20; u.dx *= -1; }
            if (u.y < 20) { u.y = 20; u.dy *= -1; }
            if (u.x > width-20) { u.x = width-20; u.dx *= -1; }
            if (u.y > height-20) { u.y = height-20; u.dy *= -1; }
        }
    }

    public List<UserNode> getAllUsers() { return users; }
    public List<UserNode> getConnections(UserNode user) { return adjacencyList.getOrDefault(user, Collections.emptyList()); }
}
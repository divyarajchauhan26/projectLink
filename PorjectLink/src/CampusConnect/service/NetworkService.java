package CampusConnect.service;

import CampusConnect.domain.UserNode;

import java.awt.Point;
import java.util.*;

public class NetworkService {

    private List<UserNode> users;
    private Map<UserNode, List<UserNode>> adjacencyList;
    private static final int MIN_DISTANCE = 60; // Distance to prevent overlap

    public NetworkService() {
        this.users = new ArrayList<>();
        this.adjacencyList = new HashMap<>();
    }

    // --- SMART ADDITION ---
    // Tries to find a valid random spot within the given width/height
    public void addRandomUser(String name, int maxX, int maxY) throws Exception {
        Random rand = new Random();
        int attempts = 0;

        while (attempts < 100) {
            // Keep a 50px padding from edges
            int x = 50 + rand.nextInt(Math.max(1, maxX - 100));
            int y = 50 + rand.nextInt(Math.max(1, maxY - 100));

            if (isLocationValid(x, y)) {
                UserNode newUser = new UserNode(name, x, y);
                users.add(newUser);
                adjacencyList.put(newUser, new ArrayList<>());
                return; // Success!
            }
            attempts++;
        }
        throw new Exception("Canvas is too crowded! Could not find a safe spot.");
    }

    // --- STANDARD LOGIC ---

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

    // --- ALGORITHMS ---

    public List<UserNode> findShortestPath(UserNode start, UserNode end) {
        if (start.equals(end)) return Collections.singletonList(start);

        Queue<UserNode> queue = new LinkedList<>();
        Map<UserNode, UserNode> predecessors = new HashMap<>();
        Set<UserNode> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            UserNode current = queue.poll();
            if (current.equals(end)) break;

            for (UserNode neighbor : getConnections(current)) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    predecessors.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }

        if (!predecessors.containsKey(end)) return Collections.emptyList();

        List<UserNode> path = new ArrayList<>();
        UserNode curr = end;
        while (curr != null) {
            path.add(curr);
            curr = predecessors.get(curr);
        }
        Collections.reverse(path);
        return path;
    }

    public boolean isLocationValid(int x, int y) {
        Point p = new Point(x, y);
        for (UserNode u : users) {
            if (p.distance(u.getX(), u.getY()) < MIN_DISTANCE) return false;
        }
        return true;
    }

    public List<UserNode> getAllUsers() { return users; }

    public List<UserNode> getConnections(UserNode user) {
        return adjacencyList.getOrDefault(user, Collections.emptyList());
    }
}
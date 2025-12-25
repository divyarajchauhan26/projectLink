package CampusConnect.model;
import java.util.*;
import java.awt.Point;
import java.util.*;

public class GraphModel {
    private List<UserNode> users;
    private Map<UserNode, List<UserNode>> adjacencyList;
    private static final int MIN_DISTANCE = 60; // Minimum gap between dots

    public GraphModel() {
        this.users = new ArrayList<>();
        this.adjacencyList = new HashMap<>();
    }

    public void addUser(UserNode user) {
        users.add(user);
        adjacencyList.put(user, new ArrayList<>());
    }

    public void addConnection(UserNode u1, UserNode u2) {
        // Prevent connecting to self or duplicates
        if (!u1.equals(u2) && !adjacencyList.get(u1).contains(u2)) {
            adjacencyList.get(u1).add(u2);
            adjacencyList.get(u2).add(u1);
        }
    }

    // NEW: Check if a specific x,y is too close to existing users
    public boolean isLocationValid(int x, int y) {
        Point p = new Point(x, y);
        for (UserNode u : users) {
            // If distance is less than 60px, it's invalid
            if (p.distance(u.getX(), u.getY()) < MIN_DISTANCE) {
                return false;
            }
        }
        return true;
    }

    public List<UserNode> getUsers() { return users; }
    public List<UserNode> getConnections(UserNode user) {
        return adjacencyList.getOrDefault(user, Collections.emptyList());
    }
}
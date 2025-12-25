package CampusConnect.model;
import java.awt.Point;

public class UserNode {
    private String id;
    private String name;
    private int x; // X position on the map
    private int y; // Y position on the map

    public UserNode(String id, String name, int x, int y) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
    }

    public String getName() { return name; }
    public String getId() { return id; }
    public int getX() { return x; }
    public int getY() { return y; }

    // Setters allow us to drag the node around
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    // Simple collision detection (did I click this node?)
    public boolean contains(Point p) {
        // Assume node radius is 20px
        return p.distance(x, y) <= 20;
    }
}
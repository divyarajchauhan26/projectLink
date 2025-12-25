package CampusConnect.domain;

import java.awt.Point;
import java.util.UUID;

public class UserNode {
    private String id;
    private String name;
    private int x;
    private int y;

    public UserNode(String name, int x, int y) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.x = x;
        this.y = y;
    }

    public String getName() { return name; }
    public int getX() { return x; }
    public int getY() { return y; }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public boolean contains(Point p) {
        return p.distance(x, y) <= 20; // Hitbox radius
    }
}
package CampusConnect.domain;

import java.awt.Point;
import java.util.UUID;

public class UserNode {
    private String id;
    private String name;

    // Physics properties
    public double x, y;
    public double dx, dy; // Velocity

    public UserNode(String name, int x, int y) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.x = x;
        this.y = y;
        this.dx = 0;
        this.dy = 0;
    }

    public String getName() { return name; }

    // Cast to int for drawing
    public int getX() { return (int) x; }
    public int getY() { return (int) y; }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        // Stop moving if user drags it manually
        this.dx = 0;
        this.dy = 0;
    }

    public boolean contains(Point p) {
        return p.distance(x, y) <= 20;
    }
}
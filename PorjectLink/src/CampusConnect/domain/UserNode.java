package CampusConnect.domain;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UserNode {
    private String id;
    private String name;

    // Physics properties
    public double x, y;
    public double dx, dy; // Velocity

    // --- Graph Analysis Fields ---
    private int communityId = -1;      // Community detection
    private double rank = 0.0;         // PageRank score
    private double centrality = 0.0;   // General centrality metric

    // --- Profile Fields ---
    private String major = "";
    private int year = 0;
    private List<String> interests = new ArrayList<>();

    public UserNode(String name, int x, int y) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.x = x;
        this.y = y;
        this.dx = 0;
        this.dy = 0;
    }

    // Constructor for deserialization (with known ID)
    public UserNode(String id, String name, int x, int y) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
        this.dx = 0;
        this.dy = 0;
    }

    // --- Core Getters ---
    public String getId() { return id; }
    public String getName() { return name; }

    // Cast to int for drawing
    public int getX() { return (int) x; }
    public int getY() { return (int) y; }

    // --- Graph Analysis Getters/Setters ---
    public int getCommunityId() { return communityId; }
    public void setCommunityId(int communityId) { this.communityId = communityId; }

    public double getRank() { return rank; }
    public void setRank(double rank) { this.rank = rank; }

    public double getCentrality() { return centrality; }
    public void setCentrality(double centrality) { this.centrality = centrality; }

    // --- Profile Getters/Setters ---
    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public List<String> getInterests() { return interests; }
    public void setInterests(List<String> interests) { this.interests = interests; }

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

    @Override
    public String toString() {
        return name;
    }
}
package CampusConnect.service;

import CampusConnect.domain.Edge;
import CampusConnect.domain.InterestCatalog;
import CampusConnect.domain.InterestTag;
import CampusConnect.domain.UserNode;

import java.io.*;
import java.util.*;

/**
 * JSON save/load and CSV export for the graph.
 * Uses manual JSON writing — no external library needed.
 */
public class GraphPersistence {

    /**
     * Save the entire graph to a JSON file.
     */
    public static void saveToJson(NetworkService service, File file) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("{");

            // --- Nodes ---
            writer.println("  \"nodes\": [");
            List<UserNode> users = service.getAllUsers();
            for (int i = 0; i < users.size(); i++) {
                UserNode u = users.get(i);
                writer.print("    {");
                writer.print("\"id\": \"" + escape(u.getId()) + "\", ");
                writer.print("\"name\": \"" + escape(u.getName()) + "\", ");
                writer.print("\"x\": " + u.getX() + ", ");
                writer.print("\"y\": " + u.getY() + ", ");
                writer.print("\"major\": \"" + escape(u.getMajor()) + "\", ");
                writer.print("\"year\": " + u.getYear() + ", ");
                writer.print("\"interests\": [");
                // Persist canonical tag ids, never display labels — labels can be
                // reworded, ids are stable forever.
                boolean firstTag = true;
                for (InterestTag tag : u.getInterests()) {
                    if (!firstTag) writer.print(", ");
                    writer.print("\"" + escape(tag.id()) + "\"");
                    firstTag = false;
                }
                writer.print("]");
                writer.print("}");
                if (i < users.size() - 1) writer.println(",");
                else writer.println();
            }
            writer.println("  ],");

            // --- Edges ---
            writer.println("  \"edges\": [");
            Set<String> writtenEdges = new HashSet<>();
            boolean firstEdge = true;
            for (UserNode u : users) {
                for (UserNode friend : service.getConnections(u)) {
                    String key = Edge.makeKey(u, friend);
                    if (!writtenEdges.contains(key)) {
                        writtenEdges.add(key);
                        if (!firstEdge) writer.println(",");
                        double weight = service.getEdgeWeight(u, friend);
                        writer.print("    {");
                        writer.print("\"source\": \"" + escape(u.getId()) + "\", ");
                        writer.print("\"target\": \"" + escape(friend.getId()) + "\", ");
                        writer.print("\"weight\": " + weight);
                        writer.print("}");
                        firstEdge = false;
                    }
                }
            }
            writer.println();
            writer.println("  ]");
            writer.println("}");
        }
    }

    /**
     * Load a graph from a JSON file.
     */
    public static void loadFromJson(NetworkService service, File file) throws IOException {
        service.clear();

        String content = readFile(file);

        // Parse nodes
        List<Map<String, String>> nodes = parseJsonArray(content, "nodes");
        for (Map<String, String> node : nodes) {
            String id = node.getOrDefault("id", UUID.randomUUID().toString());
            String name = node.getOrDefault("name", "Unknown");
            int x = parseInt(node.getOrDefault("x", "100"));
            int y = parseInt(node.getOrDefault("y", "100"));
            service.addUserWithId(id, name, x, y);

            UserNode u = service.findUserById(id);
            if (u != null) {
                u.setMajor(node.getOrDefault("major", ""));
                u.setYear(parseInt(node.getOrDefault("year", "0")));
                // Interests are handled separately
                String interestsStr = node.getOrDefault("interests", "");
                if (!interestsStr.isEmpty()) {
                    InterestCatalog catalog = InterestCatalog.getDefault();
                    for (String s : interestsStr.split(",")) {
                        // Route through the resolver rather than trusting the file:
                        // hand-edited saves and older exports may hold labels or aliases.
                        InterestCatalog.Resolution r = catalog.resolve(s.trim());
                        if (r.found()) u.addInterest(r.tag());
                    }
                }
            }
        }

        // Parse edges
        List<Map<String, String>> edges = parseJsonArray(content, "edges");
        for (Map<String, String> edge : edges) {
            String sourceId = edge.get("source");
            String targetId = edge.get("target");
            double weight = parseDouble(edge.getOrDefault("weight", "1.0"));

            UserNode source = service.findUserById(sourceId);
            UserNode target = service.findUserById(targetId);
            if (source != null && target != null) {
                try {
                    service.addConnection(source, target);
                    service.setEdgeWeight(source, target, weight);
                } catch (Exception e) {
                    // Skip duplicate connections
                }
            }
        }
    }

    /**
     * Export the adjacency matrix to a CSV file.
     */
    public static void exportToCsv(NetworkService service, File file) throws IOException {
        List<UserNode> users = service.getAllUsers();
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            // Header row
            writer.print(",");
            for (int i = 0; i < users.size(); i++) {
                writer.print(escape(users.get(i).getName()));
                if (i < users.size() - 1) writer.print(",");
            }
            writer.println();

            // Data rows
            for (UserNode u : users) {
                writer.print(escape(u.getName()) + ",");
                List<UserNode> connections = service.getConnections(u);
                for (int j = 0; j < users.size(); j++) {
                    UserNode v = users.get(j);
                    if (connections.contains(v)) {
                        writer.print(String.format("%.1f", service.getEdgeWeight(u, v)));
                    } else {
                        writer.print("0");
                    }
                    if (j < users.size() - 1) writer.print(",");
                }
                writer.println();
            }
        }
    }

    // ========== PRIVATE HELPERS ==========

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String readFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); }
        catch (NumberFormatException e) { return 1.0; }
    }

    /**
     * Simple JSON array parser — extracts objects from a named array in the JSON.
     * Not a full JSON parser, but handles our specific format reliably.
     */
    private static List<Map<String, String>> parseJsonArray(String json, String arrayName) {
        List<Map<String, String>> results = new ArrayList<>();

        // Find the array
        String searchKey = "\"" + arrayName + "\"";
        int arrayStart = json.indexOf(searchKey);
        if (arrayStart < 0) return results;

        int bracketStart = json.indexOf('[', arrayStart);
        if (bracketStart < 0) return results;

        // Find matching bracket
        int depth = 0;
        int bracketEnd = -1;
        for (int i = bracketStart; i < json.length(); i++) {
            if (json.charAt(i) == '[') depth++;
            else if (json.charAt(i) == ']') {
                depth--;
                if (depth == 0) { bracketEnd = i; break; }
            }
        }
        if (bracketEnd < 0) return results;

        String arrayContent = json.substring(bracketStart + 1, bracketEnd);

        // Parse individual objects
        int objDepth = 0;
        int objStart = -1;
        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);
            if (c == '{') {
                if (objDepth == 0) objStart = i;
                objDepth++;
            } else if (c == '}') {
                objDepth--;
                if (objDepth == 0 && objStart >= 0) {
                    String objStr = arrayContent.substring(objStart + 1, i);
                    results.add(parseJsonObject(objStr));
                    objStart = -1;
                }
            }
        }

        return results;
    }

    /**
     * Parse a flat JSON object into a key-value map.
     * Handles strings, numbers, and simple arrays (flattened to comma-separated string).
     */
    private static Map<String, String> parseJsonObject(String objStr) {
        Map<String, String> map = new HashMap<>();

        int i = 0;
        while (i < objStr.length()) {
            // Find key
            int keyStart = objStr.indexOf('"', i);
            if (keyStart < 0) break;
            int keyEnd = objStr.indexOf('"', keyStart + 1);
            if (keyEnd < 0) break;
            String key = objStr.substring(keyStart + 1, keyEnd);

            // Find colon
            int colon = objStr.indexOf(':', keyEnd);
            if (colon < 0) break;

            // Find value
            int valStart = colon + 1;
            while (valStart < objStr.length() && objStr.charAt(valStart) == ' ') valStart++;

            if (valStart >= objStr.length()) break;

            String value;
            if (objStr.charAt(valStart) == '"') {
                // String value
                int valEnd = findClosingQuote(objStr, valStart + 1);
                value = objStr.substring(valStart + 1, valEnd).replace("\\\"", "\"").replace("\\\\", "\\");
                i = valEnd + 1;
            } else if (objStr.charAt(valStart) == '[') {
                // Array value — flatten to comma-separated
                int arrayEnd = objStr.indexOf(']', valStart);
                if (arrayEnd < 0) break;
                String arrayContent = objStr.substring(valStart + 1, arrayEnd);
                // Extract string values from array
                StringBuilder sb = new StringBuilder();
                int qi = 0;
                boolean first = true;
                while (qi < arrayContent.length()) {
                    int qs = arrayContent.indexOf('"', qi);
                    if (qs < 0) break;
                    int qe = findClosingQuote(arrayContent, qs + 1);
                    if (!first) sb.append(",");
                    sb.append(arrayContent.substring(qs + 1, qe));
                    first = false;
                    qi = qe + 1;
                }
                value = sb.toString();
                i = arrayEnd + 1;
            } else {
                // Number or other value
                int valEnd = valStart;
                while (valEnd < objStr.length() && objStr.charAt(valEnd) != ','
                        && objStr.charAt(valEnd) != '}' && objStr.charAt(valEnd) != ']') {
                    valEnd++;
                }
                value = objStr.substring(valStart, valEnd).trim();
                i = valEnd;
            }

            map.put(key, value);
        }

        return map;
    }

    private static int findClosingQuote(String s, int start) {
        for (int i = start; i < s.length(); i++) {
            if (s.charAt(i) == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                return i;
            }
        }
        return s.length() - 1;
    }
}

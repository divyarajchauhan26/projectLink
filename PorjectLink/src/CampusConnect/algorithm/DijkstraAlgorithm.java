package CampusConnect.algorithm;

import CampusConnect.domain.Edge;
import CampusConnect.domain.Person;

import java.util.*;

/**
 * Dijkstra's weighted shortest path and A* variant.
 */
public class DijkstraAlgorithm {

    /**
     * Result of a pathfinding operation.
     */
    public static class PathResult {
        public final List<Person> path;
        public final double totalCost;
        public final List<List<Person>> steps; // For visualization

        public PathResult(List<Person> path, double totalCost, List<List<Person>> steps) {
            this.path = path;
            this.totalCost = totalCost;
            this.steps = steps;
        }
    }

    /**
     * Standard Dijkstra's algorithm with weighted edges.
     */
    public static PathResult findShortestPath(
            Person start, Person end,
            Map<Person, List<Person>> adjacencyList,
            Map<String, Double> edgeWeights) {

        Map<Person, Double> dist = new HashMap<>();
        Map<Person, Person> prev = new HashMap<>();
        Set<Person> visited = new HashSet<>();
        List<List<Person>> steps = new ArrayList<>();

        // Priority queue: (distance, node)
        PriorityQueue<Map.Entry<Double, Person>> pq =
                new PriorityQueue<>(Comparator.comparingDouble(Map.Entry::getKey));

        dist.put(start, 0.0);
        pq.add(new AbstractMap.SimpleEntry<>(0.0, start));

        while (!pq.isEmpty()) {
            Map.Entry<Double, Person> entry = pq.poll();
            Person current = entry.getValue();

            if (visited.contains(current)) continue;
            visited.add(current);

            // Record step for visualization
            steps.add(new ArrayList<>(visited));

            if (current.equals(end)) break;

            List<Person> neighbors = adjacencyList.getOrDefault(current, Collections.emptyList());
            for (Person neighbor : neighbors) {
                if (visited.contains(neighbor)) continue;

                String edgeKey = Edge.makeKey(current, neighbor);
                double weight = edgeWeights.getOrDefault(edgeKey, 1.0);
                double newDist = dist.get(current) + weight;

                if (newDist < dist.getOrDefault(neighbor, Double.MAX_VALUE)) {
                    dist.put(neighbor, newDist);
                    prev.put(neighbor, current);
                    pq.add(new AbstractMap.SimpleEntry<>(newDist, neighbor));
                }
            }
        }

        // Reconstruct path
        if (!prev.containsKey(end) && !start.equals(end)) {
            return new PathResult(Collections.emptyList(), 0, steps);
        }

        List<Person> path = new ArrayList<>();
        Person curr = end;
        while (curr != null) {
            path.add(curr);
            curr = prev.get(curr);
        }
        Collections.reverse(path);
        double totalCost = dist.getOrDefault(end, 0.0);

        return new PathResult(path, totalCost, steps);
    }

    /**
     * A* variant using Euclidean distance on the canvas as heuristic.
     */
    public static PathResult findPathAStar(
            Person start, Person end,
            Map<Person, List<Person>> adjacencyList,
            Map<String, Double> edgeWeights) {

        Map<Person, Double> gScore = new HashMap<>();
        Map<Person, Double> fScore = new HashMap<>();
        Map<Person, Person> prev = new HashMap<>();
        Set<Person> visited = new HashSet<>();
        List<List<Person>> steps = new ArrayList<>();

        PriorityQueue<Map.Entry<Double, Person>> openSet =
                new PriorityQueue<>(Comparator.comparingDouble(Map.Entry::getKey));

        gScore.put(start, 0.0);
        fScore.put(start, heuristic(start, end));
        openSet.add(new AbstractMap.SimpleEntry<>(fScore.get(start), start));

        while (!openSet.isEmpty()) {
            Map.Entry<Double, Person> entry = openSet.poll();
            Person current = entry.getValue();

            if (visited.contains(current)) continue;
            visited.add(current);
            steps.add(new ArrayList<>(visited));

            if (current.equals(end)) break;

            List<Person> neighbors = adjacencyList.getOrDefault(current, Collections.emptyList());
            for (Person neighbor : neighbors) {
                if (visited.contains(neighbor)) continue;

                String edgeKey = Edge.makeKey(current, neighbor);
                double weight = edgeWeights.getOrDefault(edgeKey, 1.0);
                double tentativeG = gScore.get(current) + weight;

                if (tentativeG < gScore.getOrDefault(neighbor, Double.MAX_VALUE)) {
                    prev.put(neighbor, current);
                    gScore.put(neighbor, tentativeG);
                    fScore.put(neighbor, tentativeG + heuristic(neighbor, end));
                    openSet.add(new AbstractMap.SimpleEntry<>(fScore.get(neighbor), neighbor));
                }
            }
        }

        if (!prev.containsKey(end) && !start.equals(end)) {
            return new PathResult(Collections.emptyList(), 0, steps);
        }

        List<Person> path = new ArrayList<>();
        Person curr = end;
        while (curr != null) {
            path.add(curr);
            curr = prev.get(curr);
        }
        Collections.reverse(path);
        double totalCost = gScore.getOrDefault(end, 0.0);

        return new PathResult(path, totalCost, steps);
    }

    private static double heuristic(Person a, Person b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        return Math.sqrt(dx * dx + dy * dy) * 0.01; // Scale down so it doesn't dominate
    }
}

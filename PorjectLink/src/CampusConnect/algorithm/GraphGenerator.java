package CampusConnect.algorithm;

import CampusConnect.domain.Person;
import CampusConnect.service.NetworkService;

import java.util.*;

/**
 * Random graph generators for testing and demonstration:
 * - Barabási-Albert (Preferential Attachment) — scale-free networks
 * - Erdős-Rényi (Random) — uniform random graphs
 * - Watts-Strogatz (Small World) — high clustering + short paths
 */
public class GraphGenerator {

    private static final String[] SAMPLE_NAMES = {
        "Alice", "Bob", "Charlie", "Diana", "Eve", "Frank", "Grace", "Hank",
        "Ivy", "Jack", "Karen", "Leo", "Mia", "Noah", "Olivia", "Pete",
        "Quinn", "Ryan", "Sara", "Tom", "Uma", "Victor", "Wendy", "Xavier",
        "Yara", "Zack", "Amber", "Blake", "Cora", "Derek", "Elena", "Felix",
        "Gina", "Hugo", "Iris", "James", "Kira", "Liam", "Maya", "Nate",
        "Opal", "Paul", "Rosa", "Seth", "Tina", "Uri", "Vera", "Will",
        "Xena", "Yuki"
    };

    /**
     * Barabási-Albert preferential attachment model.
     * Creates a scale-free network where popular nodes get more connections.
     *
     * @param n total number of nodes
     * @param m number of edges to attach from each new node (typically 2-3)
     * @param service the NetworkService to populate
     * @param canvasWidth canvas width for positioning
     * @param canvasHeight canvas height for positioning
     */
    public static void generateBarabasiAlbert(int n, int m,
                                               NetworkService service,
                                               int canvasWidth, int canvasHeight) throws Exception {
        Random rand = new Random();
        List<Person> nodes = new ArrayList<>();

        // Clear existing graph
        service.clear();

        // Create initial complete graph of m+1 nodes
        int initialSize = Math.min(m + 1, n);
        for (int i = 0; i < initialSize; i++) {
            String name = i < SAMPLE_NAMES.length ? SAMPLE_NAMES[i] : "User" + i;
            service.addRandomUser(name, canvasWidth, canvasHeight);
        }
        nodes.addAll(service.getAllUsers());

        // Connect initial nodes fully
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                try { service.addConnection(nodes.get(i), nodes.get(j)); } catch (Exception e) {}
            }
        }

        // Add remaining nodes with preferential attachment
        for (int i = initialSize; i < n; i++) {
            String name = i < SAMPLE_NAMES.length ? SAMPLE_NAMES[i] : "User" + i;
            service.addRandomUser(name, canvasWidth, canvasHeight);
            Person newNode = service.getAllUsers().get(service.getAllUsers().size() - 1);

            // Build degree list for preferential attachment
            List<Person> degreePool = new ArrayList<>();
            for (Person existing : nodes) {
                int degree = service.getConnections(existing).size();
                for (int d = 0; d <= degree; d++) { // degree+1 copies (so isolated nodes still have a chance)
                    degreePool.add(existing);
                }
            }

            // Select m unique targets
            Set<Person> targets = new HashSet<>();
            int attempts = 0;
            while (targets.size() < Math.min(m, nodes.size()) && attempts < 1000) {
                Person target = degreePool.get(rand.nextInt(degreePool.size()));
                targets.add(target);
                attempts++;
            }

            for (Person target : targets) {
                try { service.addConnection(newNode, target); } catch (Exception e) {}
            }
            nodes.add(newNode);
        }
    }

    /**
     * Erdős-Rényi random graph model G(n, p).
     * Each pair of nodes is connected with probability p.
     *
     * @param n number of nodes
     * @param p probability of each edge (0.0 to 1.0)
     */
    public static void generateErdosRenyi(int n, double p,
                                           NetworkService service,
                                           int canvasWidth, int canvasHeight) throws Exception {
        Random rand = new Random();
        service.clear();

        // Create all nodes
        for (int i = 0; i < n; i++) {
            String name = i < SAMPLE_NAMES.length ? SAMPLE_NAMES[i] : "User" + i;
            service.addRandomUser(name, canvasWidth, canvasHeight);
        }

        List<Person> nodes = service.getAllUsers();

        // Connect each pair with probability p
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                if (rand.nextDouble() < p) {
                    try { service.addConnection(nodes.get(i), nodes.get(j)); } catch (Exception e) {}
                }
            }
        }
    }

    /**
     * Watts-Strogatz small-world model.
     * Starts with a ring lattice with K nearest neighbors, then rewires each edge with probability beta.
     *
     * @param n number of nodes
     * @param k each node connects to k/2 nearest neighbors on each side
     * @param beta rewiring probability (0 = regular lattice, 1 = random graph)
     */
    public static void generateWattsStrogatz(int n, int k, double beta,
                                              NetworkService service,
                                              int canvasWidth, int canvasHeight) throws Exception {
        Random rand = new Random();
        service.clear();

        // Create nodes arranged in a circle
        for (int i = 0; i < n; i++) {
            String name = i < SAMPLE_NAMES.length ? SAMPLE_NAMES[i] : "User" + i;
            int cx = canvasWidth / 2;
            int cy = canvasHeight / 2;
            int radius = Math.min(canvasWidth, canvasHeight) / 3;
            double angle = 2 * Math.PI * i / n;
            int x = (int) (cx + radius * Math.cos(angle));
            int y = (int) (cy + radius * Math.sin(angle));

            service.addUserAtPosition(name, x, y);
        }

        List<Person> nodes = service.getAllUsers();
        int halfK = k / 2;

        // Create ring lattice
        for (int i = 0; i < n; i++) {
            for (int j = 1; j <= halfK; j++) {
                int neighbor = (i + j) % n;
                try { service.addConnection(nodes.get(i), nodes.get(neighbor)); } catch (Exception e) {}
            }
        }

        // Rewire edges with probability beta
        for (int i = 0; i < n; i++) {
            for (int j = 1; j <= halfK; j++) {
                if (rand.nextDouble() < beta) {
                    int neighbor = (i + j) % n;
                    // Try to remove old edge and add a new random one
                    try {
                        service.removeConnection(nodes.get(i), nodes.get(neighbor));
                        // Pick a random new target
                        int newTarget;
                        int rewireAttempts = 0;
                        do {
                            newTarget = rand.nextInt(n);
                            rewireAttempts++;
                        } while ((newTarget == i ||
                                service.getConnections(nodes.get(i)).contains(nodes.get(newTarget)))
                                && rewireAttempts < 100);

                        if (rewireAttempts < 100) {
                            service.addConnection(nodes.get(i), nodes.get(newTarget));
                        } else {
                            // Couldn't find target, restore original
                            service.addConnection(nodes.get(i), nodes.get(neighbor));
                        }
                    } catch (Exception e) {}
                }
            }
        }
    }
}

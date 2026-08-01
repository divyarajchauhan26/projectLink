package CampusConnect.ui;

import CampusConnect.domain.Person;
import CampusConnect.service.NetworkService;
import CampusConnect.algorithm.CommunityDetection;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Live network statistics sidebar.
 */
public class StatsPanel extends JPanel {

    private final NetworkService service;
    private final JLabel lblNodes;
    private final JLabel lblEdges;
    private final JLabel lblDensity;
    private final JLabel lblAvgDegree;
    private final JLabel lblClustering;
    private final JLabel lblModularity;
    
    private final DefaultListModel<String> topUsersModel;
    private final JList<String> topUsersList;

    public StatsPanel(NetworkService service) {
        this.service = service;
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(220, 0));
        setBackground(new Color(35, 35, 35));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel titleLbl = new JLabel("Network Stats");
        titleLbl.setForeground(Color.WHITE);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLbl.setBorder(new EmptyBorder(0, 0, 15, 0));
        add(titleLbl, BorderLayout.NORTH);

        JPanel statsContainer = new JPanel();
        statsContainer.setLayout(new BoxLayout(statsContainer, BoxLayout.Y_AXIS));
        statsContainer.setBackground(new Color(35, 35, 35));

        lblNodes = createStatLabel("Nodes: 0");
        lblEdges = createStatLabel("Edges: 0");
        lblDensity = createStatLabel("Density: 0.00");
        lblAvgDegree = createStatLabel("Avg Degree: 0.00");
        lblClustering = createStatLabel("Clustering: 0.00");
        lblModularity = createStatLabel("Modularity: 0.00");

        statsContainer.add(lblNodes);
        statsContainer.add(Box.createVerticalStrut(8));
        statsContainer.add(lblEdges);
        statsContainer.add(Box.createVerticalStrut(8));
        statsContainer.add(lblDensity);
        statsContainer.add(Box.createVerticalStrut(8));
        statsContainer.add(lblAvgDegree);
        statsContainer.add(Box.createVerticalStrut(8));
        statsContainer.add(lblClustering);
        statsContainer.add(Box.createVerticalStrut(8));
        statsContainer.add(lblModularity);
        statsContainer.add(Box.createVerticalStrut(20));

        JLabel lblTop = new JLabel("Top Influencers (PageRank)");
        lblTop.setForeground(new Color(180, 180, 180));
        lblTop.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statsContainer.add(lblTop);
        statsContainer.add(Box.createVerticalStrut(5));

        topUsersModel = new DefaultListModel<>();
        topUsersList = new JList<>(topUsersModel);
        topUsersList.setBackground(new Color(45, 45, 45));
        topUsersList.setForeground(new Color(220, 220, 220));
        topUsersList.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        topUsersList.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        JScrollPane scrollPane = new JScrollPane(topUsersList);
        scrollPane.setBorder(null);
        scrollPane.setPreferredSize(new Dimension(200, 150));
        scrollPane.setMaximumSize(new Dimension(200, 200));
        statsContainer.add(scrollPane);

        add(statsContainer, BorderLayout.CENTER);
        
        JButton btnRefresh = new JButton("Refresh Stats");
        btnRefresh.addActionListener(e -> updateStats());
        add(btnRefresh, BorderLayout.SOUTH);
    }

    private JLabel createStatLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setForeground(new Color(200, 200, 200));
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        return lbl;
    }

    public void updateStats() {
        int v = service.getAllUsers().size();
        int e = service.getEdgeCount();
        
        lblNodes.setText("Nodes: " + v);
        lblEdges.setText("Edges: " + e);
        lblDensity.setText(String.format("Density: %.3f", service.getDensity()));
        lblAvgDegree.setText(String.format("Avg Degree: %.2f", service.getAverageDegree()));
        lblClustering.setText(String.format("Clustering: %.3f", service.averageClusteringCoefficient()));
        
        double modularity = CommunityDetection.calculateModularity(service.getAllUsers(), service.getAdjacencyList());
        lblModularity.setText(String.format("Modularity: %.3f", modularity));

        // Update Top Users list based on PageRank score
        topUsersModel.clear();
        List<Person> sorted = service.getAllUsers().stream()
                .sorted((u1, u2) -> Double.compare(u2.getMetrics().getPageRank(), u1.getMetrics().getPageRank()))
                .limit(5)
                .collect(Collectors.toList());

        for (int i = 0; i < sorted.size(); i++) {
            Person u = sorted.get(i);
            topUsersModel.addElement((i + 1) + ". " + u.getName()
                    + " (" + String.format("%.2f", u.getMetrics().getPageRank()) + ")");
        }
    }
}

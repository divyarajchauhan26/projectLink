package CampusConnect.main;
import CampusConnect.model.GraphModel;
import CampusConnect.model.UserNode;
import CampusConnect.ui.NetworkCanvas;

import javax.swing.*;
import java.awt.*;
public class Main{
    public static void main(String[] args) {
        GraphModel graph = new GraphModel();

        JFrame frame = new JFrame("Campus Connect - Admin God Mode");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLayout(new BorderLayout());

        NetworkCanvas canvas = new NetworkCanvas(graph);
        frame.add(canvas, BorderLayout.CENTER);

        JPanel controls = new JPanel();
        JButton btnAddUser = new JButton("Add User");
        JLabel hint = new JLabel(" | Click 2 nodes to connect | Hold CTRL + Drag to move");

        controls.add(btnAddUser);
        controls.add(hint);
        frame.add(controls, BorderLayout.SOUTH);

        // BUTTON LOGIC
        btnAddUser.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(frame, "Enter Student Name:");
            if (name != null && !name.trim().isEmpty()) {

                // SMART PLACEMENT LOOP
                int maxTries = 100;
                int attempts = 0;
                int x = 0, y = 0;
                boolean foundSpot = false;

                // Try to find a spot 100 times
                while (attempts < maxTries) {
                    x = 50 + (int)(Math.random() * (canvas.getWidth() - 100));
                    y = 50 + (int)(Math.random() * (canvas.getHeight() - 100));

                    if (graph.isLocationValid(x, y)) {
                        foundSpot = true;
                        break;
                    }
                    attempts++;
                }

                if (foundSpot) {
                    graph.addUser(new UserNode(name, name, x, y));
                    canvas.repaint();
                } else {
                    JOptionPane.showMessageDialog(frame, "Canvas is too crowded! Clear some space.");
                }
            }
        });

        frame.setVisible(true);
    }
}
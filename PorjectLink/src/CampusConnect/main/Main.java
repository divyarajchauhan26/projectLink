package CampusConnect.main;

import CampusConnect.ui.MainFrame;
import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        // 1. Setup Theme
        try {
            FlatDarkLaf.setup();
        } catch (Exception e) {
            System.err.println("FlatLaf not found. Using default look.");
        }

        // 2. Launch App
        SwingUtilities.invokeLater(() -> {
            new MainFrame().setVisible(true);
        });
    }
}
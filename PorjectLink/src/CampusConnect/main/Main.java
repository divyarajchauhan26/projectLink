package CampusConnect.main;

import javax.swing.SwingUtilities;

import CampusConnect.ui.MainFrame;
import com.formdev.flatlaf.FlatDarkLaf;

public class Main {
    public static void main(String[] args) {
        try { FlatDarkLaf.setup(); } catch (Exception e) {}
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
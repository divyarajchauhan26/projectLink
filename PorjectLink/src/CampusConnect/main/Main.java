package CampusConnect.main;

import javax.swing.SwingUtilities;

import CampusConnect.persist.InterestCatalogLoader;
import CampusConnect.ui.MainFrame;
import com.formdev.flatlaf.FlatDarkLaf;

public class Main {
    public static void main(String[] args) {
        try { FlatDarkLaf.setup(); } catch (Exception e) { }

        // Fold in any campus-specific interests before the UI can load a profile —
        // people hold InterestTag references, so the vocabulary must be settled first.
        String note = InterestCatalogLoader.installIfPresent();
        if (note != null) System.out.println(note);

        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}

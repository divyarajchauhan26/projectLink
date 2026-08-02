package CampusConnect.main;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Insets;

import CampusConnect.persist.InterestCatalogLoader;
import CampusConnect.ui.MainFrame;
import CampusConnect.ui.Theme;
import com.formdev.flatlaf.FlatDarkLaf;

public class Main {
    public static void main(String[] args) {
        try {
            FlatDarkLaf.setup();
            applyTheme();
        } catch (Exception e) { }

        // Fold in any campus-specific interests before the UI can load a profile —
        // people hold InterestTag references, so the vocabulary must be settled first.
        String note = InterestCatalogLoader.installIfPresent();
        if (note != null) System.out.println(note);

        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }

    /**
     * Push the palette into FlatLaf's defaults.
     * <p>
     * Panels we draw ourselves were already themed, but menus, dialogs, scrollbars and
     * text fields are rendered by the look and feel and kept FlatLaf's stock greys — so
     * the app was two slightly different dark themes sitting next to each other, which is
     * more noticeable than either would be on its own.
     */
    private static void applyTheme() {
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 8);
        UIManager.put("ProgressBar.arc", 8);
        UIManager.put("TextComponent.arc", 6);
        UIManager.put("ScrollBar.thumbArc", 8);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.width", 11);

        UIManager.put("Panel.background", Theme.PANEL);
        UIManager.put("MenuBar.background", Theme.PANEL);
        UIManager.put("MenuBar.borderColor", Theme.BORDER);
        UIManager.put("Menu.background", Theme.PANEL);
        UIManager.put("MenuItem.background", Theme.PANEL);
        UIManager.put("PopupMenu.background", Theme.ELEVATED);
        UIManager.put("MenuItem.selectionBackground", Theme.ACCENT_DEEP);
        UIManager.put("Menu.selectionBackground", Theme.ACCENT_DEEP);
        UIManager.put("OptionPane.background", Theme.PANEL);
        UIManager.put("ToolBar.background", Theme.PANEL);
        UIManager.put("ToolBar.borderColor", Theme.BORDER);
        UIManager.put("Separator.foreground", Theme.BORDER);
        UIManager.put("Component.borderColor", Theme.BORDER);
        UIManager.put("Button.background", Theme.ELEVATED);
        UIManager.put("ToggleButton.selectedBackground", Theme.ACCENT_DEEP);
        UIManager.put("TextField.background", Theme.ELEVATED);
        UIManager.put("List.background", Theme.ELEVATED);
        UIManager.put("List.selectionBackground", Theme.ACCENT_DEEP);
        UIManager.put("Slider.trackColor", Theme.BORDER);
        UIManager.put("Slider.thumbColor", Theme.ACCENT);
        UIManager.put("Label.foreground", Theme.TEXT);
        UIManager.put("defaultFont", new FontUIResource(Theme.FAMILY, java.awt.Font.PLAIN, 12));
    }
}

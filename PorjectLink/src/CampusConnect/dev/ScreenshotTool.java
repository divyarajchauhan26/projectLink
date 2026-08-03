package CampusConnect.dev;

import CampusConnect.ui.MainFrame;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;

/**
 * Captures the running app to PNGs for the documentation.
 * <p>
 * Screenshots taken by hand go stale the moment the UI changes and nobody remembers to
 * retake them, so the README ends up showing a version of the app that no longer exists.
 * Running the real window and driving it through reflection means the images can be
 * regenerated with one command whenever the interface moves.
 *
 * <pre>java -cp "build;lib/*" CampusConnect.dev.ScreenshotTool docs/images</pre>
 */
public class ScreenshotTool {

    public static void main(String[] args) throws Exception {
        String outDir = args.length > 0 ? args[0] : "docs/images";
        File dir = new File(outDir);
        if (!dir.exists() && !dir.mkdirs()) {
            System.err.println("Could not create " + dir.getAbsolutePath());
            System.exit(1);
        }

        com.formdev.flatlaf.FlatDarkLaf.setup();
        // Same UIManager tuning the real entry point applies, or the screenshots would
        // show FlatLaf's stock greys rather than the app's actual palette.
        Method applyTheme = Class.forName("CampusConnect.main.Main")
                .getDeclaredMethod("applyTheme");
        applyTheme.setAccessible(true);
        applyTheme.invoke(null);

        final MainFrame[] frame = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            frame[0] = new MainFrame();
            frame[0].setSize(1360, 880);
            frame[0].setLocation(40, 20);
            frame[0].setVisible(true);
        });

        // The campus loads on a later event-queue pass, and physics settles after that.
        Thread.sleep(2500);

        shoot(frame[0], dir, "01-discovery");

        invokeOnEdt(frame[0], "showSimilarityHeatmap");
        Thread.sleep(1400);
        shoot(frame[0], dir, "02-similarity-map");

        invokeOnEdt(frame[0], "showCircles");
        Thread.sleep(1400);
        shoot(frame[0], dir, "03-circles");

        invokeOnEdt(frame[0], "showFragility");
        Thread.sleep(1400);
        shoot(frame[0], dir, "04-fragility");

        invokeOnEdt(frame[0], "showMyRole");
        Thread.sleep(1200);
        shoot(frame[0], dir, "05-archetype");

        invokeOnEdt(frame[0], "showIsolated");
        Thread.sleep(1200);
        shoot(frame[0], dir, "06-isolated");

        invokeOnEdt(frame[0], "showMyMatches");
        Thread.sleep(1200);
        shoot(frame[0], dir, "07-matches");

        System.out.println("\nWrote screenshots to " + dir.getAbsolutePath());
        System.exit(0);
    }

    /** Call a private MainFrame method on the EDT, then let the animation settle. */
    private static void invokeOnEdt(MainFrame frame, String method) throws Exception {
        Method m = MainFrame.class.getDeclaredMethod(method);
        m.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try { m.invoke(frame); }
            catch (Exception e) { System.err.println("  ! " + method + ": " + e.getCause()); }
        });
    }

    /**
     * Render the window straight into an image.
     * <p>
     * Deliberately not {@code Robot.createScreenCapture}: that photographs whatever pixels
     * happen to be at those coordinates, and Windows refuses programmatic focus stealing,
     * so it reliably captures whichever application is genuinely in front. Painting the
     * component tree offscreen produces the window's own content no matter what is
     * covering it — and skips the OS title bar, which the documentation does not want.
     */
    private static void shoot(Window window, File dir, String name) throws Exception {
        final BufferedImage[] image = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            Container content = ((JFrame) window).getContentPane();
            BufferedImage img = new BufferedImage(
                    content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // printAll rather than paint: it renders the whole hierarchy synchronously and
            // does not schedule further repaints while we are mid-capture.
            content.printAll(g);
            // Toasts and other popups live on the layered pane, above the content pane.
            ((JFrame) window).getLayeredPane().printAll(g);
            g.dispose();
            image[0] = img;
        });

        File out = new File(dir, name + ".png");
        ImageIO.write(image[0], "png", out);
        System.out.printf("  %-22s %dx%d  %,d bytes%n",
                out.getName(), image[0].getWidth(), image[0].getHeight(), out.length());
    }
}

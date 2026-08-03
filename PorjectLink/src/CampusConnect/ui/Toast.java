package CampusConnect.ui;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Brief, non-blocking messages that appear over the canvas and fade out.
 * <p>
 * The app confirmed almost everything with a modal {@code JOptionPane} — "Graph saved",
 * "Communities detected", "Connected". A modal stops the world and demands a click to
 * acknowledge something the user just did on purpose, which is why the interface felt
 * heavy: the reward for every successful action was being interrupted.
 * <p>
 * Modals are still right for two cases and only two: a question whose answer changes what
 * happens next, and a destructive action that deserves a moment's pause. Everything else
 * belongs here.
 */
public final class Toast {

    public enum Kind { INFO, SUCCESS, WARNING, ERROR }

    private static final int VISIBLE_MS = 2600;
    private static final int FADE_MS = 320;
    private static final int MAX_STACK = 3;

    private final JLayeredPane layer;
    private final Deque<JComponent> live = new ArrayDeque<>();

    public Toast(JLayeredPane layer) {
        this.layer = layer;
    }

    public void info(String message)    { show(message, Kind.INFO); }
    public void success(String message) { show(message, Kind.SUCCESS); }
    public void warn(String message)    { show(message, Kind.WARNING); }
    public void error(String message)   { show(message, Kind.ERROR); }

    public void show(String message, Kind kind) {
        if (message == null || message.isBlank()) return;

        ToastPanel panel = new ToastPanel(message, kind);
        layer.add(panel, JLayeredPane.POPUP_LAYER);
        live.addLast(panel);

        // Cap the stack. Without this a burst of messages walks off the top of the window
        // and the newest — the one that matters — is the one you cannot see.
        while (live.size() > MAX_STACK) dismiss(live.pollFirst());

        reflow();

        Timer hold = new Timer(VISIBLE_MS, e -> fadeOut(panel));
        hold.setRepeats(false);
        hold.start();
    }

    private void fadeOut(JComponent panel) {
        if (!live.contains(panel)) return;
        final int steps = 12;
        final float[] alpha = {1f};
        Timer fade = new Timer(FADE_MS / steps, null);
        fade.addActionListener(e -> {
            alpha[0] -= 1f / steps;
            if (alpha[0] <= 0) {
                fade.stop();
                live.remove(panel);
                dismiss(panel);
                reflow();
            } else if (panel instanceof ToastPanel t) {
                t.setOpacityLevel(alpha[0]);
                t.repaint();
            }
        });
        fade.start();
    }

    private void dismiss(JComponent panel) {
        if (panel == null) return;
        layer.remove(panel);
        layer.repaint();
    }

    /** Stack upward from the bottom-left, above the status bar. */
    private void reflow() {
        int y = layer.getHeight() - 56;
        for (java.util.Iterator<JComponent> it = live.descendingIterator(); it.hasNext(); ) {
            JComponent panel = it.next();
            Dimension size = panel.getPreferredSize();
            panel.setBounds(20, y - size.height, size.width, size.height);
            y -= size.height + 8;
        }
        layer.repaint();
    }

    // ================= rendering =================

    private static final class ToastPanel extends JComponent {
        private final String message;
        private final Kind kind;
        private float opacity = 1f;

        ToastPanel(String message, Kind kind) {
            this.message = message;
            this.kind = kind;
            setFont(Theme.body(12));
        }

        void setOpacityLevel(float o) { this.opacity = Math.max(0, Math.min(1, o)); }

        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(Theme.body(12));
            return new Dimension(Math.min(460, fm.stringWidth(message) + 46), fm.getHeight() + 22);
        }

        private Color accent() {
            return switch (kind) {
                case SUCCESS -> Theme.SUCCESS;
                case WARNING -> Theme.WARNING;
                case ERROR   -> Theme.DANGER;
                case INFO    -> Theme.ACCENT;
            };
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));

            int w = getWidth(), h = getHeight();
            g2.setColor(Theme.ELEVATED);
            g2.fillRoundRect(0, 0, w, h, 10, 10);
            g2.setColor(Theme.BORDER);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);

            // A colour bar rather than a coloured background: the message stays readable
            // and the severity is still obvious at a glance.
            g2.setColor(accent());
            g2.fillRoundRect(0, 0, 4, h, 4, 4);

            g2.setFont(Theme.body(12));
            g2.setColor(Theme.TEXT);
            FontMetrics fm = g2.getFontMetrics();
            String text = message;
            // Clip rather than wrap — a toast that grows to three lines is a dialog.
            while (fm.stringWidth(text) > w - 34 && text.length() > 4) {
                text = text.substring(0, text.length() - 2);
            }
            if (!text.equals(message)) text = text + "…";
            g2.drawString(text, 16, (h + fm.getAscent()) / 2 - 2);

            g2.dispose();
        }
    }
}

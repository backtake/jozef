package com.jozef.overlay;

import com.jozef.configuration.OverlayProperties;
import lombok.extern.slf4j.Slf4j;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;

/**
 * Swing-based overlay: a small frameless, translucent, always-on-top window.
 *
 * All UI mutation happens on the Event Dispatch Thread; {@code show/hide} are
 * safe to call from any thread (they marshal onto the EDT).
 */
@Slf4j
public class SwingOverlay implements Overlay {

    private final OverlayProperties props;

    private volatile JWindow window;
    private volatile JLabel label;

    public SwingOverlay(OverlayProperties props) {
        this.props = props;
    }

    @Override
    public void show(OverlayState state, String detail) {
        if (state == OverlayState.IDLE) {
            hide();
            return;
        }
        SwingUtilities.invokeLater(() -> {
            ensureWindow();
            label.setText(buildHtml(state, detail));
            window.setVisible(true);
            window.toFront();
        });
    }

    @Override
    public void hide() {
        SwingUtilities.invokeLater(() -> {
            if (window != null) {
                window.setVisible(false);
            }
        });
    }

    @Override
    public boolean isVisible() {
        return window != null && window.isVisible();
    }

    private void ensureWindow() {
        if (window != null) {
            return;
        }
        window = new JWindow();
        window.setAlwaysOnTop(true);
        window.setType(Window.Type.UTILITY);
        window.setLayout(new BorderLayout());

        label = new JLabel();
        label.setOpaque(true);
        label.setBackground(new Color(15, 18, 25, 220));
        label.setForeground(Color.WHITE);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        label.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
        window.add(label, BorderLayout.CENTER);

        try {
            float opacity = (float) props.opacity();
            if (opacity > 0 && opacity <= 1.0f) {
                window.setOpacity(opacity);
            }
        } catch (Exception e) {
            log.debug("Window opacity not supported: {}", e.getMessage());
        }

        window.pack();
        position();
    }

    private void position() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension size = window.getSize();
        String pos = props.position() == null ? "top-right" : props.position();

        int margin = 24;
        int x;
        int y;
        switch (pos) {
            case "top-left" -> { x = margin; y = margin; }
            case "bottom-left" -> { x = margin; y = screen.height - size.height - margin; }
            case "bottom-right" -> { x = screen.width - size.width - margin; y = screen.height - size.height - margin; }
            case "center" -> { x = (screen.width - size.width) / 2; y = (screen.height - size.height) / 2; }
            default -> { x = screen.width - size.width - margin; y = margin; } // top-right
        }
        window.setLocation(x, y);
    }

    private String buildHtml(OverlayState state, String detail) {
        String color = colorFor(state);
        String text = labelFor(state);
        if (detail != null && !detail.isBlank()) {
            text += " — " + escape(detail);
        }
        return "<html><span style='color:" + color + ";'>&#9679;</span>&nbsp;&nbsp;"
                + escape(text) + "</html>";
    }

    private String labelFor(OverlayState state) {
        return switch (state) {
            case LISTENING -> "Słucham";
            case THINKING -> "Myślę";
            case SPEAKING -> "Mówię";
            case CONFIRM -> "Potwierdź";
            default -> "Jozef";
        };
    }

    private String colorFor(OverlayState state) {
        return switch (state) {
            case LISTENING -> "#22d3ee";
            case THINKING -> "#f59e0b";
            case SPEAKING -> "#22c55e";
            case CONFIRM -> "#ef4444";
            default -> "#94a3b8";
        };
    }

    private String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}

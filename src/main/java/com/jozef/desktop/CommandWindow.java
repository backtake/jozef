package com.jozef.desktop;

import com.jozef.core.agent.AgentResult;
import com.jozef.core.agent.AgentRuntime;
import com.jozef.speech.SpeechSynthesizer;
import com.jozef.speech.SpeechTextCleaner;
import lombok.extern.slf4j.Slf4j;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "Napisz do Jozefa": a small always-on-top command box opened with the global
 * hotkey or from the tray. Type a command, press Enter, read (and optionally
 * hear) the answer. Works without a microphone and without a console — the
 * everyday input for a background assistant.
 */
@Slf4j
public class CommandWindow {

    private static final int MAX_LINES = 300;
    private static final Color BG = new Color(17, 24, 39);
    private static final Color FG = new Color(229, 231, 235);
    private static final Color MUTED = new Color(156, 163, 175);
    private static final Color ACCENT = new Color(59, 130, 246);

    private final AgentRuntime agent;
    private final SpeechSynthesizer synthesizer;
    private final boolean speakReplies;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "jozef-command-window");
        t.setDaemon(true);
        return t;
    });

    private JFrame frame;
    private JTextField input;
    private JTextArea transcript;
    private JLabel status;

    public CommandWindow(AgentRuntime agent, SpeechSynthesizer synthesizer, boolean speakReplies) {
        this.agent = agent;
        this.synthesizer = synthesizer;
        this.speakReplies = speakReplies;
    }

    public static boolean isSupported() {
        return !GraphicsEnvironment.isHeadless();
    }

    /** Shows the window (or hides it when it is already focused). Safe from any thread. */
    public void toggle() {
        if (!isSupported()) return;
        SwingUtilities.invokeLater(() -> {
            if (frame == null) build();
            if (frame.isVisible() && frame.isFocused()) {
                frame.setVisible(false);
            } else {
                bringToFront();
            }
        });
    }

    /** Opens the window with an introductory message (first run). */
    public void showWelcome(String text) {
        if (!isSupported()) return;
        SwingUtilities.invokeLater(() -> {
            if (frame == null) build();
            transcript.setText(text + "\n");
            bringToFront();
        });
    }

    public void show() {
        if (!isSupported()) return;
        SwingUtilities.invokeLater(() -> {
            if (frame == null) build();
            bringToFront();
        });
    }

    /**
     * Windows refuses keyboard focus to a background process that did not receive the
     * last input (the global hotkey went to another app). A synthetic Alt tap is the
     * documented way to be allowed to come to the foreground; without it the box shows
     * up but typing goes to the previous window.
     */
    private void bringToFront() {
        frame.setVisible(true);
        frame.setExtendedState(JFrame.NORMAL);
        try {
            java.awt.Robot robot = new java.awt.Robot();
            robot.keyPress(KeyEvent.VK_ALT);
            robot.keyRelease(KeyEvent.VK_ALT);
        } catch (Exception ignored) {
            // no robot (headless/locked session) — best effort
        }
        frame.toFront();
        frame.requestFocus();
        input.requestFocusInWindow();
    }

    private void build() {
        frame = new JFrame("Jozef");
        frame.setAlwaysOnTop(true);
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { frame.setVisible(false); }
        });

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        transcript = new JTextArea(8, 48);
        transcript.setEditable(false);
        transcript.setLineWrap(true);
        transcript.setWrapStyleWord(true);
        transcript.setBackground(BG);
        transcript.setForeground(FG);
        transcript.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        transcript.setText("Napisz polecenie, np. \"otwórz YouTube\", \"co mam jutro w kalendarzu\", "
                + "\"przypomnij mi za 20 minut o praniu\". Esc chowa okno.\n");
        JScrollPane scroll = new JScrollPane(transcript);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(BG);

        input = new JTextField();
        input.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        input.setBackground(new Color(31, 41, 55));
        input.setForeground(FG);
        input.setCaretColor(FG);
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT, 1),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        input.addActionListener(e -> submit());
        input.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "hide");
        input.getActionMap().put("hide", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { frame.setVisible(false); }
        });

        status = new JLabel(" ");
        status.setForeground(MUTED);
        status.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        root.add(scroll, BorderLayout.CENTER);
        root.add(input, BorderLayout.SOUTH);
        root.add(status, BorderLayout.NORTH);
        frame.setContentPane(root);
        frame.setMinimumSize(new Dimension(520, 300));
        frame.pack();

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setLocation((screen.width - frame.getWidth()) / 2, screen.height / 4);
    }

    private void submit() {
        String text = input.getText().trim();
        if (text.isEmpty()) return;
        input.setText("");
        append("Ty: " + text);
        status.setText("Jozef myśli…");
        input.setEnabled(false);
        worker.submit(() -> {
            String reply;
            try {
                AgentResult result = agent.process(text);
                reply = result.text() == null || result.text().isBlank() ? "Gotowe." : result.text();
            } catch (Exception e) {
                log.error("Command failed", e);
                reply = "Coś poszło nie tak: " + e.getMessage();
            }
            String finalReply = reply;
            SwingUtilities.invokeLater(() -> {
                append("Jozef: " + finalReply);
                status.setText(agent.hasPendingConfirmation() ? "Czekam na odpowiedź: tak / nie" : " ");
                input.setEnabled(true);
                input.requestFocusInWindow();
            });
            if (speakReplies && synthesizer != null) {
                try {
                    synthesizer.speak(SpeechTextCleaner.clean(finalReply));
                } catch (Exception e) {
                    log.debug("Speaking reply failed: {}", e.getMessage());
                }
            }
        });
    }

    private void append(String line) {
        transcript.append("\n" + line + "\n");
        // Keep the box light: only the recent part of the conversation.
        int excess = transcript.getLineCount() - MAX_LINES;
        if (excess > 0) {
            try {
                transcript.replaceRange("", 0, transcript.getLineEndOffset(excess - 1));
            } catch (javax.swing.text.BadLocationException ignored) {
                // nothing to trim
            }
        }
        transcript.setCaretPosition(transcript.getDocument().getLength());
    }

    public void close() {
        worker.shutdownNow();
        if (frame != null) {
            SwingUtilities.invokeLater(frame::dispose);
        }
    }
}

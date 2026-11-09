package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.theme.DiscordTheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Small non-intrusive desktop popup notification for incoming chat messages.
 * Automatically dismisses after 5 seconds or upon user click.
 */
final class MessageToastNotification extends JWindow {
    private static final int TOAST_WIDTH = 340;
    private static final int TOAST_HEIGHT = 74;
    private static final int CORNER_RADIUS = 14;

    private static MessageToastNotification currentToast = null;

    private final Timer dismissTimer;
    private final Runnable onClickAction;

    private MessageToastNotification(Window owner, String initials, String seed,
                                     String title, String snippet, Runnable onClickAction) {
        super(owner);
        this.onClickAction = onClickAction;

        setAlwaysOnTop(true);
        setFocusableWindowState(false);
        try {
            setBackground(new Color(0, 0, 0, 0));
        } catch (Exception ignored) {
        }

        JPanel card = new JPanel(new BorderLayout(12, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x23, 0x24, 0x28));
                g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, CORNER_RADIUS, CORNER_RADIUS);
                g2.setColor(new Color(0x40, 0x44, 0x4B));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, CORNER_RADIUS, CORNER_RADIUS);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 12));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Left Avatar
        AvatarBadge avatar = new AvatarBadge(initials, seed, 40);
        JPanel avatarWrap = new JPanel(new GridBagLayout());
        avatarWrap.setOpaque(false);
        avatarWrap.add(avatar);
        card.add(avatarWrap, BorderLayout.WEST);

        // Center Content (Title & Message Snippet)
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(DiscordTheme.bold(13.5f));
        titleLabel.setForeground(DiscordTheme.TEXT_PRIMARY);
        titleLabel.setAlignmentX(0f);
        titleLabel.setMaximumSize(new Dimension(TOAST_WIDTH - 100, 22));

        JLabel snippetLabel = new JLabel(snippet);
        snippetLabel.setFont(DiscordTheme.font(12f));
        snippetLabel.setForeground(DiscordTheme.TEXT_SECONDARY);
        snippetLabel.setAlignmentX(0f);
        snippetLabel.setMaximumSize(new Dimension(TOAST_WIDTH - 100, 20));

        textPanel.add(Box.createVerticalGlue());
        textPanel.add(titleLabel);
        textPanel.add(Box.createVerticalStrut(3));
        textPanel.add(snippetLabel);
        textPanel.add(Box.createVerticalGlue());
        card.add(textPanel, BorderLayout.CENTER);

        // Right close button
        JLabel closeBtn = new JLabel("✕", SwingConstants.CENTER);
        closeBtn.setFont(DiscordTheme.bold(14f));
        closeBtn.setForeground(DiscordTheme.TEXT_MUTED);
        closeBtn.setPreferredSize(new Dimension(20, 20));
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                closeBtn.setForeground(DiscordTheme.TEXT_PRIMARY);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                closeBtn.setForeground(DiscordTheme.TEXT_MUTED);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                e.consume();
                dismiss();
            }
        });
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightPanel.setOpaque(false);
        rightPanel.add(closeBtn);
        card.add(rightPanel, BorderLayout.EAST);

        // Auto-dismiss timer (4.5s)
        dismissTimer = new Timer(4500, e -> dismiss());
        dismissTimer.setRepeats(false);

        // Clicking anywhere on card opens the chat window and conversation
        MouseAdapter clickToOpenAdapter = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                dismiss();
                if (onClickAction != null) {
                    onClickAction.run();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                dismissTimer.stop();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                dismissTimer.setInitialDelay(2500);
                dismissTimer.restart();
            }
        };

        card.addMouseListener(clickToOpenAdapter);
        textPanel.addMouseListener(clickToOpenAdapter);
        titleLabel.addMouseListener(clickToOpenAdapter);
        snippetLabel.addMouseListener(clickToOpenAdapter);
        avatarWrap.addMouseListener(clickToOpenAdapter);

        setContentPane(card);
        setSize(TOAST_WIDTH, TOAST_HEIGHT);
    }

    public static synchronized void showToast(Window owner, String initials, String seed,
                                              String title, String snippet, Runnable onClickAction) {
        SwingUtilities.invokeLater(() -> {
            if (currentToast != null) {
                currentToast.dismiss();
                currentToast = null;
            }

            MessageToastNotification toast = new MessageToastNotification(
                    owner, initials, seed, title, snippet, onClickAction
            );
            currentToast = toast;

            // Position at bottom-right of screen above taskbar
            try {
                GraphicsConfiguration gc = (owner != null && owner.getGraphicsConfiguration() != null)
                        ? owner.getGraphicsConfiguration()
                        : GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
                Rectangle bounds = gc.getBounds();
                Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
                int x = bounds.x + bounds.width - insets.right - TOAST_WIDTH - 20;
                int y = bounds.y + bounds.height - insets.bottom - TOAST_HEIGHT - 20;
                toast.setLocation(x, y);
            } catch (Exception e) {
                toast.setLocationRelativeTo(owner);
            }

            toast.setVisible(true);
            toast.dismissTimer.start();
        });
    }

    private void dismiss() {
        if (dismissTimer != null) {
            dismissTimer.stop();
        }
        setVisible(false);
        dispose();
        synchronized (MessageToastNotification.class) {
            if (currentToast == this) {
                currentToast = null;
            }
        }
    }
}

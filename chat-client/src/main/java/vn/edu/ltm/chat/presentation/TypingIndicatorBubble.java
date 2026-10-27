package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.theme.DiscordTheme;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Messenger / Discord styled animated typing indicator displaying 3 animated pulsing dots
 * and the user's name at the bottom of the conversation transcript.
 */
final class TypingIndicatorBubble extends JPanel {
    private final JLabel nameLabel = new JLabel();
    private final DotsPanel dotsPanel = new DotsPanel();
    private final JPanel avatarHolder = new JPanel(new BorderLayout());
    private final Timer animTimer;

    TypingIndicatorBubble() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 8, 4));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(4, 24, 6, 24));

        avatarHolder.setOpaque(false);
        add(avatarHolder);

        RoundedPanel bubble = new RoundedPanel(16);
        bubble.setBackground(new Color(0x38, 0x3A, 0x40));
        bubble.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 6));
        bubble.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        bubble.add(dotsPanel);
        add(bubble);

        nameLabel.setFont(DiscordTheme.font(11.5f));
        nameLabel.setForeground(DiscordTheme.TEXT_SECONDARY);
        add(nameLabel);

        this.animTimer = new Timer(220, e -> dotsPanel.advance());
        setVisible(false);
    }

    void showTyping(String senderName, String senderId, String initials) {
        avatarHolder.removeAll();
        AvatarBadge badge = new AvatarBadge(
                initials != null ? initials : (senderName != null && senderName.length() >= 2 ? senderName.substring(0, 2).toUpperCase() : "?"),
                senderId,
                24,
                null,
                DiscordTheme.TRANSCRIPT,
                true
        );
        avatarHolder.add(badge, BorderLayout.CENTER);
        nameLabel.setText(senderName + " đang soạn tin nhắn...");
        setVisible(true);
        if (!animTimer.isRunning()) {
            animTimer.start();
        }
        revalidate();
        repaint();
    }

    void hideTyping() {
        if (animTimer.isRunning()) {
            animTimer.stop();
        }
        setVisible(false);
        revalidate();
        repaint();
    }

    private static class DotsPanel extends JPanel {
        private int step = 0;

        DotsPanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(28, 14));
        }

        void advance() {
            step = (step + 1) % 3;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int dotSize = 5;
            int gap = 4;
            int startX = 2;
            int baseY = (getHeight() - dotSize) / 2;

            for (int i = 0; i < 3; i++) {
                int x = startX + i * (dotSize + gap);
                int y = baseY;
                if (i == step) {
                    y -= 2; // slight vertical bounce
                    g.setColor(Color.WHITE);
                } else {
                    g.setColor(new Color(0x94, 0x9B, 0xA4));
                }
                g.fillOval(x, y, dotSize, dotSize);
            }
            g.dispose();
        }
    }
}

package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.theme.DiscordTheme;
import vn.edu.ltm.chat.theme.IconFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rich embed card for Discord invite links (e.g. https://discord.gg/...).
 * Renders an authentic Discord-styled invite card with red "Invalid Invite" status.
 */
final class DiscordInviteCard extends RoundedPanel {
    private static final Pattern DISCORD_INVITE_PATTERN = Pattern.compile(
            "https?://(?:www\\.)?(?:discord\\.gg|discord(?:app)?\\.com/invite)/([a-zA-Z0-9_-]+)"
    );

    private static final Color CARD_BG = new Color(0x2B, 0x2D, 0x31);
    private static final Color CARD_BORDER = new Color(0x1E, 0x1F, 0x22);
    private static final Color RED_ICON_BG = new Color(0xDA, 0x37, 0x3C);
    private static final Color RED_TITLE = new Color(0xF2, 0x3F, 0x43);
    private static final Color TEXT_MUTED = new Color(0x94, 0x9B, 0xA4);

    public static boolean containsInvite(String text) {
        if (text == null || text.isBlank()) return false;
        return DISCORD_INVITE_PATTERN.matcher(text).find();
    }

    public static String extractInviteCode(String text) {
        if (text == null) return null;
        Matcher m = DISCORD_INVITE_PATTERN.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    DiscordInviteCard(String inviteCode) {
        super(10);
        setBackground(CARD_BG);
        setLayout(new BorderLayout(0, 10));
        setMaximumSize(new Dimension(440, 105));
        setPreferredSize(new Dimension(420, 95));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(inviteCode != null ? "discord.gg/" + inviteCode : "Discord Invite");

        setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(10, CARD_BORDER),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)
        ));

        // Subtitle: "You've been invited, but"
        JLabel subHeader = new JLabel("You've been invited, but");
        subHeader.setFont(DiscordTheme.font(11.5f));
        subHeader.setForeground(TEXT_MUTED);
        add(subHeader, BorderLayout.NORTH);

        // Content row: Red icon square on left, text column on right
        JPanel contentRow = new JPanel(new BorderLayout(12, 0));
        contentRow.setOpaque(false);

        // Red icon block (44x44)
        JPanel iconBlock = new RoundedPanel(8) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(44, 44);
            }
        };
        iconBlock.setBackground(RED_ICON_BG);
        iconBlock.setLayout(new BorderLayout());
        JLabel iconLabel = new JLabel(IconFactory.phosphor("envelope-simple-open.svg", 22, Color.WHITE), SwingConstants.CENTER);
        iconBlock.add(iconLabel, BorderLayout.CENTER);
        contentRow.add(iconBlock, BorderLayout.WEST);

        // Text details column
        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel("Invalid Invite");
        titleLabel.setFont(DiscordTheme.bold(14f));
        titleLabel.setForeground(RED_TITLE);

        JLabel descLabel = new JLabel("Ask for a new invite");
        descLabel.setFont(DiscordTheme.font(12f));
        descLabel.setForeground(TEXT_MUTED);

        textCol.add(Box.createVerticalGlue());
        textCol.add(titleLabel);
        textCol.add(Box.createVerticalStrut(2));
        textCol.add(descLabel);
        textCol.add(Box.createVerticalGlue());

        contentRow.add(textCol, BorderLayout.CENTER);
        add(contentRow, BorderLayout.CENTER);
    }

    private static final class RoundedBorder implements javax.swing.border.Border {
        private final int radius;
        private final Color color;

        RoundedBorder(int radius, Color color) {
            this.radius = radius;
            this.color = color;
        }

        @Override
        public void paintBorder(java.awt.Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g2.dispose();
        }

        @Override
        public java.awt.Insets getBorderInsets(java.awt.Component c) {
            return new java.awt.Insets(1, 1, 1, 1);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }
    }
}


package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.theme.DiscordTheme;
import vn.edu.ltm.chat.theme.IconFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;

/** Games view placeholder panel (Canvas 10). */
public final class GamePlaceholderPanel extends JPanel {
    private final Runnable onBackToChat;

    public GamePlaceholderPanel(Runnable onBackToChat) {
        this.onBackToChat = onBackToChat;

        setLayout(new BorderLayout());
        setBackground(DiscordTheme.TRANSCRIPT);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(DiscordTheme.TRANSCRIPT);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DiscordTheme.HAIRLINE),
                BorderFactory.createEmptyBorder(14, 24, 14, 24)
        ));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);

        JLabel icon = new JLabel(IconFactory.phosphor("game-controller.svg", 22, DiscordTheme.ACCENT_SOFT));
        left.add(icon);

        JLabel title = new JLabel("Trò chơi");
        title.setFont(DiscordTheme.bold(16f));
        title.setForeground(Color.WHITE);
        left.add(title);

        JLabel desc = new JLabel("· Những trò chơi để chơi cùng nhau");
        desc.setFont(DiscordTheme.font(13f));
        desc.setForeground(DiscordTheme.TEXT_MUTED);
        left.add(desc);

        header.add(left, BorderLayout.WEST);

        JButton backBtn = new JButton("Quay lại chat");
        backBtn.setFont(DiscordTheme.semibold(12f));
        backBtn.setForeground(DiscordTheme.TEXT_SECONDARY);
        backBtn.setContentAreaFilled(false);
        backBtn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        backBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        backBtn.addActionListener(e -> onBackToChat.run());
        header.add(backBtn, BorderLayout.EAST);

        return header;
    }

    private JPanel buildCenter() {
        JPanel host = new JPanel(new GridBagLayout());
        host.setOpaque(false);

        RoundedPanel card = new RoundedPanel(14);
        card.setBackground(DiscordTheme.SIDEBAR);
        card.setPreferredSize(new Dimension(500, 320));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x3B, 0x3D, 0x44), 1),
                BorderFactory.createEmptyBorder(28, 28, 28, 28)
        ));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel icon = new JLabel(IconFactory.phosphor("game-controller.svg", 52, DiscordTheme.ACCENT_SOFT));
        icon.setAlignmentX(CENTER_ALIGNMENT);
        card.add(icon);
        card.add(Box.createVerticalStrut(14));

        JLabel title = new JLabel("Trò chơi sẽ có ở đây");
        title.setFont(DiscordTheme.bold(18f));
        title.setForeground(Color.WHITE);
        title.setAlignmentX(CENTER_ALIGNMENT);
        card.add(title);
        card.add(Box.createVerticalStrut(8));

        WrappedTextArea desc = new WrappedTextArea(
                "Khu vực này sẽ dành cho những trò chơi mọi người có thể cùng tham gia khi tính năng được kết nối.",
                400
        );
        desc.setFont(DiscordTheme.font(13f));
        desc.setForeground(DiscordTheme.TEXT_SECONDARY);
        desc.setAlignmentX(CENTER_ALIGNMENT);
        card.add(desc);
        card.add(Box.createVerticalStrut(18));

        RoundedPanel badge = new RoundedPanel(6);
        badge.setBackground(DiscordTheme.SUNKEN);
        badge.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        badge.setLayout(new BorderLayout());
        JLabel badgeLabel = new JLabel("Khu vực trò chơi chưa được kết nối với hệ thống.", SwingConstants.CENTER);
        badgeLabel.setFont(DiscordTheme.font(11f));
        badgeLabel.setForeground(DiscordTheme.TEXT_MUTED);
        badge.add(badgeLabel, BorderLayout.CENTER);
        badge.setAlignmentX(CENTER_ALIGNMENT);
        card.add(badge);

        host.add(card);
        return host;
    }
}


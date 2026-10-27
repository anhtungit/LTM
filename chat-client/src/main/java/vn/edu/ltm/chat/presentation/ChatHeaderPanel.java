package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.theme.DiscordTheme;
import vn.edu.ltm.chat.theme.IconFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;

/**
 * Top header bar for the chat workspace displaying active conversation info and quick actions.
 */
final class ChatHeaderPanel extends JPanel {
    private final JPanel avatarHolder = new JPanel(new BorderLayout());
    private final JLabel titleLabel = new JLabel();
    private final JLabel subtitleLabel = new JLabel();
    private final JLabel connectionBadgeLabel = new JLabel();

    ChatHeaderPanel(Runnable onVoiceCall,
                    Runnable onVideoCall,
                    Runnable onInbox,
                    Runnable onToggleInfoPane) {
        super(new BorderLayout(12, 0));
        setBackground(DiscordTheme.TRANSCRIPT);
        setPreferredSize(new Dimension(0, 48));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DiscordTheme.HAIRLINE),
                BorderFactory.createEmptyBorder(0, 16, 0, 12)));

        JPanel identity = new JPanel(new BorderLayout(11, 0));
        identity.setOpaque(false);
        identity.setMinimumSize(new Dimension(0, 0));

        avatarHolder.setOpaque(false);
        identity.add(avatarHolder, BorderLayout.WEST);

        JPanel titleStack = new JPanel();
        titleStack.setOpaque(false);
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
        titleStack.setMinimumSize(new Dimension(0, 0));

        titleLabel.setFont(DiscordTheme.extrabold(15f));
        titleLabel.setForeground(DiscordTheme.TEXT_PRIMARY);
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));

        subtitleLabel.setFont(DiscordTheme.font(11f));
        subtitleLabel.setForeground(DiscordTheme.TEXT_SECONDARY);
        subtitleLabel.setAlignmentX(LEFT_ALIGNMENT);

        titleStack.add(titleLabel);
        titleStack.add(Box.createVerticalStrut(1));
        titleStack.add(subtitleLabel);
        identity.add(titleStack, BorderLayout.CENTER);
        add(identity, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.setOpaque(false);

        JButton callVoice = iconButton("phone.svg", "Gọi thoại · Vào sảnh gặp mặt");
        callVoice.addActionListener(e -> {
            if (onVoiceCall != null) onVoiceCall.run();
        });
        actions.add(callVoice);

        JButton callVideo = iconButton("video-camera.svg", "Gọi video · Vào phòng trực tiếp");
        callVideo.addActionListener(e -> {
            if (onVideoCall != null) onVideoCall.run();
        });
        actions.add(callVideo);

        JButton headerInbox = iconButton("envelope-simple-open.svg", "Hộp thư thông báo");
        headerInbox.addActionListener(e -> {
            if (onInbox != null) onInbox.run();
        });
        actions.add(headerInbox);

        JButton memberToggle = iconButton("users.svg", "Ẩn/hiện thông tin chi tiết");
        memberToggle.addActionListener(e -> {
            if (onToggleInfoPane != null) onToggleInfoPane.run();
        });
        actions.add(memberToggle);

        actions.add(Box.createHorizontalStrut(6));

        connectionBadgeLabel.setFont(DiscordTheme.extrabold(9f));
        actions.add(connectionBadgeLabel);

        add(actions, BorderLayout.EAST);
    }

    void update(String title, String subtitle, JComponent avatar) {
        titleLabel.setText(title);
        subtitleLabel.setText(subtitle != null ? subtitle : "");
        avatarHolder.removeAll();
        if (avatar != null) {
            avatarHolder.add(avatar, BorderLayout.CENTER);
        }
        avatarHolder.revalidate();
        avatarHolder.repaint();
    }

    void setTitle(String title) {
        titleLabel.setText(title);
    }

    void setSubtitle(String subtitle) {
        subtitleLabel.setText(subtitle != null ? subtitle : "");
    }

    void clearAvatar() {
        avatarHolder.removeAll();
        avatarHolder.revalidate();
        avatarHolder.repaint();
    }

    void refreshConnectionBadge(boolean online) {
        connectionBadgeLabel.setText(online ? "MÁY CHỦ: ĐÃ KẾT NỐI" : "DEMO CỤC BỘ");
        connectionBadgeLabel.setForeground(online ? DiscordTheme.SUCCESS : DiscordTheme.ACCENT_SOFT);
        connectionBadgeLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(online ? DiscordTheme.SUCCESS : new Color(0x4A, 0x50, 0x99)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        connectionBadgeLabel.setToolTipText(online
                ? "Đã tự động kết nối tới máy chủ chat (TCP)."
                : "Đang chạy chế độ ngoại tuyến; sẽ tự kết nối khi phát hiện máy chủ.");
    }

    private JButton iconButton(String iconName, String tooltip) {
        JButton button = new JButton(IconFactory.phosphor(iconName, 18, DiscordTheme.TEXT_SECONDARY));
        button.setToolTipText(tooltip);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(34, 34));
        button.setMargin(new java.awt.Insets(0, 0, 0, 0));
        return button;
    }
}

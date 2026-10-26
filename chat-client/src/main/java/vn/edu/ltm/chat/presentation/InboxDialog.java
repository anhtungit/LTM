package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.theme.DiscordTheme;
import vn.edu.ltm.chat.theme.IconFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Modal/flyout dialog for the Inbox notifications (Canvas 04). */
public final class InboxDialog extends JDialog {
    public record NotificationItem(
            String id,
            String senderInitials,
            String senderName,
            String location,
            String type,
            String snippet,
            boolean unread
    ) {}

    private static final class TrackingPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 64;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private final List<NotificationItem> items = new ArrayList<>();
    private final JPanel listPanel = new TrackingPanel();
    private final Consumer<NotificationItem> onSelectNotification;
    private boolean filterUnreadOnly = false;
    private final JLabel unreadCountLabel = new JLabel("3 mới");

    public InboxDialog(JFrame parent, Consumer<NotificationItem> onSelectNotification) {
        super(parent, "Hộp thư thông báo", true);
        this.onSelectNotification = onSelectNotification;

        initDefaultData();

        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));

        RoundedPanel container = new RoundedPanel(12);
        container.setBackground(DiscordTheme.SIDEBAR);
        container.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x3B, 0x3D, 0x44), 1),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)
        ));
        container.setLayout(new BorderLayout(0, 12));

        container.add(buildHeader(), BorderLayout.NORTH);
        container.add(buildContent(), BorderLayout.CENTER);
        container.add(buildFooter(), BorderLayout.SOUTH);

        setContentPane(container);
        setSize(480, 560);
        setLocationRelativeTo(parent);
    }

    private void initDefaultData() {
        items.add(new NotificationItem("1", "HM", "Hà My", "#hỏi-đáp", "Nhắc đến bạn",
                "@Lan Anh ơi, xem giúp mình flow đăng nhập với nhé. Mình vừa cập nhật màn hình…", true));
        items.add(new NotificationItem("2", "MK", "Minh Khoa", "Tin nhắn riêng", "Tin nhắn mới",
                "Mình gửi bạn file wireframe bản mới nhé.", true));
        items.add(new NotificationItem("3", "QT", "Quốc Thái", "#chia-sẻ", "Trả lời tin nhắn của bạn",
                "Đã tải ảnh lên, bạn xem thử nhé.", true));
        items.add(new NotificationItem("4", "HN", "Huyền Nguyễn", "#chung", "Nhắc đến bạn",
                "Lan ơi, tài liệu hướng dẫn đã cập nhật rồi.", false));
        items.add(new NotificationItem("5", "TD", "Thanh Duy", "Nhóm Thiết kế", "Tin nhắn mới",
                "Cảm ơn mọi người, mình đã nhận được.", false));
        items.add(new NotificationItem("6", "PL", "Phương Linh", "#hỏi-đáp", "Trả lời tin nhắn",
                "Đã rõ, mình sẽ gửi lại sau.", false));
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        JLabel title = new JLabel("Hộp thư");
        title.setFont(DiscordTheme.bold(17f));
        title.setForeground(Color.WHITE);
        left.add(title);

        unreadCountLabel.setFont(DiscordTheme.bold(11f));
        unreadCountLabel.setForeground(Color.WHITE);
        unreadCountLabel.setOpaque(true);
        unreadCountLabel.setBackground(DiscordTheme.ACCENT);
        unreadCountLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        left.add(unreadCountLabel);

        header.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);

        JButton markAllRead = new JButton("Đánh dấu đã đọc");
        markAllRead.setFont(DiscordTheme.semibold(11f));
        markAllRead.setForeground(DiscordTheme.ACCENT_SOFT);
        markAllRead.setContentAreaFilled(false);
        markAllRead.setBorderPainted(false);
        markAllRead.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        markAllRead.addActionListener(e -> markAllAsRead());
        right.add(markAllRead);

        JButton closeBtn = new JButton(IconFactory.phosphor("x.svg", 16, DiscordTheme.TEXT_MUTED));
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dispose());
        right.add(closeBtn);

        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel buildContent() {
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);

        // Filter tabs
        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tabs.setOpaque(false);

        JButton allTab = new JButton("Tất cả (6)");
        JButton unreadTab = new JButton("Chưa đọc (3)");

        styleTab(allTab, !filterUnreadOnly);
        styleTab(unreadTab, filterUnreadOnly);

        allTab.addActionListener(e -> {
            filterUnreadOnly = false;
            styleTab(allTab, true);
            styleTab(unreadTab, false);
            refreshList();
        });

        unreadTab.addActionListener(e -> {
            filterUnreadOnly = true;
            styleTab(allTab, false);
            styleTab(unreadTab, true);
            refreshList();
        });

        tabs.add(allTab);
        tabs.add(unreadTab);
        content.add(tabs, BorderLayout.NORTH);

        // List
        listPanel.setOpaque(false);
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        refreshList();

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        content.add(scroll, BorderLayout.CENTER);

        return content;
    }

    private void styleTab(JButton btn, boolean active) {
        btn.setFont(DiscordTheme.bold(12f));
        btn.setForeground(active ? Color.WHITE : DiscordTheme.TEXT_MUTED);
        btn.setBackground(active ? DiscordTheme.SELECTED : DiscordTheme.SUNKEN);
        btn.setContentAreaFilled(active);
        btn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void refreshList() {
        listPanel.removeAll();
        for (NotificationItem item : items) {
            if (filterUnreadOnly && !item.unread()) {
                continue;
            }
            listPanel.add(buildNotificationRow(item));
            listPanel.add(Box.createVerticalStrut(8));
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    private JPanel buildNotificationRow(NotificationItem item) {
        RoundedPanel row = new RoundedPanel(8);
        row.setBackground(item.unread() ? new Color(0x27, 0x29, 0x2D) : DiscordTheme.SIDEBAR);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(item.unread() ? DiscordTheme.ACCENT : new Color(0x2B, 0x2D, 0x31), 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        row.setLayout(new BorderLayout(10, 4));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        AvatarBadge avatar = new AvatarBadge(item.senderInitials(), item.senderName(), 32);
        row.add(avatar, BorderLayout.WEST);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        topRow.setOpaque(false);

        JLabel sender = new JLabel(item.senderName());
        sender.setFont(DiscordTheme.bold(12f));
        sender.setForeground(Color.WHITE);
        topRow.add(sender);

        JLabel loc = new JLabel("trong " + item.location());
        loc.setFont(DiscordTheme.font(11f));
        loc.setForeground(DiscordTheme.TEXT_MUTED);
        topRow.add(loc);

        center.add(topRow);

        JLabel type = new JLabel(item.type());
        type.setFont(DiscordTheme.semibold(10f));
        type.setForeground(DiscordTheme.ACCENT_SOFT);
        type.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        center.add(type);

        WrappedTextArea snippet = new WrappedTextArea(item.snippet(), 280);
        snippet.setFont(DiscordTheme.font(11f));
        snippet.setForeground(DiscordTheme.TEXT_SECONDARY);
        center.add(snippet);

        row.add(center, BorderLayout.CENTER);

        JButton openBtn = new JButton("Mở");
        openBtn.setFont(DiscordTheme.bold(11f));
        openBtn.setForeground(Color.WHITE);
        openBtn.setBackground(DiscordTheme.ACCENT);
        openBtn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        openBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        openBtn.addActionListener(e -> {
            if (onSelectNotification != null) {
                onSelectNotification.accept(item);
            }
            dispose();
        });
        JPanel rightHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightHolder.setOpaque(false);
        rightHolder.add(openBtn);
        row.add(rightHolder, BorderLayout.EAST);

        return row;
    }

    private void markAllAsRead() {
        for (int i = 0; i < items.size(); i++) {
            NotificationItem it = items.get(i);
            items.set(i, new NotificationItem(it.id(), it.senderInitials(), it.senderName(),
                    it.location(), it.type(), it.snippet(), false));
        }
        unreadCountLabel.setText("0 mới");
        unreadCountLabel.setBackground(DiscordTheme.TEXT_MUTED);
        refreshList();
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        JLabel note = new JLabel("Chọn thông báo để mở đúng tin nhắn trong hội thoại.", SwingConstants.CENTER);
        note.setFont(DiscordTheme.font(11f));
        note.setForeground(DiscordTheme.TEXT_MUTED);
        footer.add(note, BorderLayout.CENTER);
        return footer;
    }
}


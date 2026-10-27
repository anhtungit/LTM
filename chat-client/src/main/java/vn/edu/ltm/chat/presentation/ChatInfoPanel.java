package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.domain.Conversation;
import vn.edu.ltm.chat.theme.DiscordTheme;
import vn.edu.ltm.chat.theme.IconFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Sidebar information panel displaying details about the active conversation,
 * such as Discord-styled user profile card (banner, overlapping avatar, badges, bio)
 * and partitioned member presence lists (Trực tuyến / Ngoại tuyến) for group chats.
 */
final class ChatInfoPanel extends JPanel {
    static final int PANEL_WIDTH = 250;

    private static final Color[] BANNER_PALETTE = {
            new Color(0x58, 0x65, 0xF2), // Discord Blurple
            new Color(0x3B, 0xA5, 0x5D), // Emerald Green
            new Color(0xEB, 0x45, 0x9E), // Fuchsia Pink
            new Color(0xFD, 0xA8, 0x1A), // Solar Gold
            new Color(0x58, 0x35, 0x80), // Deep Amethyst
            new Color(0x00, 0xA8, 0xFC), // Sky Blue
            new Color(0x74, 0x7F, 0x8D)  // Slate
    };

    private final Function<String, String> displayNameResolver;
    private final Predicate<String> onlineResolver;
    private final Consumer<String> profileViewer;

    private final JPanel contentPanel = new JPanel();
    private final JScrollPane scrollPane;

    ChatInfoPanel(Function<String, String> displayNameResolver, Predicate<String> onlineResolver) {
        this(displayNameResolver, onlineResolver, null);
    }

    ChatInfoPanel(Function<String, String> displayNameResolver, Predicate<String> onlineResolver, Consumer<String> profileViewer) {
        this.displayNameResolver = Objects.requireNonNull(displayNameResolver);
        this.onlineResolver = Objects.requireNonNull(onlineResolver);
        this.profileViewer = profileViewer;

        setBackground(DiscordTheme.SIDEBAR);
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(PANEL_WIDTH, 0));
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, DiscordTheme.HAIRLINE));

        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);

        scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(14);
        add(scrollPane, BorderLayout.CENTER);
    }

    void update(Conversation conversation, String selectedChannel) {
        contentPanel.removeAll();
        if (conversation == null) {
            revalidate();
            repaint();
            return;
        }

        if (conversation.isGroup()) {
            buildGroupView(conversation, selectedChannel);
        } else {
            buildDmView(conversation);
        }

        revalidate();
        repaint();
    }

    void clear() {
        contentPanel.removeAll();
        revalidate();
        repaint();
    }

    static Color getBannerColor(String seed) {
        if (seed == null || seed.isBlank()) return BANNER_PALETTE[0];
        int index = Math.floorMod(seed.hashCode(), BANNER_PALETTE.length);
        return BANNER_PALETTE[index];
    }

    private void buildDmView(Conversation conversation) {
        JButton addFriendBtn = new JButton(IconFactory.phosphor("user-plus.svg", 13, Color.WHITE));
        addFriendBtn.setContentAreaFilled(false);
        addFriendBtn.setBorderPainted(false);
        addFriendBtn.setFocusPainted(false);
        addFriendBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addFriendBtn.setToolTipText("Thêm bạn bè");

        JButton bannerMoreBtn = new JButton(IconFactory.phosphor("dots-three.svg", 14, Color.WHITE));
        bannerMoreBtn.setContentAreaFilled(false);
        bannerMoreBtn.setBorderPainted(false);
        bannerMoreBtn.setFocusPainted(false);
        bannerMoreBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        bannerMoreBtn.setToolTipText("Tùy chọn khác");

        boolean isPeerOnline = onlineResolver.test(conversation.id());
        AvatarBadge avatar = new AvatarBadge(
                conversation.initials(),
                conversation.id(),
                68,
                isPeerOnline ? DiscordTheme.SUCCESS : null,
                DiscordTheme.SIDEBAR,
                true
        );

        // 1. Banner + Overlapping Avatar Header
        JPanel bannerHeader = new JPanel(null) {
            @Override
            public void doLayout() {
                super.doLayout();
                int w = getWidth();
                if (w <= 0) w = PANEL_WIDTH;
                bannerMoreBtn.setBounds(w - 32, 10, 24, 24);
                addFriendBtn.setBounds(w - 60, 10, 24, 24);
                avatar.setBounds(16, 32, 68, 68);
            }

            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bannerColor = getBannerColor(conversation.id());
                GradientPaint gp = new GradientPaint(0, 0, bannerColor, 0, 65, bannerColor.darker());
                g.setPaint(gp);
                g.fillRect(0, 0, getWidth(), 65);

                // Circular background cutout behind avatar for clean separation
                g.setColor(DiscordTheme.SIDEBAR);
                g.fillOval(12, 28, 76, 76);
                g.dispose();
            }
        };
        bannerHeader.setOpaque(false);
        bannerHeader.setPreferredSize(new Dimension(PANEL_WIDTH, 110));
        bannerHeader.setMaximumSize(new Dimension(PANEL_WIDTH, 110));
        bannerHeader.setMinimumSize(new Dimension(PANEL_WIDTH, 110));
        bannerHeader.setAlignmentX(0.0f);

        bannerHeader.add(avatar);
        bannerHeader.add(addFriendBtn);
        bannerHeader.add(bannerMoreBtn);
        contentPanel.add(bannerHeader);

        // 2. Profile Card Body Container
        int cardWidth = PANEL_WIDTH - 24;
        RoundedPanel card = new RoundedPanel(8);
        card.setBackground(new Color(0x23, 0x24, 0x28));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        card.setAlignmentX(0.5f);
        card.setMaximumSize(new Dimension(cardWidth, 32767));

        JLabel nameLabel = new JLabel(conversation.displayName());
        nameLabel.setFont(DiscordTheme.extrabold(15f));
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setAlignmentX(0.0f);
        card.add(nameLabel);

        JLabel handleLabel = new JLabel("@" + conversation.id().toLowerCase());
        handleLabel.setFont(DiscordTheme.font(12f));
        handleLabel.setForeground(DiscordTheme.TEXT_MUTED);
        handleLabel.setAlignmentX(0.0f);
        card.add(handleLabel);

        card.add(Box.createVerticalStrut(8));

        // Badges strip
        JPanel badgesRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        badgesRow.setOpaque(false);
        badgesRow.setAlignmentX(0.0f);
        badgesRow.add(buildBadgeLabel(IconFactory.phosphor("game-controller.svg", 14, new Color(0xFF, 0x73, 0xFA)), "Người đăng ký Nitro"));
        badgesRow.add(buildBadgeLabel(IconFactory.phosphor("compass.svg", 14, new Color(0xF0, 0xBB, 0x3B)), "HypeSquad Bravery"));
        badgesRow.add(buildBadgeLabel(IconFactory.phosphor("check.svg", 14, DiscordTheme.SUCCESS), "Tài khoản đã xác thực"));
        card.add(badgesRow);

        card.add(Box.createVerticalStrut(8));

        // Mutual info
        JPanel mutualRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        mutualRow.setOpaque(false);
        mutualRow.setAlignmentX(0.0f);
        JLabel mutualDot = new JLabel("•");
        mutualDot.setForeground(DiscordTheme.SUCCESS);
        mutualDot.setFont(DiscordTheme.bold(12f));
        mutualRow.add(mutualDot);
        JLabel mutualLabel = new JLabel("1 bạn chung • 2 máy chủ chung");
        mutualLabel.setFont(DiscordTheme.font(11f));
        mutualLabel.setForeground(DiscordTheme.TEXT_MUTED);
        mutualRow.add(mutualLabel);
        card.add(mutualRow);

        card.add(Box.createVerticalStrut(10));
        card.add(createDivider(cardWidth - 24));
        card.add(Box.createVerticalStrut(10));

        // Section "VỀ TÔI"
        JLabel aboutTitle = new JLabel("VỀ TÔI");
        aboutTitle.setFont(DiscordTheme.extrabold(10f));
        aboutTitle.setForeground(new Color(0xB5, 0xBA, 0xC1));
        aboutTitle.setAlignmentX(0.0f);
        card.add(aboutTitle);
        card.add(Box.createVerticalStrut(4));

        String bioText = (conversation.about() != null && !conversation.about().isBlank())
                ? conversation.about()
                : "Thích trò chuyện, nghe nhạc và lập trình cùng bạn bè. ☁️";
        WrappedTextArea bioArea = new WrappedTextArea(bioText, cardWidth - 24);
        bioArea.setFont(DiscordTheme.font(11f));
        bioArea.setForeground(new Color(0xDB, 0xDE, 0xE1));
        bioArea.setAlignmentX(0.0f);
        card.add(bioArea);

        card.add(Box.createVerticalStrut(12));

        // Section "THÀNH VIÊN DISCORD TỪ"
        JLabel memberSinceTitle = new JLabel("THÀNH VIÊN DISCORD TỪ");
        memberSinceTitle.setFont(DiscordTheme.extrabold(10f));
        memberSinceTitle.setForeground(new Color(0xB5, 0xBA, 0xC1));
        memberSinceTitle.setAlignmentX(0.0f);
        card.add(memberSinceTitle);
        card.add(Box.createVerticalStrut(4));

        JLabel memberDate = new JLabel("15 thg 4, 2024");
        memberDate.setFont(DiscordTheme.font(11f));
        memberDate.setForeground(new Color(0xDB, 0xDE, 0xE1));
        memberDate.setAlignmentX(0.0f);
        card.add(memberDate);

        card.add(Box.createVerticalStrut(12));
        card.add(createDivider(cardWidth - 24));
        card.add(Box.createVerticalStrut(12));

        // Button "Xem hồ sơ đầy đủ"
        JButton fullProfileBtn = new JButton("Xem hồ sơ đầy đủ");
        fullProfileBtn.setFont(DiscordTheme.semibold(12f));
        fullProfileBtn.setForeground(Color.WHITE);
        fullProfileBtn.setBackground(new Color(0x2B, 0x2D, 0x31));
        fullProfileBtn.setFocusPainted(false);
        fullProfileBtn.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
        fullProfileBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        fullProfileBtn.setMaximumSize(new Dimension(cardWidth - 24, 32));
        fullProfileBtn.setAlignmentX(0.0f);
        fullProfileBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                fullProfileBtn.setBackground(new Color(0x35, 0x37, 0x3C));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                fullProfileBtn.setBackground(new Color(0x2B, 0x2D, 0x31));
            }
        });
        fullProfileBtn.addActionListener(e -> {
            if (profileViewer != null) {
                profileViewer.accept(conversation.id());
            }
        });
        card.add(fullProfileBtn);

        JPanel cardHolder = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        cardHolder.setOpaque(false);
        cardHolder.setAlignmentX(0.0f);
        cardHolder.add(card);
        contentPanel.add(cardHolder);
        contentPanel.add(Box.createVerticalStrut(16));
    }

    private void buildGroupView(Conversation conversation, String selectedChannel) {
        // Channel Info Header
        JPanel groupHeader = new JPanel();
        groupHeader.setLayout(new BoxLayout(groupHeader, BoxLayout.Y_AXIS));
        groupHeader.setOpaque(false);
        groupHeader.setBorder(BorderFactory.createEmptyBorder(14, 14, 10, 14));
        groupHeader.setAlignmentX(0.0f);

        JLabel chanTitle = new JLabel("KÊNH HIỆN TẠI");
        chanTitle.setFont(DiscordTheme.extrabold(10f));
        chanTitle.setForeground(DiscordTheme.TEXT_SECONDARY);
        chanTitle.setAlignmentX(0.0f);
        groupHeader.add(chanTitle);
        groupHeader.add(Box.createVerticalStrut(4));

        JLabel hashName = new JLabel("# " + (selectedChannel != null ? selectedChannel : "chung"));
        hashName.setFont(DiscordTheme.bold(14f));
        hashName.setForeground(Color.WHITE);
        hashName.setAlignmentX(0.0f);
        groupHeader.add(hashName);

        if (conversation.about() != null && !conversation.about().isBlank()) {
            groupHeader.add(Box.createVerticalStrut(4));
            WrappedTextArea about = new WrappedTextArea(conversation.about(), PANEL_WIDTH - 28);
            about.setFont(DiscordTheme.font(11f));
            about.setForeground(new Color(0xC6, 0xC7, 0xCD));
            about.setAlignmentX(0.0f);
            groupHeader.add(about);
        }
        contentPanel.add(groupHeader);
        contentPanel.add(createDivider(PANEL_WIDTH - 28));

        // Partition members into Online and Offline
        List<String> members = conversation.members();
        List<String> onlineMembers = new ArrayList<>();
        List<String> offlineMembers = new ArrayList<>();
        for (String m : members) {
            if (onlineResolver.test(m)) {
                onlineMembers.add(m);
            } else {
                offlineMembers.add(m);
            }
        }

        if (!onlineMembers.isEmpty()) {
            contentPanel.add(Box.createVerticalStrut(10));
            JLabel onlineHeader = new JLabel("TRỰC TUYẾN — " + onlineMembers.size());
            onlineHeader.setFont(DiscordTheme.extrabold(10f));
            onlineHeader.setForeground(DiscordTheme.TEXT_SECONDARY);
            onlineHeader.setBorder(BorderFactory.createEmptyBorder(0, 14, 4, 14));
            onlineHeader.setAlignmentX(0.0f);
            contentPanel.add(onlineHeader);

            for (String member : onlineMembers) {
                contentPanel.add(buildMemberRow(member, true));
            }
        }

        if (!offlineMembers.isEmpty()) {
            contentPanel.add(Box.createVerticalStrut(12));
            JLabel offlineHeader = new JLabel("NGOẠI TUYẾN — " + offlineMembers.size());
            offlineHeader.setFont(DiscordTheme.extrabold(10f));
            offlineHeader.setForeground(DiscordTheme.TEXT_MUTED);
            offlineHeader.setBorder(BorderFactory.createEmptyBorder(0, 14, 4, 14));
            offlineHeader.setAlignmentX(0.0f);
            contentPanel.add(offlineHeader);

            for (String member : offlineMembers) {
                contentPanel.add(buildMemberRow(member, false));
            }
        }

        contentPanel.add(Box.createVerticalStrut(16));
    }

    private JPanel buildMemberRow(String member, boolean isOnline) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        row.setMaximumSize(new Dimension(PANEL_WIDTH, 36));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.setAlignmentX(0.0f);

        String displayName = displayNameResolver.apply(member);
        String initials = computeInitials(displayName);
        Color presence = isOnline ? DiscordTheme.SUCCESS : null;
        AvatarBadge avatar = new AvatarBadge(initials, member, 26, presence, DiscordTheme.SIDEBAR, isOnline);
        row.add(avatar, BorderLayout.WEST);

        JLabel nameLabel = new JLabel(displayName);
        nameLabel.setFont(isOnline ? DiscordTheme.semibold(12f) : DiscordTheme.font(12f));
        nameLabel.setForeground(isOnline ? DiscordTheme.TEXT_PRIMARY : DiscordTheme.TEXT_MUTED);
        row.add(nameLabel, BorderLayout.CENTER);

        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                row.setOpaque(true);
                row.setBackground(new Color(0x35, 0x37, 0x3C));
                row.repaint();
            }
            @Override
            public void mouseExited(MouseEvent e) {
                row.setOpaque(false);
                row.repaint();
            }
            @Override
            public void mouseClicked(MouseEvent e) {
                if (profileViewer != null) {
                    profileViewer.accept(member);
                }
            }
        });

        return row;
    }

    private JLabel buildBadgeLabel(javax.swing.Icon icon, String tooltip) {
        JLabel label = new JLabel(icon);
        label.setToolTipText(tooltip);
        label.setOpaque(true);
        label.setBackground(new Color(0x1E, 0x1F, 0x22));
        label.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
        return label;
    }

    private JPanel createDivider(int width) {
        JPanel line = new JPanel();
        line.setBackground(new Color(0x35, 0x36, 0x3C));
        line.setPreferredSize(new Dimension(width, 1));
        line.setMaximumSize(new Dimension(width, 1));
        line.setAlignmentX(0.5f);
        return line;
    }

    private static String computeInitials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }
}

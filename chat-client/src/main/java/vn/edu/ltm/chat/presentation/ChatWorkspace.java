package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.application.ChatService;
import vn.edu.ltm.chat.domain.ChatMessage;
import vn.edu.ltm.chat.domain.Conversation;
import vn.edu.ltm.chat.domain.UserProfile;
import vn.edu.ltm.chat.theme.DiscordTheme;
import vn.edu.ltm.chat.theme.IconFactory;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discord-like desktop workspace supporting:
 * - Dynamic rail listing all created group servers
 * - Group channels creation and deletion (+ button and delete icon)
 * - Independent chat history per channel (not shared)
 * - Rich categorized emoji and sticker picker
 * - File attachments via JFileChooser or mock success/fail (Canvas 09)
 * - Inline edit/recall/delete (Canvas 02)
 * - Inbox notifications (Canvas 04)
 * - Meet lobby & room (Canvas 05, 06)
 * - Profile dialog (Canvas 07)
 * - Games panel (Canvas 10)
 */
public final class ChatWorkspace extends JPanel {
    private static final int MAX_CONTENT_WIDTH = 780;
    private static final int AVATAR_SIZE = 34;
    private static final int AVATAR_GAP = 12;
    private static final int SIDEBAR_WIDTH = 240;
    private static final int INFO_WIDTH = 220;

    private UserProfile user;
    private final ChatService chatService;
    private final Runnable onLogout;
    private final List<Conversation> conversations;
    private final Map<String, String> drafts = new HashMap<>();
    private final Map<String, List<ChatMessage>> messageCache = new ConcurrentHashMap<>();

    // Rail navigation
    private final JPanel rail = new JPanel();
    private String activeRailGroupId = "dm"; // "dm" or conversationId of group

    // Main workspace center switching
    private final CardLayout centerLayout = new CardLayout();
    private final JPanel centerHost = new JPanel(centerLayout);

    // Sidebar switching: "dm" or "group"
    private final CardLayout sidebarLayout = new CardLayout();
    private final JPanel sidebarHost = new JPanel(sidebarLayout);

    // DM sidebar components
    private final JPanel directMessagesList = new JPanel();
    private final JPanel groupMessagesList = new JPanel();
    private final JTextField searchField = new JTextField();

    // Group sidebar components
    private final JPanel groupChannelsPanel = new JPanel();
    private final JLabel groupSidebarTitle = new JLabel("Nhóm");

    // Chat transcript & composer
    private final JPanel transcript = new JPanel();
    private final JScrollPane transcriptScroll = new JScrollPane(transcript);
    private final RichComposerPane composer = new RichComposerPane(this::sendCurrentMessage);
    private final JScrollPane composerScroll = new JScrollPane(composer);
    private final JLabel conversationTitle = new JLabel();
    private final JLabel conversationSubtitle = new JLabel();
    private final JLabel composerHint = new JLabel("Bản nháp riêng cho cuộc trò chuyện này");
    private final JPanel headerAvatarHolder = new JPanel(new BorderLayout());
    private final JPanel infoPane = new JPanel();
    private final JButton sendButton = new JButton();
    private final JLabel connectionBadgeLabel = new JLabel();

    private final JPanel accountFooterDm = new JPanel(new BorderLayout());
    private final JPanel accountFooterGroup = new JPanel(new BorderLayout());

    private String selectedConversationId;
    private String selectedChannel = "học-tập";
    private String filter = "";

    private boolean loadingConversations = false;

    public ChatWorkspace(UserProfile user, ChatService chatService, Runnable onLogout) {
        this.user = user;
        this.chatService = chatService;
        this.onLogout = onLogout;
        boolean isConnected = chatService.isServerConnected();
        this.loadingConversations = isConnected;
        this.conversations = new ArrayList<>(isConnected
                ? Collections.emptyList()
                : chatService.conversations());

        setLayout(new BorderLayout());
        setBackground(DiscordTheme.TRANSCRIPT);

        initCenterViews();

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(buildSidebarHost(), BorderLayout.WEST);
        body.add(centerHost, BorderLayout.CENTER);

        add(buildRail(), BorderLayout.WEST);
        add(body, BorderLayout.CENTER);

        setMinimumSize(new Dimension(960, 620));
        installSearchShortcut();

        if (!conversations.isEmpty()) {
            selectConversation(conversations.getFirst());
        } else {
            rebuildConversationButtons();
            renderEmptyWorkspaceState();
        }

        this.chatService.setOnRealtimeUpdate(this::handleServerBroadcast);
        if (isConnected) {
            handleServerBroadcast();
        }
    }

    private void handleServerBroadcast() {
        refreshConnectionBadge();
        CompletableFuture.supplyAsync(() -> {
            try {
                return chatService.conversations();
            } catch (Exception e) {
                return Collections.<Conversation>emptyList();
            }
        }).thenAccept(latest -> SwingUtilities.invokeLater(() -> {
            loadingConversations = false;
            if (!latest.isEmpty()) {
                conversations.clear();
                conversations.addAll(latest);
                rebuildRail();
                rebuildConversationButtons();
            } else {
                rebuildConversationButtons();
            }
            if (selectedConversationId == null && !conversations.isEmpty()) {
                selectConversation(conversations.getFirst());
                return;
            }
            Conversation conv = currentConversation();
            if (conv != null) {
                if (conv.isGroup()) {
                    rebuildGroupChannels(conv);
                }
                rebuildInfoPane(conv);
                rebuildTranscript();
            } else {
                renderEmptyWorkspaceState();
            }
        }));
    }

    private void initCenterViews() {
        centerHost.setOpaque(false);
        centerHost.add(buildChatArea(), "chat");
        centerHost.add(new MeetLobbyPanel(this::showMeetRoom, this::showChatView), "meet-lobby");
        centerHost.add(new MeetRoomPanel(this::showChatView), "meet-room");
        centerHost.add(new GamePlaceholderPanel(this::showChatView), "game");
    }

    private void showChatView() {
        centerLayout.show(centerHost, "chat");
    }

    private void showMeetLobby() {
        centerLayout.show(centerHost, "meet-lobby");
    }

    private void showMeetRoom() {
        centerLayout.show(centerHost, "meet-room");
    }

    private void showGames() {
        centerLayout.show(centerHost, "game");
    }

    // ---------------------------------------------------------------- rail

    private JComponent buildRail() {
        rail.setLayout(new BoxLayout(rail, BoxLayout.Y_AXIS));
        rail.setBackground(DiscordTheme.RAIL);
        rail.setBorder(BorderFactory.createEmptyBorder(14, 6, 14, 6));
        rail.setPreferredSize(new Dimension(72, 0));

        rebuildRail();
        return rail;
    }

    private void rebuildRail() {
        rail.removeAll();

        // 1. Direct Messages Tile
        boolean isDmActive = "dm".equals(activeRailGroupId);
        RailTile dmTile = new RailTile(null,
                IconFactory.phosphor("chat-circle-dots.svg", 25, Color.WHITE),
                isDmActive, true, 48);
        dmTile.setToolTipText("Tin nhắn trực tiếp");
        dmTile.addActionListener(e -> {
            activeRailGroupId = "dm";
            sidebarLayout.show(sidebarHost, "dm-sidebar");
            showChatView();
            for (Conversation c : conversations) {
                if (!c.isGroup()) {
                    selectConversation(c);
                    break;
                }
            }
            rebuildRail();
        });
        rail.add(railEntry(dmTile, isDmActive));
        rail.add(Box.createVerticalStrut(10));
        rail.add(railDivider());
        rail.add(Box.createVerticalStrut(10));

        // 2. Dynamic Group Tiles for all group servers
        for (Conversation conv : conversations) {
            if (conv.isGroup()) {
                boolean isActiveGroup = conv.id().equals(activeRailGroupId);
                RailTile groupTile = new RailTile(conv.initials(), null, isActiveGroup, true, 48);
                groupTile.setToolTipText(conv.displayName() + " · Bấm để vào nhóm");
                groupTile.addActionListener(e -> {
                    activeRailGroupId = conv.id();
                    selectConversation(conv);
                    sidebarLayout.show(sidebarHost, "group-sidebar");
                    showChatView();
                    rebuildRail();
                });
                rail.add(railEntry(groupTile, isActiveGroup));
                rail.add(Box.createVerticalStrut(8));
            }
        }

        // 3. Add Group Tile (+)
        RailTile addTile = new RailTile(null,
                IconFactory.phosphor("plus.svg", 20, DiscordTheme.SUCCESS), false, false, 48);
        addTile.setToolTipText("Tạo nhóm mới");
        addTile.addActionListener(e -> openCreateGroupDialog());
        rail.add(railEntry(addTile, false));

        rail.add(Box.createVerticalGlue());
        rail.revalidate();
        rail.repaint();
    }

    private JComponent railEntry(JComponent tile, boolean active) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(CENTER_ALIGNMENT);
        row.setMaximumSize(new Dimension(60, 48));

        JComponent pill = new JPanel();
        pill.setOpaque(false);
        if (active) {
            RoundedPanel marker = new RoundedPanel(4);
            marker.setBackground(DiscordTheme.TEXT_PRIMARY);
            pill = marker;
        }
        Dimension pillSize = new Dimension(4, 32);
        pill.setPreferredSize(pillSize);
        pill.setMinimumSize(pillSize);
        pill.setMaximumSize(pillSize);
        row.add(pill);
        row.add(Box.createHorizontalStrut(8));
        row.add(tile);
        return row;
    }

    private JComponent railDivider() {
        JPanel divider = new JPanel();
        divider.setBackground(new Color(0x34, 0x36, 0x3B));
        divider.setAlignmentX(CENTER_ALIGNMENT);
        divider.setPreferredSize(new Dimension(32, 1));
        divider.setMaximumSize(new Dimension(32, 1));
        divider.setMinimumSize(new Dimension(32, 1));
        return divider;
    }

    // ------------------------------------------------------------- sidebars

    private JComponent buildSidebarHost() {
        sidebarHost.setOpaque(false);
        sidebarHost.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 0));
        sidebarHost.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, DiscordTheme.HAIRLINE));

        sidebarHost.add(buildDmSidebar(), "dm-sidebar");
        sidebarHost.add(buildGroupSidebar(), "group-sidebar");
        sidebarLayout.show(sidebarHost, "dm-sidebar");
        rebuildAccountFooters();

        return sidebarHost;
    }

    private JComponent buildDmSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(DiscordTheme.SIDEBAR);

        // Header
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBackground(DiscordTheme.SIDEBAR);
        header.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 54));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DiscordTheme.SUNKEN),
                BorderFactory.createEmptyBorder(0, 14, 0, 10)));

        JLabel title = new JLabel("Tin nhắn trực tiếp");
        title.setFont(DiscordTheme.extrabold(15f));
        title.setForeground(DiscordTheme.TEXT_PRIMARY);
        header.add(title, BorderLayout.CENTER);

        JButton createGroupBtn = iconButton("user-plus.svg", "Tạo nhóm mới",
                18, DiscordTheme.TEXT_SECONDARY);
        createGroupBtn.addActionListener(event -> openCreateGroupDialog());
        header.add(createGroupBtn, BorderLayout.EAST);
        sidebar.add(header, BorderLayout.NORTH);

        // Content
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(DiscordTheme.SIDEBAR);
        content.setBorder(BorderFactory.createEmptyBorder(12, 10, 8, 10));

        content.add(searchBar());
        content.add(Box.createVerticalStrut(10));
        content.add(inboxRow());
        content.add(Box.createVerticalStrut(14));

        // Group section in DM sidebar
        JPanel groupSectionHeader = new JPanel(new BorderLayout());
        groupSectionHeader.setOpaque(false);
        groupSectionHeader.setAlignmentX(LEFT_ALIGNMENT);
        groupSectionHeader.add(sectionLabel("NHÓM GẦN ĐÂY"), BorderLayout.WEST);

        JButton plusGroup = new JButton(IconFactory.phosphor("plus.svg", 14, DiscordTheme.TEXT_MUTED));
        plusGroup.setContentAreaFilled(false);
        plusGroup.setBorderPainted(false);
        plusGroup.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        plusGroup.setToolTipText("Tạo nhóm mới");
        plusGroup.addActionListener(e -> openCreateGroupDialog());
        groupSectionHeader.add(plusGroup, BorderLayout.EAST);

        content.add(groupSectionHeader);
        content.add(Box.createVerticalStrut(4));

        groupMessagesList.setLayout(new BoxLayout(groupMessagesList, BoxLayout.Y_AXIS));
        groupMessagesList.setOpaque(false);
        groupMessagesList.setAlignmentX(LEFT_ALIGNMENT);
        content.add(groupMessagesList);

        content.add(Box.createVerticalStrut(14));

        JPanel dmSectionHeader = new JPanel(new BorderLayout());
        dmSectionHeader.setOpaque(false);
        dmSectionHeader.setAlignmentX(LEFT_ALIGNMENT);
        dmSectionHeader.add(sectionLabel("TIN NHẮN TRỰC TIẾP"), BorderLayout.WEST);

        JButton plusDm = new JButton(IconFactory.phosphor("plus.svg", 14, DiscordTheme.TEXT_MUTED));
        plusDm.setContentAreaFilled(false);
        plusDm.setBorderPainted(false);
        plusDm.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        plusDm.setToolTipText("Bắt đầu tin nhắn trực tiếp mới");
        plusDm.addActionListener(e -> openStartDmDialog());
        dmSectionHeader.add(plusDm, BorderLayout.EAST);

        content.add(dmSectionHeader);
        content.add(Box.createVerticalStrut(4));

        directMessagesList.setLayout(new BoxLayout(directMessagesList, BoxLayout.Y_AXIS));
        directMessagesList.setOpaque(false);
        directMessagesList.setAlignmentX(LEFT_ALIGNMENT);
        content.add(directMessagesList);

        JPanel contentHost = new JPanel(new BorderLayout());
        contentHost.setOpaque(false);
        contentHost.add(content, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(contentHost);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        sidebar.add(scroll, BorderLayout.CENTER);

        sidebar.add(accountFooterDm, BorderLayout.SOUTH);
        return sidebar;
    }

    private JComponent buildGroupSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(DiscordTheme.SIDEBAR);

        // Header
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBackground(DiscordTheme.SIDEBAR);
        header.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 54));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DiscordTheme.SUNKEN),
                BorderFactory.createEmptyBorder(0, 14, 0, 14)));

        groupSidebarTitle.setFont(DiscordTheme.extrabold(15f));
        groupSidebarTitle.setForeground(DiscordTheme.TEXT_PRIMARY);
        header.add(groupSidebarTitle, BorderLayout.WEST);

        JLabel caret = new JLabel(IconFactory.phosphor("caret-down.svg", 16, DiscordTheme.TEXT_SECONDARY));
        header.add(caret, BorderLayout.EAST);
        sidebar.add(header, BorderLayout.NORTH);

        // Channels container
        groupChannelsPanel.setLayout(new BoxLayout(groupChannelsPanel, BoxLayout.Y_AXIS));
        groupChannelsPanel.setBackground(DiscordTheme.SIDEBAR);
        groupChannelsPanel.setBorder(BorderFactory.createEmptyBorder(12, 10, 8, 10));

        JPanel channelsHost = new JPanel(new BorderLayout());
        channelsHost.setOpaque(false);
        channelsHost.add(groupChannelsPanel, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(channelsHost);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        sidebar.add(scroll, BorderLayout.CENTER);

        sidebar.add(accountFooterGroup, BorderLayout.SOUTH);
        return sidebar;
    }

    private void rebuildGroupChannels(Conversation group) {
        groupChannelsPanel.removeAll();
        groupSidebarTitle.setText(group.displayName());

        // 1. Text channels header with (+) button to add channel
        JPanel textHeader = new JPanel(new BorderLayout());
        textHeader.setOpaque(false);
        textHeader.setAlignmentX(LEFT_ALIGNMENT);
        textHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        textHeader.setPreferredSize(new Dimension(SIDEBAR_WIDTH - 20, 24));
        textHeader.add(sectionLabel("KÊNH VĂN BẢN"), BorderLayout.WEST);

        JButton addChanBtn = new JButton(IconFactory.phosphor("plus.svg", 14, DiscordTheme.TEXT_MUTED));
        addChanBtn.setContentAreaFilled(false);
        addChanBtn.setBorderPainted(false);
        addChanBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addChanBtn.setToolTipText("Tạo kênh văn bản mới");
        addChanBtn.addActionListener(e -> promptAddTextChannel(group));
        textHeader.add(addChanBtn, BorderLayout.EAST);

        groupChannelsPanel.add(textHeader);
        groupChannelsPanel.add(Box.createVerticalStrut(4));

        for (String chan : group.channels()) {
            boolean active = chan.equals(selectedChannel);
            RoundedPanel row = new RoundedPanel(4);
            row.setLayout(new BorderLayout(6, 0));
            row.setOpaque(true);
            row.setBackground(active ? DiscordTheme.SELECTED : DiscordTheme.SIDEBAR);
            row.setAlignmentX(LEFT_ALIGNMENT);
            row.setPreferredSize(new Dimension(SIDEBAR_WIDTH - 20, 34));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            row.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 6));
            row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            left.setOpaque(false);
            JLabel hashIcon = new JLabel(IconFactory.phosphor("hash.svg", 15,
                    active ? Color.WHITE : DiscordTheme.TEXT_MUTED));
            left.add(hashIcon);

            JLabel nameLbl = new JLabel(chan);
            nameLbl.setFont(active ? DiscordTheme.bold(13f) : DiscordTheme.semibold(13f));
            nameLbl.setForeground(active ? Color.WHITE : new Color(0xAE, 0xB0, 0xB6));
            left.add(nameLbl);
            row.add(left, BorderLayout.CENTER);

            if (group.channels().size() > 1) {
                JButton delBtn = new JButton(IconFactory.phosphor("trash.svg", 13, DiscordTheme.TEXT_MUTED));
                delBtn.setContentAreaFilled(false);
                delBtn.setBorderPainted(false);
                delBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                delBtn.setToolTipText("Xóa kênh #" + chan);
                delBtn.addActionListener(e -> {
                    int confirm = JOptionPane.showConfirmDialog(this,
                            "Bạn có chắc muốn xóa kênh #" + chan + "?", "Xác nhận xóa kênh",
                            JOptionPane.YES_NO_OPTION);
                    if (confirm == JOptionPane.YES_OPTION) {
                        chatService.removeChannel(group.id(), chan);
                        Conversation updated = group.withRemovedChannel(chan);
                        updateConversationInList(updated);
                        if (selectedChannel.equals(chan)) {
                            selectedChannel = updated.channels().getFirst();
                        }
                        selectConversation(updated);
                    }
                });
                row.add(delBtn, BorderLayout.EAST);
            }

            row.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (!active) {
                        row.setBackground(DiscordTheme.HOVER);
                        row.repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (!active) {
                        row.setBackground(DiscordTheme.SIDEBAR);
                        row.repaint();
                    }
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    selectedChannel = chan;
                    conversationTitle.setText("#" + selectedChannel);
                    showChatView();
                    rebuildTranscript();
                    rebuildInfoPane(group);
                    rebuildGroupChannels(group);
                }
            });

            groupChannelsPanel.add(row);
            groupChannelsPanel.add(Box.createVerticalStrut(2));
        }

        groupChannelsPanel.add(Box.createVerticalStrut(14));

        // 2. Voice channels header with (+) button to add voice room
        JPanel voiceHeader = new JPanel(new BorderLayout());
        voiceHeader.setOpaque(false);
        voiceHeader.setAlignmentX(LEFT_ALIGNMENT);
        voiceHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        voiceHeader.setPreferredSize(new Dimension(SIDEBAR_WIDTH - 20, 24));
        voiceHeader.add(sectionLabel("KÊNH GẶP"), BorderLayout.WEST);

        JButton addVoiceBtn = new JButton(IconFactory.phosphor("plus.svg", 14, DiscordTheme.TEXT_MUTED));
        addVoiceBtn.setContentAreaFilled(false);
        addVoiceBtn.setBorderPainted(false);
        addVoiceBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addVoiceBtn.setToolTipText("Tạo kênh gặp mới");
        addVoiceBtn.addActionListener(e -> promptAddVoiceChannel(group));
        voiceHeader.add(addVoiceBtn, BorderLayout.EAST);

        groupChannelsPanel.add(voiceHeader);
        groupChannelsPanel.add(Box.createVerticalStrut(4));

        for (String vChan : group.voiceChannels()) {
            RoundedPanel vRow = new RoundedPanel(4);
            vRow.setLayout(new BorderLayout(6, 0));
            vRow.setOpaque(true);
            vRow.setBackground(DiscordTheme.SIDEBAR);
            vRow.setAlignmentX(LEFT_ALIGNMENT);
            vRow.setPreferredSize(new Dimension(SIDEBAR_WIDTH - 20, 34));
            vRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            vRow.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 6));
            vRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JPanel vLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            vLeft.setOpaque(false);
            JLabel vIcon = new JLabel(IconFactory.phosphor("headset.svg", 15, DiscordTheme.SUCCESS));
            vLeft.add(vIcon);

            JLabel vName = new JLabel(vChan);
            vName.setFont(DiscordTheme.semibold(13f));
            vName.setForeground(new Color(0xAE, 0xB0, 0xB6));
            vLeft.add(vName);
            vRow.add(vLeft, BorderLayout.CENTER);

            if (group.voiceChannels().size() > 1) {
                JButton delVBtn = new JButton(IconFactory.phosphor("trash.svg", 13, DiscordTheme.TEXT_MUTED));
                delVBtn.setContentAreaFilled(false);
                delVBtn.setBorderPainted(false);
                delVBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                delVBtn.setToolTipText("Xóa kênh gặp " + vChan);
                delVBtn.addActionListener(e -> {
                    chatService.removeVoiceChannel(group.id(), vChan);
                    Conversation updated = group.withRemovedVoiceChannel(vChan);
                    updateConversationInList(updated);
                    rebuildGroupChannels(updated);
                });
                vRow.add(delVBtn, BorderLayout.EAST);
            }

            vRow.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    vRow.setBackground(DiscordTheme.HOVER);
                    vRow.repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    vRow.setBackground(DiscordTheme.SIDEBAR);
                    vRow.repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    showMeetLobby();
                }
            });

            groupChannelsPanel.add(vRow);
            groupChannelsPanel.add(Box.createVerticalStrut(2));
        }

        groupChannelsPanel.add(Box.createVerticalStrut(14));
        groupChannelsPanel.add(sectionLabel("TRÒ CHƠI"));
        groupChannelsPanel.add(Box.createVerticalStrut(4));

        JButton gameBtn = new JButton("Trò chơi · Chưa tích hợp");
        gameBtn.setFont(DiscordTheme.semibold(13f));
        gameBtn.setForeground(new Color(0xAE, 0xB0, 0xB6));
        gameBtn.setContentAreaFilled(false);
        gameBtn.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
        gameBtn.setAlignmentX(LEFT_ALIGNMENT);
        gameBtn.setHorizontalAlignment(SwingConstants.LEFT);
        gameBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        gameBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        gameBtn.addActionListener(e -> showGames());
        groupChannelsPanel.add(gameBtn);

        groupChannelsPanel.revalidate();
        groupChannelsPanel.repaint();
    }

    private void promptAddTextChannel(Conversation group) {
        String name = JOptionPane.showInputDialog(this, "Nhập tên kênh văn bản mới (không dấu, ví dụ: thao-luan):",
                "Tạo kênh văn bản", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            String sanitized = name.trim().toLowerCase().replaceAll("\\s+", "-");
            chatService.addChannel(group.id(), sanitized);
            Conversation updated = group.withAddedChannel(sanitized);
            updateConversationInList(updated);
            selectedChannel = sanitized;
            selectConversation(updated);
        }
    }

    private void promptAddVoiceChannel(Conversation group) {
        String name = JOptionPane.showInputDialog(this, "Nhập tên kênh gặp mới:",
                "Tạo kênh gặp", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            String sanitized = name.trim();
            chatService.addVoiceChannel(group.id(), sanitized);
            Conversation updated = group.withAddedVoiceChannel(sanitized);
            updateConversationInList(updated);
            rebuildGroupChannels(updated);
        }
    }

    private void updateConversationInList(Conversation updated) {
        for (int i = 0; i < conversations.size(); i++) {
            if (conversations.get(i).id().equals(updated.id())) {
                conversations.set(i, updated);
                break;
            }
        }
    }

    private JComponent searchBar() {
        RoundedPanel shell = new RoundedPanel(6);
        shell.setBackground(DiscordTheme.RAIL);
        shell.setLayout(new BorderLayout(6, 0));
        shell.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 6));
        shell.setAlignmentX(LEFT_ALIGNMENT);
        shell.setPreferredSize(new Dimension(SIDEBAR_WIDTH - 20, 34));
        shell.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        JLabel icon = new JLabel(IconFactory.phosphor("magnifying-glass.svg", 15, DiscordTheme.TEXT_MUTED));
        shell.add(icon, BorderLayout.WEST);

        searchField.setBorder(BorderFactory.createEmptyBorder());
        searchField.setOpaque(false);
        searchField.setForeground(DiscordTheme.TEXT_PRIMARY);
        searchField.setCaretColor(DiscordTheme.TEXT_PRIMARY);
        searchField.setFont(DiscordTheme.font(12f));
        searchField.putClientProperty("JTextField.placeholderText", "Tìm kiếm");
        searchField.getAccessibleContext().setAccessibleName("Tìm cuộc trò chuyện trực tiếp");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { updateFilter(); }
            @Override public void removeUpdate(DocumentEvent event) { updateFilter(); }
            @Override public void changedUpdate(DocumentEvent event) { updateFilter(); }
        });
        shell.add(searchField, BorderLayout.CENTER);

        JLabel shortcut = new JLabel("Ctrl K");
        shortcut.setOpaque(true);
        shortcut.setBackground(new Color(0x25, 0x26, 0x2A));
        shortcut.setForeground(DiscordTheme.TEXT_MUTED);
        shortcut.setFont(DiscordTheme.semibold(10f));
        shortcut.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
        shell.add(shortcut, BorderLayout.EAST);
        return shell;
    }

    private JComponent inboxRow() {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        row.setPreferredSize(new Dimension(SIDEBAR_WIDTH - 20, 34));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        row.add(new JLabel(IconFactory.phosphor("envelope-simple-open.svg", 18, DiscordTheme.TEXT_SECONDARY)),
                BorderLayout.WEST);
        JLabel label = new JLabel("Hộp thư");
        label.setFont(DiscordTheme.semibold(13f));
        label.setForeground(DiscordTheme.TEXT_SECONDARY);
        row.add(label, BorderLayout.CENTER);

        JLabel inboxBadge = new JLabel(" 3 ");
        inboxBadge.setFont(DiscordTheme.bold(10f));
        inboxBadge.setForeground(Color.WHITE);
        inboxBadge.setOpaque(true);
        inboxBadge.setBackground(DiscordTheme.ACCENT);
        inboxBadge.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        row.add(inboxBadge, BorderLayout.EAST);

        row.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                openInboxDialog();
            }
        });
        return row;
    }

    private JComponent sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(DiscordTheme.extrabold(10f));
        label.setForeground(DiscordTheme.TEXT_MUTED);
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        return label;
    }

    private void rebuildAccountFooters() {
        accountFooterDm.removeAll();
        accountFooterDm.add(createAccountBar(), BorderLayout.CENTER);
        accountFooterDm.revalidate();
        accountFooterDm.repaint();

        accountFooterGroup.removeAll();
        accountFooterGroup.add(createAccountBar(), BorderLayout.CENTER);
        accountFooterGroup.revalidate();
        accountFooterGroup.repaint();
    }

    private JComponent createAccountBar() {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBackground(DiscordTheme.SIDEBAR);

        // Voice state card
        RoundedPanel voiceState = new RoundedPanel(6);
        voiceState.setBackground(DiscordTheme.SUNKEN);
        voiceState.setLayout(new BoxLayout(voiceState, BoxLayout.Y_AXIS));
        voiceState.setBorder(BorderFactory.createEmptyBorder(8, 10, 9, 10));
        voiceState.setAlignmentX(CENTER_ALIGNMENT);
        voiceState.setMaximumSize(new Dimension(SIDEBAR_WIDTH - 16, 58));
        voiceState.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel stateTitle = new JPanel(new BorderLayout(8, 0));
        stateTitle.setOpaque(false);
        stateTitle.setAlignmentX(LEFT_ALIGNMENT);
        stateTitle.add(new JLabel(IconFactory.phosphor("headset.svg", 16, DiscordTheme.SUCCESS)),
                BorderLayout.WEST);
        JLabel state = new JLabel("Kênh gặp · Sẵn sàng");
        state.setFont(DiscordTheme.bold(11f));
        state.setForeground(new Color(0xC5, 0xC6, 0xCD));
        stateTitle.add(state, BorderLayout.CENTER);
        voiceState.add(stateTitle);
        voiceState.add(Box.createVerticalStrut(3));

        JLabel stateSubtext = new JLabel("Bấm để mở sảnh gặp mặt");
        stateSubtext.setFont(DiscordTheme.font(10f));
        stateSubtext.setForeground(DiscordTheme.TEXT_MUTED);
        stateSubtext.setAlignmentX(LEFT_ALIGNMENT);
        padRight(stateSubtext);
        voiceState.add(stateSubtext);

        voiceState.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                showMeetLobby();
            }
        });

        JPanel voiceHolder = new JPanel(new BorderLayout());
        voiceHolder.setOpaque(false);
        voiceHolder.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        voiceHolder.add(voiceState, BorderLayout.CENTER);
        wrapper.add(voiceHolder);

        // User account bar
        JPanel account = new JPanel(new BorderLayout(8, 0));
        account.setBackground(DiscordTheme.ACCOUNT_BAR);
        account.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 8));
        account.setMaximumSize(new Dimension(SIDEBAR_WIDTH, 58));
        account.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 58));
        account.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        AvatarBadge avatar = new AvatarBadge(user.initials(), user.id(), 32,
                user.statusColor(), DiscordTheme.ACCOUNT_BAR, true, user.avatarPath());
        account.add(avatar, BorderLayout.WEST);

        JPanel userInfo = new JPanel();
        userInfo.setOpaque(false);
        userInfo.setLayout(new BoxLayout(userInfo, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(user.displayName());
        name.setFont(DiscordTheme.bold(12f));
        name.setForeground(DiscordTheme.TEXT_PRIMARY);
        userInfo.add(name);
        JLabel local = new JLabel(user.username() + " · " + user.statusLabel());
        local.setFont(DiscordTheme.font(10f));
        local.setForeground(DiscordTheme.TEXT_MUTED);
        padRight(local);
        userInfo.add(local);
        account.add(userInfo, BorderLayout.CENTER);

        account.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                openProfileDialog();
            }
        });

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        rightBtns.setOpaque(false);

        JButton gear = iconButton("gear.svg", "Cài đặt hồ sơ (Canvas 07)", 16, DiscordTheme.TEXT_SECONDARY);
        gear.addActionListener(e -> openProfileDialog());
        rightBtns.add(gear);

        JButton logout = iconButton("sign-out.svg", "Đăng xuất demo", 16, DiscordTheme.TEXT_SECONDARY);
        logout.addActionListener(event -> onLogout.run());
        rightBtns.add(logout);

        account.add(rightBtns, BorderLayout.EAST);
        wrapper.add(account);
        return wrapper;
    }

    // ----------------------------------------------------------- chat area

    private JComponent buildChatArea() {
        JPanel chatArea = new JPanel(new BorderLayout());
        chatArea.setBackground(DiscordTheme.TRANSCRIPT);

        JPanel messageColumn = new JPanel(new BorderLayout());
        messageColumn.setOpaque(false);
        messageColumn.add(buildChatHeader(), BorderLayout.NORTH);
        messageColumn.add(buildTranscriptArea(), BorderLayout.CENTER);
        messageColumn.add(buildComposer(), BorderLayout.SOUTH);

        chatArea.add(messageColumn, BorderLayout.CENTER);
        chatArea.add(buildInfoPane(), BorderLayout.EAST);
        return chatArea;
    }

    private JComponent buildChatHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(DiscordTheme.TRANSCRIPT);
        header.setPreferredSize(new Dimension(0, 62));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DiscordTheme.HAIRLINE),
                BorderFactory.createEmptyBorder(0, 18, 0, 14)));

        JPanel identity = new JPanel(new BorderLayout(11, 0));
        identity.setOpaque(false);

        headerAvatarHolder.setOpaque(false);
        identity.add(headerAvatarHolder, BorderLayout.WEST);

        JPanel titleStack = new JPanel();
        titleStack.setOpaque(false);
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
        conversationTitle.setFont(DiscordTheme.extrabold(15f));
        conversationTitle.setForeground(DiscordTheme.TEXT_PRIMARY);
        conversationTitle.setAlignmentX(LEFT_ALIGNMENT);
        padRight(conversationTitle);
        conversationSubtitle.setFont(DiscordTheme.font(11f));
        conversationSubtitle.setForeground(DiscordTheme.TEXT_SECONDARY);
        conversationSubtitle.setAlignmentX(LEFT_ALIGNMENT);
        titleStack.add(conversationTitle);
        titleStack.add(Box.createVerticalStrut(1));
        titleStack.add(conversationSubtitle);
        identity.add(titleStack, BorderLayout.CENTER);
        header.add(identity, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.setOpaque(false);

        JButton callVoice = iconButton("phone.svg", "Gọi thoại · Vào sảnh gặp mặt", 18, DiscordTheme.TEXT_SECONDARY);
        callVoice.addActionListener(e -> showMeetLobby());
        actions.add(callVoice);

        JButton callVideo = iconButton("video-camera.svg", "Gọi video · Vào phòng trực tiếp", 18, DiscordTheme.TEXT_SECONDARY);
        callVideo.addActionListener(e -> showMeetRoom());
        actions.add(callVideo);

        JButton headerInbox = iconButton("envelope-simple-open.svg", "Hộp thư thông báo (Canvas 04)", 18, DiscordTheme.TEXT_SECONDARY);
        headerInbox.addActionListener(e -> openInboxDialog());
        actions.add(headerInbox);

        actions.add(Box.createHorizontalStrut(6));
        actions.add(connectionBadge());
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JComponent connectionBadge() {
        connectionBadgeLabel.setFont(DiscordTheme.extrabold(9f));
        refreshConnectionBadge();
        return connectionBadgeLabel;
    }

    private void refreshConnectionBadge() {
        boolean online = chatService.isServerConnected();
        connectionBadgeLabel.setText(online ? "TRỰC TUYẾN" : "DEMO CỤC BỘ");
        connectionBadgeLabel.setForeground(online ? DiscordTheme.SUCCESS : DiscordTheme.ACCENT_SOFT);
        connectionBadgeLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(online ? DiscordTheme.SUCCESS : new Color(0x4A, 0x50, 0x99)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        connectionBadgeLabel.setToolTipText(online
                ? "Đã tự động kết nối tới máy chủ chat (TCP/SQLite)."
                : "Đang chạy chế độ ngoại tuyến; sẽ tự kết nối khi phát hiện máy chủ.");
    }

    private JComponent buildTranscriptArea() {
        JPanel area = new JPanel(new BorderLayout());
        area.setBackground(DiscordTheme.TRANSCRIPT);

        area.add(buildDemoBanner(), BorderLayout.NORTH);

        transcript.setLayout(new ColumnLayout(MAX_CONTENT_WIDTH, AVATAR_SIZE + AVATAR_GAP));
        transcript.setBackground(DiscordTheme.TRANSCRIPT);
        transcript.setBorder(BorderFactory.createEmptyBorder(14, 24, 8, 24));

        transcriptScroll.setBorder(null);
        transcriptScroll.setOpaque(false);
        transcriptScroll.getViewport().setOpaque(false);
        transcriptScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        transcriptScroll.getVerticalScrollBar().setUnitIncrement(18);
        area.add(transcriptScroll, BorderLayout.CENTER);
        return area;
    }

    private JComponent buildDemoBanner() {
        JPanel banner = new JPanel(new BorderLayout(9, 0));
        banner.setBackground(DiscordTheme.BANNER);
        banner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DiscordTheme.HAIRLINE),
                BorderFactory.createEmptyBorder(9, 18, 9, 18)));

        JLabel icon = new JLabel(IconFactory.phosphor("info.svg", 16, DiscordTheme.TEXT_SECONDARY));
        banner.add(icon, BorderLayout.WEST);

        JLabel text = new JLabel("Phiên demo: hỗ trợ gửi tệp thực tế/mẫu, emoji/sticker, sửa/xoá tin, nhóm & kênh gặp.");
        text.setFont(DiscordTheme.font(11f));
        text.setForeground(DiscordTheme.TEXT_SECONDARY);
        banner.add(text, BorderLayout.CENTER);
        return banner;
    }

    private JComponent buildComposer() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(DiscordTheme.TRANSCRIPT);
        wrapper.setBorder(BorderFactory.createEmptyBorder(4, 24, 12, 24));

        RoundedPanel shell = new RoundedPanel(10);
        shell.setBackground(DiscordTheme.COMPOSER);
        shell.setLayout(new BorderLayout());

        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setOpaque(false);
        inputRow.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 8));

        // Attach button with real FileChooser & mock options
        JButton attach = iconButton("plus-circle.svg", "Đính kèm tệp", 22, DiscordTheme.TEXT_SECONDARY);
        attach.addActionListener(e -> showAttachMenu(attach));
        inputRow.add(attach, BorderLayout.WEST);

        composer.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { adjustComposerHeight(); }
            @Override public void removeUpdate(DocumentEvent event) { adjustComposerHeight(); }
            @Override public void changedUpdate(DocumentEvent event) { adjustComposerHeight(); }
        });
        composer.getAccessibleContext().setAccessibleName("Soạn tin nhắn");

        composerScroll.setBorder(null);
        composerScroll.setOpaque(false);
        composerScroll.getViewport().setOpaque(false);
        composerScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        composerScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        composerScroll.setPreferredSize(new Dimension(0, 32));
        inputRow.add(composerScroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        actions.setOpaque(false);

        // Emoji & Sticker button opening rich EmojiPickerPopup
        JButton emojiBtn = iconButton("smiley.svg", "Emoji & Sticker", 19, DiscordTheme.TEXT_SECONDARY);
        emojiBtn.addActionListener(e -> {
            EmojiPickerPopup popup = new EmojiPickerPopup(
                    selectedEmoji -> {
                        composer.insertEmoji(selectedEmoji);
                        adjustComposerHeight();
                    },
                    selectedSticker -> sendTextAsync("[Sticker: " + selectedSticker + "]")
            );
            popup.showAbove(emojiBtn);
        });
        actions.add(emojiBtn);

        actions.add(buildSendButton());
        inputRow.add(actions, BorderLayout.EAST);
        shell.add(inputRow, BorderLayout.NORTH);

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0x3A, 0x3C, 0x42)),
                BorderFactory.createEmptyBorder(5, 10, 6, 10)));
        composerHint.setFont(DiscordTheme.font(10f));
        composerHint.setForeground(DiscordTheme.TEXT_MUTED);
        padRight(composerHint);
        footer.add(composerHint, BorderLayout.CENTER);
        JLabel keys = new JLabel("Enter để gửi · Shift + Enter xuống dòng");
        keys.setFont(DiscordTheme.font(10f));
        keys.setForeground(DiscordTheme.TEXT_MUTED);
        keys.setHorizontalAlignment(SwingConstants.RIGHT);
        padRight(keys);
        footer.add(keys, BorderLayout.EAST);
        shell.add(footer, BorderLayout.SOUTH);

        wrapper.add(centeredColumn(shell), BorderLayout.CENTER);
        return wrapper;
    }

    private void showAttachMenu(JButton source) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem chooseFile = new JMenuItem("Chọn tệp từ máy tính...");
        chooseFile.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Chọn tệp đính kèm để gửi");
            int res = chooser.showOpenDialog(this);
            if (res == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                sendAttachmentAsync(file.getAbsolutePath(), file.getName(), file.length(), false);
            }
        });
        menu.add(chooseFile);

        menu.addSeparator();

        JMenuItem sampleFile = new JMenuItem("Gửi tệp mẫu thành công (PDF 2.4 MB)");
        sampleFile.addActionListener(e -> {
            sendAttachmentAsync("Tong_hop_bai_giang.pdf", "Tong_hop_bai_giang.pdf", 2_400_000L, false);
        });
        menu.add(sampleFile);

        JMenuItem failFile = new JMenuItem("Gửi tệp mẫu thất bại (Màn 09 demo)");
        failFile.addActionListener(e -> {
            sendAttachmentAsync("architecture-chat-flow-v2.png", "architecture-chat-flow-v2.png", 2_400_000L, true);
        });
        menu.add(failFile);

        menu.show(source, 0, source.getHeight());
    }

    private JButton buildSendButton() {
        sendButton.setIcon(IconFactory.phosphor("paper-plane-tilt.svg", 17, Color.WHITE));
        sendButton.setToolTipText("Gửi tin nhắn");
        sendButton.setPreferredSize(new Dimension(32, 32));
        sendButton.setFocusPainted(true);
        DiscordTheme.stylePrimaryButton(sendButton);
        sendButton.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        sendButton.addActionListener(event -> sendCurrentMessage());
        return sendButton;
    }

    private JComponent centeredColumn(JComponent content) {
        JPanel row = new JPanel(new ColumnLayout(MAX_CONTENT_WIDTH, 0));
        row.setOpaque(false);
        row.add(content);
        return row;
    }

    // ---------------------------------------------------------- info pane

    private JComponent buildInfoPane() {
        infoPane.setBackground(DiscordTheme.SIDEBAR);
        infoPane.setLayout(new GridBagLayout());
        infoPane.setPreferredSize(new Dimension(INFO_WIDTH, 0));
        infoPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, DiscordTheme.HAIRLINE),
                BorderFactory.createEmptyBorder(18, 14, 14, 14)));
        return infoPane;
    }

    private void rebuildInfoPane(Conversation conversation) {
        infoPane.removeAll();
        int innerWidth = INFO_WIDTH - 28;
        int row = 0;

        if (conversation.isGroup()) {
            JLabel section = new JLabel("THÀNH VIÊN — " + conversation.members().size());
            section.setFont(DiscordTheme.extrabold(10f));
            section.setForeground(DiscordTheme.TEXT_SECONDARY);
            row = addInfoRow(section, row, 12);

            for (String member : conversation.members()) {
                JPanel memberRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
                memberRow.setOpaque(false);
                String initials = member.length() >= 2 ? member.substring(0, 2).toUpperCase() : member.toUpperCase();
                memberRow.add(new AvatarBadge(initials, member, 26, DiscordTheme.SUCCESS, DiscordTheme.SIDEBAR, true));

                JLabel mName = new JLabel(member);
                mName.setFont(DiscordTheme.semibold(12f));
                mName.setForeground(DiscordTheme.TEXT_PRIMARY);
                memberRow.add(mName);

                row = addInfoRow(memberRow, row, 8);
            }

            JPanel line = new JPanel();
            line.setBackground(new Color(0x33, 0x35, 0x3B));
            line.setPreferredSize(new Dimension(innerWidth, 1));
            row = addInfoRow(line, row, 12);

            JLabel chanTitle = new JLabel("THÔNG TIN KÊNH");
            chanTitle.setFont(DiscordTheme.extrabold(10f));
            chanTitle.setForeground(DiscordTheme.TEXT_SECONDARY);
            row = addInfoRow(chanTitle, row, 6);

            JLabel hashName = new JLabel("#" + selectedChannel);
            hashName.setFont(DiscordTheme.bold(12f));
            hashName.setForeground(Color.WHITE);
            row = addInfoRow(hashName, row, 4);

            WrappedTextArea about = new WrappedTextArea(conversation.about(), innerWidth);
            about.setFont(DiscordTheme.font(11f));
            about.setForeground(new Color(0xC6, 0xC7, 0xCD));
            row = addInfoRow(about, row, 0);

        } else {
            JLabel section = new JLabel("THÔNG TIN");
            section.setFont(DiscordTheme.extrabold(10f));
            section.setForeground(DiscordTheme.TEXT_SECONDARY);
            row = addInfoRow(section, row, 16);

            JPanel identity = new JPanel(new GridBagLayout());
            identity.setOpaque(false);

            AvatarBadge avatar = new AvatarBadge(conversation.initials(), conversation.id(), 74,
                    conversation.online() ? DiscordTheme.SUCCESS : null,
                    DiscordTheme.SIDEBAR, true);
            identity.add(avatar, centeredCell(GridBagConstraints.NONE, 0));

            JLabel name = new JLabel(conversation.displayName());
            name.setFont(DiscordTheme.extrabold(15f));
            name.setForeground(Color.WHITE);
            name.setHorizontalAlignment(SwingConstants.CENTER);
            identity.add(name, centeredCell(GridBagConstraints.HORIZONTAL, 1));

            JLabel handle = new JLabel("@" + conversation.id());
            handle.setFont(DiscordTheme.font(11f));
            handle.setForeground(DiscordTheme.TEXT_MUTED);
            handle.setHorizontalAlignment(SwingConstants.CENTER);
            identity.add(handle, centeredCell(GridBagConstraints.HORIZONTAL, 2));

            JLabel presence = new JLabel(conversation.online() ? "Đang hoạt động" : "Ngoại tuyến");
            presence.setFont(DiscordTheme.semibold(10f));
            presence.setForeground(conversation.online() ? DiscordTheme.SUCCESS : DiscordTheme.TEXT_MUTED);
            presence.setHorizontalAlignment(SwingConstants.CENTER);
            padRight(presence);
            identity.add(presence, centeredCell(GridBagConstraints.HORIZONTAL, 3));
            row = addInfoRow(identity, row, 14);

            JPanel line = new JPanel();
            line.setBackground(new Color(0x33, 0x35, 0x3B));
            line.setPreferredSize(new Dimension(innerWidth, 1));
            row = addInfoRow(line, row, 12);

            JLabel aboutTitle = new JLabel("GIỚI THIỆU");
            aboutTitle.setFont(DiscordTheme.extrabold(10f));
            aboutTitle.setForeground(DiscordTheme.TEXT_SECONDARY);
            row = addInfoRow(aboutTitle, row, 6);

            WrappedTextArea about = new WrappedTextArea(conversation.about(), innerWidth);
            about.setFont(DiscordTheme.font(11f));
            about.setForeground(new Color(0xC6, 0xC7, 0xCD));
            row = addInfoRow(about, row, 0);
        }

        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        GridBagConstraints spacerConstraints = new GridBagConstraints();
        spacerConstraints.gridx = 0;
        spacerConstraints.gridy = row;
        spacerConstraints.weightx = 1;
        spacerConstraints.weighty = 1;
        spacerConstraints.fill = GridBagConstraints.BOTH;
        infoPane.add(spacer, spacerConstraints);

        RoundedPanel note = new RoundedPanel(6);
        note.setBackground(DiscordTheme.SUNKEN);
        note.setLayout(new BorderLayout(6, 0));
        note.setBorder(BorderFactory.createEmptyBorder(8, 9, 8, 9));
        JLabel lock = new JLabel(IconFactory.phosphor("info.svg", 13, DiscordTheme.TEXT_MUTED));
        lock.setVerticalAlignment(SwingConstants.TOP);
        note.add(lock, BorderLayout.WEST);
        WrappedTextArea noteText = new WrappedTextArea("Dữ liệu minh hoạ theo mẫu canvas Kombai.",
                innerWidth - 30);
        noteText.setFont(DiscordTheme.font(10f));
        noteText.setForeground(DiscordTheme.TEXT_MUTED);
        note.add(noteText, BorderLayout.CENTER);
        addInfoRow(note, row + 1, 0);

        infoPane.revalidate();
        infoPane.repaint();
    }

    private GridBagConstraints centeredCell(int fill, int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 1;
        constraints.fill = fill;
        constraints.anchor = GridBagConstraints.NORTH;
        int topGap = switch (row) {
            case 1 -> 10;
            case 2 -> 2;
            case 3 -> 8;
            default -> 0;
        };
        constraints.insets = new Insets(topGap, 0, 0, 0);
        return constraints;
    }

    private int addInfoRow(JComponent component, int row, int bottomGap) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(0, 0, bottomGap, 0);
        infoPane.add(component, constraints);
        return row + 1;
    }

    // ------------------------------------------------------------- actions

    private void padRight(JLabel label) {
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
    }

    private JButton iconButton(String icon, String accessibleName, int size, Color foreground) {
        JButton button = new JButton(IconFactory.phosphor(icon, size, foreground));
        button.setToolTipText(accessibleName);
        button.getAccessibleContext().setAccessibleName(accessibleName);
        button.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        button.setContentAreaFilled(false);
        button.setFocusPainted(true);
        button.setPreferredSize(new Dimension(32, 32));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private void installSearchShortcut() {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_K, KeyEvent.CTRL_DOWN_MASK), "focus-search");
        getActionMap().put("focus-search", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                searchField.requestFocusInWindow();
            }
        });
    }

    private void updateFilter() {
        filter = searchField.getText().trim().toLowerCase(Locale.ROOT);
        rebuildConversationButtons();
    }

    private void rebuildConversationButtons() {
        directMessagesList.removeAll();
        groupMessagesList.removeAll();

        for (Conversation conversation : conversations) {
            if (!filter.isEmpty()
                    && !conversation.displayName().toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }
            JButton btn = conversationButton(conversation, conversation.id().equals(selectedConversationId));
            if (conversation.isGroup()) {
                groupMessagesList.add(btn);
            } else {
                directMessagesList.add(btn);
            }
        }

        if (directMessagesList.getComponentCount() == 0 && groupMessagesList.getComponentCount() == 0) {
            if (loadingConversations) {
                JPanel loadingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
                loadingPanel.setOpaque(false);
                loadingPanel.add(new JLabel(IconFactory.phosphor("arrow-clockwise.svg", 14, DiscordTheme.ACCENT)));
                JLabel text = new JLabel("Đang tải hội thoại...");
                text.setFont(DiscordTheme.font(11f));
                text.setForeground(DiscordTheme.TEXT_MUTED);
                loadingPanel.add(text);
                directMessagesList.add(loadingPanel);
            } else {
                JLabel empty = new JLabel(conversations.isEmpty()
                        ? "Chưa có cuộc trò chuyện nào."
                        : "Không có hội thoại phù hợp.");
                empty.setFont(DiscordTheme.font(11f));
                empty.setForeground(DiscordTheme.TEXT_MUTED);
                empty.setBorder(BorderFactory.createEmptyBorder(8, 6, 4, 6));
                directMessagesList.add(empty);

                if (conversations.isEmpty()) {
                    JButton startDmBtn = new JButton("+ Nhắn tin mới");
                    startDmBtn.setFont(DiscordTheme.semibold(11f));
                    startDmBtn.setForeground(DiscordTheme.ACCENT_SOFT);
                    startDmBtn.setContentAreaFilled(false);
                    startDmBtn.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
                    startDmBtn.setHorizontalAlignment(SwingConstants.LEFT);
                    startDmBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    startDmBtn.addActionListener(e -> openStartDmDialog());
                    directMessagesList.add(startDmBtn);
                }
            }
        }

        directMessagesList.revalidate();
        directMessagesList.repaint();
        groupMessagesList.revalidate();
        groupMessagesList.repaint();
    }

    private JButton conversationButton(Conversation conversation, boolean selected) {
        JButton row = new JButton();
        row.setLayout(new BorderLayout(9, 0));
        row.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        row.setBackground(selected ? DiscordTheme.SELECTED : DiscordTheme.SIDEBAR);
        row.setContentAreaFilled(true);
        row.setFocusPainted(true);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        row.setPreferredSize(new Dimension(SIDEBAR_WIDTH - 20, 46));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        AvatarBadge avatar = new AvatarBadge(conversation.initials(), conversation.id(), 30,
                conversation.online() ? DiscordTheme.SUCCESS : null,
                selected ? DiscordTheme.SELECTED : DiscordTheme.SIDEBAR, true);
        row.add(avatar, BorderLayout.WEST);

        JLabel name = new JLabel(conversation.displayName());
        name.setFont(DiscordTheme.semibold(13f));
        name.setForeground(selected ? Color.WHITE : new Color(0xB9, 0xBA, 0xC2));
        row.add(name, BorderLayout.CENTER);

        row.addActionListener(e -> {
            selectConversation(conversation);
            showChatView();
        });
        return row;
    }

    private void selectConversation(Conversation conversation) {
        if (conversation == null) return;
        if (conversation.id().equals(selectedConversationId)
                && (!conversation.isGroup() || conversation.channels().contains(selectedChannel))) {
            return;
        }

        if (selectedConversationId != null) {
            drafts.put(selectedConversationId, composer.getMessageText());
        }
        selectedConversationId = conversation.id();

        if (conversation.isGroup()) {
            activeRailGroupId = conversation.id();
            if (!conversation.channels().contains(selectedChannel)) {
                selectedChannel = conversation.channels().isEmpty() ? "chung" : conversation.channels().getFirst();
            }
            sidebarLayout.show(sidebarHost, "group-sidebar");
            conversationTitle.setText("#" + selectedChannel);
            rebuildGroupChannels(conversation);
        } else {
            activeRailGroupId = "dm";
            sidebarLayout.show(sidebarHost, "dm-sidebar");
            conversationTitle.setText(conversation.displayName());
        }
        rebuildRail();

        conversationSubtitle.setText(conversation.about());

        headerAvatarHolder.removeAll();
        AvatarBadge headerAvatar = new AvatarBadge(conversation.initials(), conversation.id(), AVATAR_SIZE,
                conversation.online() ? DiscordTheme.SUCCESS : null, DiscordTheme.TRANSCRIPT, true);
        headerAvatarHolder.add(headerAvatar, BorderLayout.CENTER);
        headerAvatarHolder.revalidate();
        headerAvatarHolder.repaint();

        composer.setMessageText(drafts.getOrDefault(selectedConversationId, ""));
        showHint("Bản nháp riêng cho cuộc trò chuyện này", DiscordTheme.TEXT_MUTED);
        rebuildConversationButtons();
        rebuildInfoPane(conversation);
        rebuildTranscript();
    }

    private Conversation currentConversation() {
        return conversations.stream()
                .filter(c -> c.id().equals(selectedConversationId))
                .findFirst()
                .orElse(null);
    }

    private void renderEmptyWorkspaceState() {
        if (loadingConversations) {
            conversationTitle.setText("Đang tải dữ liệu...");
            conversationSubtitle.setText("Đang kết nối và đồng bộ dữ liệu từ máy chủ TCP.");
            headerAvatarHolder.removeAll();
            headerAvatarHolder.revalidate();
            headerAvatarHolder.repaint();

            infoPane.removeAll();
            infoPane.revalidate();
            infoPane.repaint();

            transcript.removeAll();
            JPanel loadingBox = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 40));
            loadingBox.setOpaque(false);
            loadingBox.add(new JLabel(IconFactory.phosphor("arrow-clockwise.svg", 20, DiscordTheme.ACCENT)));
            JLabel lbl = new JLabel("Đang tải dữ liệu người dùng và phòng chat từ máy chủ...");
            lbl.setFont(DiscordTheme.font(13f));
            lbl.setForeground(DiscordTheme.TEXT_SECONDARY);
            loadingBox.add(lbl);
            transcript.add(loadingBox);
            transcript.revalidate();
            transcript.repaint();
            return;
        }

        conversationTitle.setText("Chào mừng, " + user.displayName() + "!");
        conversationSubtitle.setText("Bấm dấu (+) ở mục Tin nhắn trực tiếp hoặc Tạo nhóm để bắt đầu trò chuyện.");
        headerAvatarHolder.removeAll();
        headerAvatarHolder.revalidate();
        headerAvatarHolder.repaint();

        infoPane.removeAll();
        infoPane.revalidate();
        infoPane.repaint();

        transcript.removeAll();
        JLabel welcome = new JLabel("Tài khoản của bạn hiện chưa có cuộc trò chuyện nào.");
        welcome.setFont(DiscordTheme.semibold(14f));
        welcome.setForeground(DiscordTheme.TEXT_PRIMARY);
        welcome.setBorder(BorderFactory.createEmptyBorder(24, 0, 8, 0));
        transcript.add(welcome);

        JLabel sub = new JLabel("Hãy chọn người dùng để nhắn tin 1-1 hoặc tạo nhóm trò chuyện mới.");
        sub.setFont(DiscordTheme.font(12f));
        sub.setForeground(DiscordTheme.TEXT_MUTED);
        sub.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        transcript.add(sub);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);

        JButton newDmBtn = new JButton("Nhắn tin trực tiếp mới (+)");
        DiscordTheme.stylePrimaryButton(newDmBtn);
        newDmBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        newDmBtn.addActionListener(e -> openStartDmDialog());
        actions.add(newDmBtn);

        JButton newGroupBtn = new JButton("Tạo nhóm mới (+)");
        newGroupBtn.setFont(DiscordTheme.bold(12f));
        newGroupBtn.setForeground(Color.WHITE);
        newGroupBtn.setBackground(new Color(0x35, 0x37, 0x3D));
        newGroupBtn.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        newGroupBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        newGroupBtn.addActionListener(e -> openCreateGroupDialog());
        actions.add(newGroupBtn);

        transcript.add(actions);
        transcript.revalidate();
        transcript.repaint();
    }

    private String transcriptCacheKey(String convId, String chan) {
        return (convId != null ? convId : "") + "::" + (chan != null ? chan : "");
    }

    private void rebuildTranscript() {
        if (selectedConversationId == null || currentConversation() == null) {
            renderEmptyWorkspaceState();
            return;
        }

        Conversation conv = currentConversation();
        String chan = (conv != null && conv.isGroup()) ? selectedChannel : "";
        String key = transcriptCacheKey(selectedConversationId, chan);

        // 1. Instant render from cache (0ms delay, no freezing, no flicker)
        List<ChatMessage> cached = messageCache.get(key);
        if (cached != null) {
            renderTranscriptRows(new ArrayList<>(cached), chan);
        } else {
            renderLoadingTranscript(chan);
        }

        // 2. Refresh from server asynchronously in the background
        final String convId = selectedConversationId;
        final String targetChan = chan;
        CompletableFuture.supplyAsync(() -> {
            try {
                return chatService.messages(convId, targetChan);
            } catch (Exception e) {
                return Collections.<ChatMessage>emptyList();
            }
        }).thenAccept(fetched -> SwingUtilities.invokeLater(() -> {
            if (convId.equals(selectedConversationId) && targetChan.equals((currentConversation() != null && currentConversation().isGroup()) ? selectedChannel : "")) {
                String cacheKey = transcriptCacheKey(convId, targetChan);
                List<ChatMessage> currentList = messageCache.computeIfAbsent(cacheKey, k -> new ArrayList<>());
                synchronized (currentList) {
                    List<ChatMessage> inFlight = currentList.stream()
                            .filter(m -> m.id().startsWith("opt_"))
                            .toList();

                    boolean isSame = (currentList.size() == fetched.size() + inFlight.size());
                    if (isSame) {
                        for (int i = 0; i < fetched.size(); i++) {
                            ChatMessage a = currentList.get(i);
                            ChatMessage b = fetched.get(i);
                            if (!a.id().equals(b.id()) || !a.content().equals(b.content()) || a.edited() != b.edited() || a.recalled() != b.recalled()) {
                                isSame = false;
                                break;
                            }
                        }
                    }

                    if (isSame && !currentList.isEmpty()) {
                        // Content unchanged, skip re-rendering the whole transcript to avoid UI stutter/flicker!
                        return;
                    }

                    currentList.clear();
                    currentList.addAll(fetched);

                    for (ChatMessage opt : inFlight) {
                        boolean matched = currentList.stream().anyMatch(m ->
                                m.senderId().equals(opt.senderId()) &&
                                m.content().equals(opt.content())
                        );
                        if (!matched) {
                            currentList.add(opt);
                        }
                    }
                    renderTranscriptRows(new ArrayList<>(currentList), targetChan);
                }
            }
        }));
    }

    private void renderLoadingTranscript(String chan) {
        transcript.removeAll();
        transcript.add(buildIntroRow());

        JPanel loadingBox = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 20));
        loadingBox.setOpaque(false);

        JLabel spinnerIcon = new JLabel(IconFactory.phosphor("arrow-clockwise.svg", 18, DiscordTheme.ACCENT));
        JLabel loadingLabel = new JLabel("Đang tải tin nhắn từ máy chủ...");
        loadingLabel.setFont(DiscordTheme.font(13f));
        loadingLabel.setForeground(DiscordTheme.TEXT_SECONDARY);

        loadingBox.add(spinnerIcon);
        loadingBox.add(loadingLabel);

        transcript.add(loadingBox);
        transcript.revalidate();
        transcript.repaint();
    }

    private void renderTranscriptRows(List<ChatMessage> history, String chan) {
        transcript.removeAll();
        transcript.add(buildIntroRow());

        if (history.isEmpty()) {
            JLabel empty = new JLabel("Chưa có tin nhắn trong " + (chan.isEmpty() ? "cuộc trò chuyện này." : "#" + chan + "."));
            empty.setFont(DiscordTheme.font(13f));
            empty.setForeground(DiscordTheme.TEXT_MUTED);
            empty.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
            transcript.add(empty);
        } else {
            String previousSenderId = null;
            MessageRow.MessageActionListener actionListener = new MessageRow.MessageActionListener() {
                @Override
                public void onEdit(ChatMessage message, String newContent) {
                    CompletableFuture.runAsync(() -> chatService.edit(selectedConversationId, message.id(), newContent))
                            .thenRun(() -> SwingUtilities.invokeLater(ChatWorkspace.this::rebuildTranscript));
                }

                @Override
                public void onRecall(ChatMessage message) {
                    // Optimistic recall: instantly reflect in cache and UI
                    String cacheKey = transcriptCacheKey(selectedConversationId, chan);
                    List<ChatMessage> currentList = messageCache.computeIfAbsent(cacheKey, k -> new ArrayList<>());
                    synchronized (currentList) {
                        for (int i = 0; i < currentList.size(); i++) {
                            ChatMessage m = currentList.get(i);
                            if (m.id().equals(message.id())) {
                                currentList.set(i, new ChatMessage(
                                        m.id(), m.conversationId(), m.channel(), m.senderId(),
                                        m.senderName(), m.senderInitials(), "Tin nhắn đã được thu hồi",
                                        m.sentAt(), m.fromCurrentUser(), false, true, null, 0L, false
                                ));
                                break;
                            }
                        }
                        renderTranscriptRows(new ArrayList<>(currentList), chan);
                    }
                    CompletableFuture.runAsync(() -> chatService.recall(selectedConversationId, message.id()))
                            .thenRun(() -> SwingUtilities.invokeLater(ChatWorkspace.this::rebuildTranscript));
                }

                @Override
                public void onDelete(ChatMessage message) {
                    // Optimistic delete: instantly reflect in cache and UI
                    String cacheKey = transcriptCacheKey(selectedConversationId, chan);
                    List<ChatMessage> currentList = messageCache.computeIfAbsent(cacheKey, k -> new ArrayList<>());
                    synchronized (currentList) {
                        currentList.removeIf(m -> m.id().equals(message.id()));
                        renderTranscriptRows(new ArrayList<>(currentList), chan);
                    }
                    CompletableFuture.runAsync(() -> chatService.delete(selectedConversationId, message.id()))
                            .thenRun(() -> SwingUtilities.invokeLater(ChatWorkspace.this::rebuildTranscript));
                }

                @Override
                public void onResend(ChatMessage message) {
                    CompletableFuture.runAsync(() -> chatService.sendAttachment(selectedConversationId, chan, user, message.attachmentName(), message.attachmentSize(), false))
                            .thenRun(() -> SwingUtilities.invokeLater(ChatWorkspace.this::rebuildTranscript));
                }

                @Override
                public void onDownload(ChatMessage message) {
                    downloadAttachmentToFile(message);
                }
            };

            for (ChatMessage message : history) {
                boolean beginsGroup = !message.senderId().equals(previousSenderId);
                transcript.add(new MessageRow(message, defaultWrapWidth(), beginsGroup, actionListener));
                previousSenderId = message.senderId();
            }
        }
        transcript.revalidate();
        transcript.repaint();
        scrollTranscriptToEnd();
    }

    private int defaultWrapWidth() {
        return MAX_CONTENT_WIDTH - AVATAR_SIZE - AVATAR_GAP;
    }

    private JComponent buildIntroRow() {
        JPanel intro = new JPanel(new BorderLayout(8, 0));
        intro.setOpaque(false);
        intro.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0x35, 0x37, 0x3D)),
                BorderFactory.createEmptyBorder(0, 0, 10, 0)));

        JLabel icon = new JLabel(IconFactory.phosphor("chat-circle-dots.svg", 16, DiscordTheme.TEXT_SECONDARY));
        intro.add(icon, BorderLayout.WEST);

        Conversation conv = currentConversation();
        String name = conv != null ? (conv.isGroup() ? ("#" + selectedChannel) : conv.displayName()) : "";
        JLabel text = new JLabel("<html>Chào mừng đến với <b>" + name + "</b>!</html>");
        text.setFont(DiscordTheme.font(11f));
        text.setForeground(DiscordTheme.TEXT_SECONDARY);
        intro.add(text, BorderLayout.CENTER);

        JLabel day = new JLabel("Hôm nay");
        day.setFont(DiscordTheme.font(10f));
        day.setForeground(DiscordTheme.TEXT_MUTED);
        intro.add(day, BorderLayout.EAST);
        return intro;
    }

    private void scrollTranscriptToEnd() {
        SwingUtilities.invokeLater(() -> {
            JScrollPane scroll = transcriptScroll;
            scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum());
        });
    }

    private void adjustComposerHeight() {
        int lines = Math.max(1, composer.getMessageText().split("\n", -1).length);
        int visibleLines = Math.min(4, lines);
        int height = 32 + (visibleLines - 1) * 18;
        composerScroll.setPreferredSize(new Dimension(0, height));
        composerScroll.revalidate();
    }

    private void showHint(String text, Color color) {
        composerHint.setText(text);
        composerHint.setForeground(color);
    }

    private void sendCurrentMessage() {
        String content = composer.getMessageText();
        if (content == null || content.isBlank()) {
            showHint("Nhập tin nhắn trước khi gửi.", DiscordTheme.DANGER);
            composer.requestFocusInWindow();
            return;
        }

        final String convId = selectedConversationId;
        final String text = content.trim();

        // 1. Instant UI update: clear composer & reset draft immediately
        composer.clear();
        drafts.put(convId, "");
        adjustComposerHeight();
        composer.requestFocusInWindow();

        sendTextAsync(text);
    }

    private void sendAttachmentAsync(String filePath, String fileName, long fileSize, boolean failed) {
        if (selectedConversationId == null) {
            return;
        }
        final String convId = selectedConversationId;
        Conversation conv = currentConversation();
        final String chan = (conv != null && conv.isGroup()) ? selectedChannel : "";

        showHint("Đang tải tệp lên máy chủ...", DiscordTheme.WARNING);

        // Send over TCP in background thread directly without creating optimistic placeholder
        CompletableFuture.supplyAsync(() -> chatService.sendAttachment(convId, chan, user, filePath, fileSize, failed))
                .thenAccept(confirmed -> SwingUtilities.invokeLater(() -> {
                    String cacheKey = transcriptCacheKey(convId, chan);
                    List<ChatMessage> currentList = messageCache.computeIfAbsent(cacheKey, k -> new ArrayList<>());
                    synchronized (currentList) {
                        boolean alreadyThere = currentList.stream().anyMatch(m -> m.id().equals(confirmed.id()));
                        if (!alreadyThere) {
                            currentList.add(confirmed);
                        }
                    }
                    if (convId.equals(selectedConversationId)) {
                        renderTranscriptRows(new ArrayList<>(currentList), chan);
                    }
                    showHint("Đã gửi tệp đính kèm thành công", DiscordTheme.SUCCESS);
                }))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        showHint("Lỗi gửi tệp: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()), DiscordTheme.DANGER);
                    });
                    return null;
                });
    }

    private void sendTextAsync(String text) {
        if (selectedConversationId == null || text == null || text.isBlank()) {
            return;
        }
        final String convId = selectedConversationId;
        Conversation conv = currentConversation();
        final String chan = (conv != null && conv.isGroup()) ? selectedChannel : "";

        // 2. Optimistic UI update: instantly show message in transcript
        String optId = "opt_" + UUID.randomUUID();
        ChatMessage optimisticMsg = new ChatMessage(
                optId,
                convId,
                chan,
                user.id(),
                user.displayName(),
                user.initials(),
                text,
                java.time.LocalDateTime.now(),
                true,
                false,
                false,
                null,
                0L,
                false
        );
        String key = transcriptCacheKey(convId, chan);
        List<ChatMessage> list = messageCache.computeIfAbsent(key, k -> new ArrayList<>());
        synchronized (list) {
            list.add(optimisticMsg);
        }
        renderTranscriptRows(new ArrayList<>(list), chan);
        showHint("Đã gửi tin nhắn qua máy chủ TCP & CSDL Cloud", DiscordTheme.SUCCESS);

        // 3. Send over TCP in background thread
        CompletableFuture.supplyAsync(() -> chatService.send(convId, chan, user, text))
                .thenAccept(confirmed -> SwingUtilities.invokeLater(() -> {
                    String cacheKey = transcriptCacheKey(convId, chan);
                    List<ChatMessage> currentList = messageCache.computeIfAbsent(cacheKey, k -> new ArrayList<>());
                    synchronized (currentList) {
                        currentList.removeIf(m -> m.id().equals(optId));
                        boolean alreadyThere = currentList.stream().anyMatch(m -> m.id().equals(confirmed.id()));
                        if (!alreadyThere) {
                            currentList.add(confirmed);
                        }
                    }
                    if (convId.equals(selectedConversationId)) {
                        renderTranscriptRows(new ArrayList<>(currentList), chan);
                    }
                }))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        String cacheKey = transcriptCacheKey(convId, chan);
                        List<ChatMessage> currentList = messageCache.computeIfAbsent(cacheKey, k -> new ArrayList<>());
                        synchronized (currentList) {
                            currentList.removeIf(m -> m.id().equals(optId));
                        }
                        if (convId.equals(selectedConversationId)) {
                            renderTranscriptRows(new ArrayList<>(currentList), chan);
                        }
                        if (!text.startsWith("[Sticker:")) {
                            composer.setMessageText(text);
                        }
                        showHint("Lỗi gửi tin nhắn: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()), DiscordTheme.DANGER);
                    });
                    return null;
                });
    }

    private void openInboxDialog() {
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
        InboxDialog dialog = new InboxDialog(frame, notif -> {
            if (!conversations.isEmpty()) {
                selectConversation(conversations.getFirst());
                showChatView();
            }
        });
        dialog.setVisible(true);
    }

    private void openCreateGroupDialog() {
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
        CreateGroupDialog dialog = new CreateGroupDialog(frame, chatService, createdGroup -> {
            conversations.add(createdGroup);
            activeRailGroupId = createdGroup.id();
            selectConversation(createdGroup);
            showChatView();
            rebuildRail();
        });
        dialog.setVisible(true);
    }

    private void openStartDmDialog() {
        CompletableFuture.supplyAsync(() -> {
            try {
                return chatService.listUsers();
            } catch (Exception e) {
                return Collections.<UserProfile>emptyList();
            }
        }).thenAccept(users -> SwingUtilities.invokeLater(() -> {
            List<UserProfile> candidates = new ArrayList<>();
            if (users != null) {
                for (UserProfile u : users) {
                    if (u != null && !u.id().equals(user.id())) {
                        candidates.add(u);
                    }
                }
            }
            if (candidates.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Hiện chưa có người dùng nào khác trên máy chủ.",
                        "Tin nhắn trực tiếp mới",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            String[] options = new String[candidates.size()];
            for (int i = 0; i < candidates.size(); i++) {
                UserProfile u = candidates.get(i);
                options[i] = u.displayName() + " (" + u.username() + ")";
            }

            String chosen = (String) JOptionPane.showInputDialog(
                    this,
                    "Chọn người dùng để bắt đầu trò chuyện 1-1:",
                    "Tin nhắn trực tiếp mới",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    options,
                    options[0]
            );
            if (chosen == null) {
                return;
            }
            for (int i = 0; i < options.length; i++) {
                if (options[i].equals(chosen)) {
                    UserProfile target = candidates.get(i);
                    CompletableFuture.supplyAsync(() -> chatService.openDirectMessage(target.id()))
                            .thenAccept(dm -> SwingUtilities.invokeLater(() -> {
                                if (dm != null) {
                                    boolean exists = false;
                                    for (int j = 0; j < conversations.size(); j++) {
                                        if (conversations.get(j).id().equals(dm.id())) {
                                            conversations.set(j, dm);
                                            exists = true;
                                            break;
                                        }
                                    }
                                    if (!exists) {
                                        conversations.add(dm);
                                    }
                                    selectConversation(dm);
                                    showChatView();
                                }
                            }));
                    break;
                }
            }
        }));
    }

    private void openProfileDialog() {
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
        ProfileDialog dialog = new ProfileDialog(frame, user, updatedUser -> {
            this.user = chatService.updateProfile(updatedUser);
            rebuildAccountFooters();
            rebuildConversationButtons();
            Conversation conv = currentConversation();
            if (conv != null) {
                rebuildInfoPane(conv);
            }
            showHint("Đã cập nhật hồ sơ cá nhân (" + user.displayName() + ")", DiscordTheme.SUCCESS);
        });
        dialog.setVisible(true);
    }

    private void downloadAttachmentToFile(ChatMessage message) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Lưu tệp đính kèm");
        chooser.setSelectedFile(new File(message.attachmentName()));
        int res = chooser.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File target = chooser.getSelectedFile();
            try {
                byte[] bytes = chatService.downloadAttachment(message.id(), message.attachmentName());
                if (bytes == null || bytes.length == 0) {
                    bytes = ("Nội dung tệp đính kèm: " + message.attachmentName()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }
                java.nio.file.Files.write(target.toPath(), bytes);
                showHint("Đã lưu tệp tại: " + target.getAbsolutePath(), DiscordTheme.SUCCESS);
            } catch (Exception ex) {
                showHint("Không thể lưu tệp: " + ex.getMessage(), DiscordTheme.DANGER);
            }
        }
    }
}

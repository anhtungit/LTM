package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.theme.DiscordTheme;
import vn.edu.ltm.chat.theme.IconFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Video/voice room interface with live audio status, duration timer, and dynamic participants. */
public final class MeetRoomPanel extends JPanel {
    private final Runnable onLeave;
    private Runnable onOpenVoiceSettings;
    private Runnable onOpenChat;
    private Runnable onWatchTogether;
    private Runnable onStartScreenShare;
    private BiConsumer<Integer, Boolean> onStartScreenShareConfig;
    private Runnable onStopScreenShare;
    private Consumer<Boolean> onMuteToggle;
    private Consumer<Boolean> onCamToggle;
    private boolean micMuted = false;
    private boolean micAvailable = true;
    private boolean camMuted = true;
    private boolean screenSharingActive = false;
    private boolean speakerLayoutMode = false;
    private boolean membersHidden = false;
    private String selfParticipantId;
    private String pinnedParticipantId;
    private String lastSpeakingParticipantId;

    private final JLabel titleLabel = new JLabel("Cuộc gọi thoại");
    private final JLabel statusLabel = new JLabel("· 00:00 · Đang kết nối âm thanh...");
    private final JPanel grid = new JPanel(new GridLayout(1, 2, 12, 12));
    private final CardLayout centerLayout = new CardLayout();
    private final JPanel centerHost = new JPanel(centerLayout);
    private final JPanel watchStageContainer = new JPanel(new BorderLayout());
    private final JPanel watchStrip = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
    private final JPanel callBody = new JPanel(new BorderLayout());
    private final JPanel chatDrawerHost = new JPanel(new BorderLayout());
    private JButton micBtn;
    private JButton camBtn;
    private JButton shareBtn;
    private JButton layoutModeBtn;
    private JButton chatBtn;

    private final Map<String, ParticipantEntry> participants = new LinkedHashMap<>();
    private final Map<String, BufferedImage> videoFrames = new ConcurrentHashMap<>();
    private final Map<String, JPanel> tileViews = new LinkedHashMap<>();
    private final Timer callTimer;
    private int elapsedSeconds = 0;

    private record ParticipantEntry(String id, String initials, String name, String sub, boolean speaking) {}

    public MeetRoomPanel(Runnable onLeave) {
        this.onLeave = onLeave;

        setLayout(new BorderLayout());
        setBackground(DiscordTheme.TRANSCRIPT);

        this.callTimer = new Timer(1000, e -> {
            elapsedSeconds++;
            updateHeaderStatus();
        });

        centerHost.setOpaque(false);
        centerHost.add(buildVideoGrid(), "grid");
        centerHost.add(watchStageContainer, "watch");

        callBody.setOpaque(false);
        callBody.add(buildHeader(), BorderLayout.NORTH);
        callBody.add(centerHost, BorderLayout.CENTER);
        callBody.add(buildBottomControls(), BorderLayout.SOUTH);

        chatDrawerHost.setOpaque(false);
        chatDrawerHost.setVisible(false);
        chatDrawerHost.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, DiscordTheme.HAIRLINE));

        add(callBody, BorderLayout.CENTER);
        add(chatDrawerHost, BorderLayout.SOUTH);
    }

    public void setOnMuteToggle(Consumer<Boolean> onMuteToggle) {
        this.onMuteToggle = onMuteToggle;
    }

    public void setOnCamToggle(Consumer<Boolean> onCamToggle) {
        this.onCamToggle = onCamToggle;
    }

    public void setOnStartScreenShare(Runnable onStartScreenShare) {
        this.onStartScreenShare = onStartScreenShare;
    }

    public void setOnStartScreenShareConfig(BiConsumer<Integer, Boolean> onStartScreenShareConfig) {
        this.onStartScreenShareConfig = onStartScreenShareConfig;
    }

    public void setOnStopScreenShare(Runnable onStopScreenShare) {
        this.onStopScreenShare = onStopScreenShare;
    }

    public boolean isScreenSharingActive() {
        return screenSharingActive;
    }

    public void setScreenSharingActive(boolean active) {
        this.screenSharingActive = active;
        if (shareBtn != null) {
            shareBtn.setText(active ? "Dừng chia sẻ" : "Chia sẻ màn hình");
            shareBtn.setBackground(active ? DiscordTheme.DANGER : new Color(0x3B, 0x3D, 0x44));
        }
        if (!active && selfParticipantId != null) {
            updateScreenShare(selfParticipantId, null, null);
        }
    }

    public void setOnOpenVoiceSettings(Runnable onOpenVoiceSettings) {
        this.onOpenVoiceSettings = onOpenVoiceSettings;
    }

    public void setOnWatchTogether(Runnable onWatchTogether) {
        this.onWatchTogether = onWatchTogether;
    }

    public void setOnOpenChat(Runnable onOpenChat) {
        this.onOpenChat = onOpenChat;
    }

    public boolean isChatDrawerOpen() {
        return chatDrawerHost.isVisible() && chatDrawerHost.getComponentCount() > 0;
    }

    public void openChatDrawer(JComponent chatComponent) {
        chatDrawerHost.removeAll();
        int h = Math.max(240, getHeight() > 0 ? (getHeight() * 44 / 100) : 280);
        chatDrawerHost.setPreferredSize(new Dimension(0, h));
        chatDrawerHost.add(chatComponent, BorderLayout.CENTER);
        chatDrawerHost.setVisible(true);
        if (chatBtn != null) {
            chatBtn.setText("Ẩn đoạn chat");
            chatBtn.setBackground(DiscordTheme.ACCENT);
        }
        revalidate();
        repaint();
    }

    public void closeChatDrawer() {
        chatDrawerHost.removeAll();
        chatDrawerHost.setVisible(false);
        if (chatBtn != null) {
            chatBtn.setText("Mở đoạn chat");
            chatBtn.setBackground(new Color(0x2B, 0x2D, 0x31));
        }
        revalidate();
        repaint();
    }

    public void startSession(String roomTitle, String selfId, String selfInitials, String selfName, boolean micReady) {
        startSession(roomTitle, selfId, selfInitials, selfName, micReady, false, false);
    }

    public void startSession(String roomTitle, String selfId, String selfInitials, String selfName, boolean micReady, boolean initialMicMuted) {
        startSession(roomTitle, selfId, selfInitials, selfName, micReady, initialMicMuted, false);
    }

    public void startSession(String roomTitle, String selfId, String selfInitials, String selfName, boolean micReady, boolean initialMicMuted, boolean initialCamOn) {
        this.elapsedSeconds = 0;
        this.selfParticipantId = selfId;
        this.micAvailable = micReady;
        this.micMuted = initialMicMuted || !micReady;
        this.camMuted = !initialCamOn;
        if (micBtn != null) {
            micBtn.setBackground(this.micMuted ? DiscordTheme.DANGER : new Color(0x3B, 0x3D, 0x44));
        }
        if (camBtn != null) {
            camBtn.setBackground(this.camMuted ? new Color(0x3B, 0x3D, 0x44) : DiscordTheme.SUCCESS);
        }
        titleLabel.setText(roomTitle != null && !roomTitle.isBlank() ? roomTitle : "Cuộc gọi thoại");
        videoFrames.clear();
        tileViews.clear();
        participants.clear();
        this.pinnedParticipantId = null;
        this.lastSpeakingParticipantId = null;
        this.speakerLayoutMode = false;
        this.membersHidden = false;
        if (layoutModeBtn != null) {
            layoutModeBtn.setText("Bố cục: Lưới");
        }
        String micSub = !micReady
                ? "Chế độ nghe (không tìm thấy mic)"
                : (this.micMuted ? "Đã tắt mic" : "");
        String selfDisplayName = (selfName != null && selfName.endsWith("(Bạn)")) ? selfName : (selfName + " (Bạn)");
        participants.put(selfId, new ParticipantEntry(selfId, selfInitials, selfDisplayName, micSub, false));
        rebuildGrid();
        updateHeaderStatus();
        callTimer.restart();
    }

    public boolean isCamMuted() {
        return camMuted;
    }

    public void updateParticipantVideo(String id, BufferedImage frame) {
        if (id == null || id.isBlank()) return;
        if (id.equals(selfParticipantId) && camMuted && frame != null) {
            return;
        }
        boolean hadVideo = videoFrames.containsKey(id);
        if (frame != null) {
            videoFrames.put(id, frame);
        } else {
            videoFrames.remove(id);
        }
        boolean hasVideo = (frame != null);
        if (hadVideo != hasVideo) {
            rebuildGrid();
            return;
        }
        JPanel tile = tileViews.get(id);
        if (tile != null) {
            tile.repaint();
        }
    }

    public void setParticipantMuted(String id, boolean muted) {
        if (id == null || id.isBlank()) return;
        ParticipantEntry existing = participants.get(id);
        if (existing != null) {
            String newSub = muted ? "Đã tắt mic" : "";
            boolean newSpeaking = muted ? false : existing.speaking();
            participants.put(id, new ParticipantEntry(existing.id(), existing.initials(), existing.name(), newSub, newSpeaking));
            rebuildGrid();
        }
    }

    public void addOrUpdateParticipant(String id, String initials, String name, String sub, boolean speaking) {
        if (id == null || id.isBlank()) return;
        String safeInitials = (initials != null && !initials.isBlank()) ? initials : computeInitials(name);
        participants.put(id, new ParticipantEntry(id, safeInitials, name, sub, speaking));
        rebuildGrid();
        updateHeaderStatus();
    }

    public void setParticipantSpeaking(String id, boolean speaking) {
        if (id == null || id.isBlank()) return;
        ParticipantEntry existing = participants.get(id);
        if (existing != null && existing.speaking() != speaking) {
            if (speaking) {
                this.lastSpeakingParticipantId = id;
            }
            participants.put(id, new ParticipantEntry(existing.id(), existing.initials(), existing.name(), existing.sub(), speaking));
            rebuildGrid();
        }
    }

    public void updateScreenShare(String id, String name, BufferedImage img) {
        if (id == null || id.isBlank()) return;
        if (id.equals(selfParticipantId) && !screenSharingActive && img != null) {
            return;
        }
        String screenId = id + ":screen";
        boolean had = videoFrames.containsKey(screenId);
        if (img != null) {
            videoFrames.put(screenId, img);
            if (!had) {
                String title;
                if (id.equals(selfParticipantId)) {
                    title = "Màn hình của bạn";
                } else if (name != null && !name.isBlank()) {
                    title = "Màn hình của " + name;
                } else {
                    title = "Màn hình chia sẻ";
                }
                participants.put(screenId, new ParticipantEntry(screenId, "🖥", title, "Trực tiếp", false));
                pinnedParticipantId = screenId;
                speakerLayoutMode = true;
                if (layoutModeBtn != null) {
                    layoutModeBtn.setText("Bố cục: Tiêu điểm");
                }
                rebuildGrid();
            } else {
                JPanel tile = tileViews.get(screenId);
                if (tile != null) {
                    tile.repaint();
                }
            }
        } else {
            if (had) {
                removeParticipant(screenId);
            }
        }
    }

    public void removeParticipant(String id) {
        if (id != null) {
            boolean removed = removeParticipantInternal(id);
            boolean removedScreen = removeParticipantInternal(id + ":screen");
            if (removed || removedScreen) {
                boolean anyScreenLeft = participants.keySet().stream().anyMatch(k -> k.endsWith(":screen"));
                if (!anyScreenLeft) {
                    membersHidden = false;
                    if ((pinnedParticipantId == null || pinnedParticipantId.endsWith(":screen")) && speakerLayoutMode) {
                        pinnedParticipantId = null;
                        speakerLayoutMode = false;
                        if (layoutModeBtn != null) {
                            layoutModeBtn.setText("Bố cục: Lưới");
                        }
                    }
                }
                rebuildGrid();
                updateHeaderStatus();
            }
        }
    }

    private boolean removeParticipantInternal(String id) {
        videoFrames.remove(id);
        tileViews.remove(id);
        if (id.equals(pinnedParticipantId)) {
            pinnedParticipantId = null;
        }
        if (id.equals(lastSpeakingParticipantId)) {
            lastSpeakingParticipantId = null;
        }
        return participants.remove(id) != null;
    }

    public void showWatchTogetherStage(JComponent playerComponent, Runnable onStop) {
        watchStageContainer.removeAll();

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DiscordTheme.HAIRLINE),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)
        ));

        JLabel titleLbl = new JLabel("Xem YouTube cùng nhau", IconFactory.phosphor("video-camera.svg", 18, DiscordTheme.ACCENT), JLabel.LEFT);
        titleLbl.setFont(DiscordTheme.bold(14f));
        titleLbl.setForeground(Color.WHITE);
        topBar.add(titleLbl, BorderLayout.WEST);

        refreshWatchStrip();
        JScrollPane scrollStrip = new JScrollPane(watchStrip, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollStrip.setOpaque(false);
        scrollStrip.getViewport().setOpaque(false);
        scrollStrip.setBorder(BorderFactory.createEmptyBorder());
        scrollStrip.setPreferredSize(new Dimension(0, 108));

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightBtns.setOpaque(false);

        JButton toggleMembersBtn = new JButton("Ẩn thành viên");
        toggleMembersBtn.setFont(DiscordTheme.semibold(12f));
        toggleMembersBtn.setForeground(DiscordTheme.TEXT_PRIMARY);
        toggleMembersBtn.setBackground(new Color(0x2B, 0x2D, 0x31));
        toggleMembersBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggleMembersBtn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        toggleMembersBtn.addActionListener(e -> {
            boolean vis = !scrollStrip.isVisible();
            scrollStrip.setVisible(vis);
            toggleMembersBtn.setText(vis ? "Ẩn thành viên" : "Hiện thành viên");
            watchStageContainer.revalidate();
            watchStageContainer.repaint();
        });
        rightBtns.add(toggleMembersBtn);

        JButton stopBtn = new JButton("Dừng xem chung");
        stopBtn.setFont(DiscordTheme.bold(12f));
        stopBtn.setForeground(Color.WHITE);
        stopBtn.setBackground(DiscordTheme.DANGER);
        stopBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        stopBtn.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        stopBtn.addActionListener(e -> {
            hideWatchTogetherStage();
            if (onStop != null) onStop.run();
        });
        rightBtns.add(stopBtn);

        topBar.add(rightBtns, BorderLayout.EAST);

        watchStageContainer.add(topBar, BorderLayout.NORTH);
        watchStageContainer.add(playerComponent, BorderLayout.CENTER);
        watchStageContainer.add(scrollStrip, BorderLayout.SOUTH);

        centerLayout.show(centerHost, "watch");
        revalidate();
        repaint();
    }

    public void hideWatchTogetherStage() {
        watchStageContainer.removeAll();
        centerLayout.show(centerHost, "grid");
        revalidate();
        repaint();
    }

    public boolean isWatchingTogether() {
        return watchStageContainer.getComponentCount() > 0;
    }

    private void refreshWatchStrip() {
        watchStrip.removeAll();
        watchStrip.setOpaque(false);
        for (ParticipantEntry p : participants.values()) {
            JPanel smallTile = buildParticipantTile(p.id(), p.initials(), p.name(), p.sub(), p.speaking(), true);
            smallTile.setPreferredSize(new Dimension(150, 94));
            tileViews.put(p.id(), smallTile);
            watchStrip.add(smallTile);
        }
        watchStrip.revalidate();
        watchStrip.repaint();
    }

    public int getParticipantCount() {
        return (int) participants.keySet().stream().filter(k -> !k.endsWith(":screen")).count();
    }

    public String stopSessionAndFormatDuration() {
        callTimer.stop();
        setScreenSharingActive(false);
        hideWatchTogetherStage();
        videoFrames.clear();
        tileViews.clear();
        participants.clear();
        pinnedParticipantId = null;
        speakerLayoutMode = false;
        membersHidden = false;
        int mins = elapsedSeconds / 60;
        int secs = elapsedSeconds % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    private void updateHeaderStatus() {
        int mins = elapsedSeconds / 60;
        int secs = elapsedSeconds % 60;
        statusLabel.setText(String.format("· %02d:%02d · %d người trong cuộc gọi", mins, secs, Math.max(1, getParticipantCount())));
    }

    private static String computeInitials(String name) {
        if (name == null || name.isBlank()) return "U";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(DiscordTheme.TRANSCRIPT);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DiscordTheme.HAIRLINE),
                BorderFactory.createEmptyBorder(12, 20, 12, 20)
        ));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);

        JLabel icon = new JLabel(IconFactory.phosphor("headset.svg", 20, DiscordTheme.SUCCESS));
        left.add(icon);

        titleLabel.setFont(DiscordTheme.bold(15f));
        titleLabel.setForeground(Color.WHITE);
        left.add(titleLabel);

        statusLabel.setFont(DiscordTheme.font(12f));
        statusLabel.setForeground(DiscordTheme.TEXT_MUTED);
        left.add(statusLabel);

        header.add(left, BorderLayout.CENTER);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightActions.setOpaque(false);

        layoutModeBtn = new JButton("Bố cục: Lưới", IconFactory.phosphor("users.svg", 16, DiscordTheme.TEXT_PRIMARY));
        layoutModeBtn.setFont(DiscordTheme.semibold(12f));
        layoutModeBtn.setForeground(DiscordTheme.TEXT_PRIMARY);
        layoutModeBtn.setBackground(new Color(0x2B, 0x2D, 0x31));
        layoutModeBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x3B, 0x3D, 0x44), 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        layoutModeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        layoutModeBtn.setFocusPainted(false);
        layoutModeBtn.setToolTipText("Chuyển đổi giữa chế độ Lưới và chế độ Tiêu điểm (Người đang nói / Ghim)");
        layoutModeBtn.addActionListener(e -> {
            speakerLayoutMode = !speakerLayoutMode;
            if (!speakerLayoutMode) {
                pinnedParticipantId = null;
            }
            layoutModeBtn.setText(speakerLayoutMode ? "Bố cục: Tiêu điểm" : "Bố cục: Lưới");
            rebuildGrid();
        });
        rightActions.add(layoutModeBtn);

        chatBtn = new JButton("Mở đoạn chat", IconFactory.phosphor("chat-circle-dots.svg", 16, DiscordTheme.TEXT_PRIMARY));
        chatBtn.setFont(DiscordTheme.semibold(12f));
        chatBtn.setForeground(DiscordTheme.TEXT_PRIMARY);
        chatBtn.setBackground(new Color(0x2B, 0x2D, 0x31));
        chatBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x3B, 0x3D, 0x44), 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        chatBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        chatBtn.setFocusPainted(false);
        chatBtn.setToolTipText("Bật/Tắt khung tin nhắn văn bản ngay bên dưới cuộc gọi (giống Discord)");
        chatBtn.addActionListener(e -> {
            if (onOpenChat != null) {
                onOpenChat.run();
            }
        });
        rightActions.add(chatBtn);

        header.add(rightActions, BorderLayout.EAST);
        return header;
    }

    private JPanel buildVideoGrid() {
        grid.setOpaque(false);
        grid.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        return grid;
    }

    private void rebuildGrid() {
        grid.removeAll();
        tileViews.clear();
        int count = Math.max(1, participants.size());

        boolean useSpotlight = (speakerLayoutMode || pinnedParticipantId != null) && count >= 2;
        if (useSpotlight) {
            String focusId = pinnedParticipantId;
            if (focusId == null || !participants.containsKey(focusId)) {
                if (lastSpeakingParticipantId != null && participants.containsKey(lastSpeakingParticipantId)) {
                    focusId = lastSpeakingParticipantId;
                } else {
                    for (ParticipantEntry p : participants.values()) {
                        if (!p.id().equals(selfParticipantId)) {
                            focusId = p.id();
                            break;
                        }
                    }
                    if (focusId == null) {
                        focusId = participants.keySet().iterator().next();
                    }
                }
            }

            grid.setLayout(new BorderLayout(12, 12));
            ParticipantEntry focused = participants.get(focusId);
            JPanel stageTile = buildParticipantTile(focused.id(), focused.initials(), focused.name(), focused.sub(), focused.speaking(), false);
            tileViews.put(focused.id(), stageTile);
            grid.add(stageTile, BorderLayout.CENTER);

            if (!membersHidden) {
                JPanel strip = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
                strip.setOpaque(false);
                for (ParticipantEntry p : participants.values()) {
                    if (p.id().equals(focusId)) continue;
                    JPanel smallTile = buildParticipantTile(p.id(), p.initials(), p.name(), p.sub(), p.speaking(), true);
                    smallTile.setPreferredSize(new Dimension(180, 118));
                    tileViews.put(p.id(), smallTile);
                    strip.add(smallTile);
                }
                JScrollPane stripScroll = new JScrollPane(strip, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
                stripScroll.setOpaque(false);
                stripScroll.getViewport().setOpaque(false);
                stripScroll.setBorder(BorderFactory.createEmptyBorder());
                stripScroll.setPreferredSize(new Dimension(0, 130));
                grid.add(stripScroll, BorderLayout.SOUTH);
            }
        } else {
            int cols = count == 1 ? 1 : (count <= 4 ? 2 : (count <= 9 ? 3 : 4));
            int rows = (int) Math.ceil((double) count / cols);
            JPanel matrix = new JPanel(new GridLayout(rows, cols, 12, 12));
            matrix.setOpaque(false);
            for (ParticipantEntry p : participants.values()) {
                JPanel tile = buildParticipantTile(p.id(), p.initials(), p.name(), p.sub(), p.speaking(), count > 6);
                if (count > 6) {
                    tile.setPreferredSize(new Dimension(220, 150));
                }
                tileViews.put(p.id(), tile);
                matrix.add(tile);
            }
            if (count > 6) {
                grid.setLayout(new BorderLayout());
                JScrollPane scroll = new JScrollPane(matrix, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
                scroll.setOpaque(false);
                scroll.getViewport().setOpaque(false);
                scroll.setBorder(BorderFactory.createEmptyBorder());
                scroll.getVerticalScrollBar().setUnitIncrement(16);
                grid.add(scroll, BorderLayout.CENTER);
            } else {
                grid.setLayout(new GridLayout(rows, cols, 12, 12));
                for (java.awt.Component c : matrix.getComponents()) {
                    grid.add(c);
                }
            }
        }
        grid.revalidate();
        grid.repaint();
        if (isWatchingTogether()) {
            refreshWatchStrip();
        }
    }

    private JPanel buildParticipantTile(String id, String initials, String name, String sub, boolean speaking, boolean compact) {
        boolean isScreen = id.endsWith(":screen");
        RoundedPanel tile = new RoundedPanel(10) {
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                BufferedImage frame = videoFrames.get(id);
                if (frame != null) {
                    Graphics2D g2 = (Graphics2D) graphics.create();
                    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2.setClip(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                    int w = getWidth();
                    int h = getHeight();
                    if (isScreen) {
                        g2.setColor(Color.BLACK);
                        g2.fillRect(0, 0, w, h);
                    }
                    double scale = isScreen
                            ? Math.min((double) w / frame.getWidth(), (double) h / frame.getHeight())
                            : Math.max((double) w / frame.getWidth(), (double) h / frame.getHeight());
                    int dw = (int) Math.round(frame.getWidth() * scale);
                    int dh = (int) Math.round(frame.getHeight() * scale);
                    int dx = (w - dw) / 2;
                    int dy = (h - dh) / 2;
                    boolean mirrorSelfCam = id.equals(selfParticipantId) && !isScreen;
                    if (mirrorSelfCam) {
                        g2.drawImage(frame, dx + dw, dy, -dw, dh, null);
                    } else {
                        g2.drawImage(frame, dx, dy, dw, dh, null);
                    }
                    g2.dispose();
                }
            }
        };
        tile.setBackground(new Color(0x1E, 0x1F, 0x23));
        boolean isPinned = id.equals(pinnedParticipantId);
        Color borderColor = speaking ? DiscordTheme.SUCCESS : (isPinned ? DiscordTheme.ACCENT : new Color(0x2B, 0x2D, 0x31));
        int pad = compact ? 6 : 12;
        tile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, (speaking || isPinned) ? 2 : 1),
                BorderFactory.createEmptyBorder(pad, pad, pad, pad)
        ));
        tile.setLayout(new BorderLayout());
        tile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tile.setToolTipText(isPinned ? "Bấm để bỏ ghim tiêu điểm" : "Bấm để ghim người này làm tiêu điểm");
        tile.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (participants.size() < 2) return;
                if (id.equals(pinnedParticipantId)) {
                    pinnedParticipantId = null;
                    speakerLayoutMode = false;
                    membersHidden = false;
                } else {
                    pinnedParticipantId = id;
                    speakerLayoutMode = true;
                }
                if (layoutModeBtn != null) {
                    layoutModeBtn.setText((speakerLayoutMode || pinnedParticipantId != null) ? "Bố cục: Tiêu điểm" : "Bố cục: Lưới");
                }
                rebuildGrid();
            }
        });

        JPanel northBadges = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        northBadges.setOpaque(false);
        boolean showHideMembers = !compact && (speakerLayoutMode || pinnedParticipantId != null) && participants.size() >= 2;
        if (showHideMembers) {
            JButton hideMembersBtn = new JButton(membersHidden ? "Hiện thành viên" : "Ẩn thành viên");
            hideMembersBtn.setFont(DiscordTheme.semibold(10f));
            hideMembersBtn.setForeground(Color.WHITE);
            hideMembersBtn.setBackground(new Color(0x11, 0x12, 0x14, 210));
            hideMembersBtn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0x3B, 0x3D, 0x44), 1),
                    BorderFactory.createEmptyBorder(3, 8, 3, 8)
            ));
            hideMembersBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            hideMembersBtn.setFocusPainted(false);
            hideMembersBtn.addActionListener(e -> {
                membersHidden = !membersHidden;
                rebuildGrid();
            });
            northBadges.add(hideMembersBtn);
        }

        if (isScreen) {
            if (!compact) {
                RoundedPanel resBadge = new RoundedPanel(6);
                resBadge.setBackground(new Color(0x11, 0x12, 0x14, 210));
                resBadge.setLayout(new FlowLayout(FlowLayout.CENTER, 6, 3));
                JLabel resLbl = new JLabel("720P 30FPS");
                resLbl.setFont(DiscordTheme.bold(10f));
                resLbl.setForeground(DiscordTheme.TEXT_PRIMARY);
                resBadge.add(resLbl);
                northBadges.add(resBadge);
            }
            RoundedPanel liveBadge = new RoundedPanel(6);
            liveBadge.setBackground(DiscordTheme.DANGER);
            liveBadge.setLayout(new FlowLayout(FlowLayout.CENTER, 6, 3));
            JLabel liveLbl = new JLabel("LIVE");
            liveLbl.setFont(DiscordTheme.bold(10f));
            liveLbl.setForeground(Color.WHITE);
            liveBadge.add(liveLbl);
            northBadges.add(liveBadge);
        }
        if (northBadges.getComponentCount() > 0) {
            tile.add(northBadges, BorderLayout.NORTH);
        }

        boolean hasVideo = videoFrames.containsKey(id);
        if (!hasVideo) {
            JPanel center = new JPanel(new GridBagLayout());
            center.setOpaque(false);

            JPanel inner = new JPanel();
            inner.setOpaque(false);
            inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

            int avatarSize = compact ? 42 : (participants.size() <= 2 ? 84 : 64);
            AvatarBadge avatar = new AvatarBadge(initials, name, avatarSize);
            avatar.setAlignmentX(CENTER_ALIGNMENT);
            inner.add(avatar);
            inner.add(Box.createVerticalStrut(compact ? 4 : 10));

            String statusText = speaking ? "Đang nói..." : (sub != null && sub.contains("Đã tắt mic") ? "Đã tắt mic" : "");
            JLabel status = new JLabel(statusText);
            status.setFont(DiscordTheme.font(compact ? 10f : 11f));
            status.setForeground(speaking ? DiscordTheme.SUCCESS : DiscordTheme.TEXT_MUTED);
            status.setAlignmentX(CENTER_ALIGNMENT);
            if (!status.getText().isEmpty()) {
                inner.add(status);
            }

            center.add(inner);
            tile.add(center, BorderLayout.CENTER);
        }

        RoundedPanel tagPill = new RoundedPanel(6);
        tagPill.setBackground(hasVideo ? new Color(0x11, 0x12, 0x14, 200) : new Color(0x1E, 0x1F, 0x23));
        tagPill.setLayout(new FlowLayout(FlowLayout.LEFT, compact ? 4 : 6, compact ? 2 : 4));
        JLabel nameLbl = new JLabel(name);
        nameLbl.setFont(DiscordTheme.bold(compact ? 11f : 12f));
        nameLbl.setForeground(Color.WHITE);
        tagPill.add(nameLbl);

        if (!compact && sub != null && !sub.isBlank()) {
            JLabel subLbl = new JLabel("· " + sub);
            subLbl.setFont(DiscordTheme.font(10f));
            subLbl.setForeground(DiscordTheme.TEXT_MUTED);
            tagPill.add(subLbl);
        }

        JPanel southWrap = new JPanel(new BorderLayout());
        southWrap.setOpaque(false);
        JPanel leftTag = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftTag.setOpaque(false);
        leftTag.add(tagPill);
        southWrap.add(leftTag, BorderLayout.WEST);

        tile.add(southWrap, BorderLayout.SOUTH);
        return tile;
    }

    private JPanel buildBottomControls() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 12));
        bar.setBackground(new Color(0x1E, 0x1F, 0x23));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, DiscordTheme.HAIRLINE));

        micBtn = new JButton(IconFactory.phosphor("microphone.svg", 18, Color.WHITE));
        styleControlBtn(micBtn, new Color(0x3B, 0x3D, 0x44));
        micBtn.setToolTipText("Bật/Tắt micrô");
        micBtn.addActionListener(e -> {
            micMuted = !micMuted;
            micBtn.setBackground(micMuted ? DiscordTheme.DANGER : new Color(0x3B, 0x3D, 0x44));
            if (selfParticipantId != null) {
                ParticipantEntry selfEntry = participants.get(selfParticipantId);
                if (selfEntry != null) {
                    String newSub = !micAvailable
                            ? "Chế độ nghe (không tìm thấy mic)"
                            : (micMuted ? "Đã tắt mic" : "");
                    participants.put(selfParticipantId, new ParticipantEntry(
                            selfEntry.id(), selfEntry.initials(), selfEntry.name(), newSub, micMuted ? false : selfEntry.speaking()
                    ));
                    rebuildGrid();
                }
            }
            if (onMuteToggle != null) {
                onMuteToggle.accept(micMuted);
            }
        });
        bar.add(micBtn);

        camBtn = new JButton(IconFactory.phosphor("video-camera.svg", 18, Color.WHITE));
        styleControlBtn(camBtn, new Color(0x3B, 0x3D, 0x44));
        camBtn.setToolTipText("Bật/Tắt camera");
        camBtn.addActionListener(e -> {
            camMuted = !camMuted;
            camBtn.setBackground(camMuted ? new Color(0x3B, 0x3D, 0x44) : DiscordTheme.SUCCESS);
            if (onCamToggle != null) {
                onCamToggle.accept(camMuted);
            }
        });
        bar.add(camBtn);

        shareBtn = new JButton("Chia sẻ màn hình", IconFactory.phosphor("monitor.svg", 18, Color.WHITE));
        styleControlBtn(shareBtn, new Color(0x3B, 0x3D, 0x44));
        shareBtn.setFont(DiscordTheme.semibold(12f));
        shareBtn.setToolTipText("Bật/Tắt chia sẻ màn hình");
        shareBtn.addActionListener(e -> {
            if (screenSharingActive) {
                if (onStopScreenShare != null) {
                    onStopScreenShare.run();
                }
            } else {
                showShareScreenDialog();
            }
        });
        bar.add(shareBtn);

        JButton ytBtn = new JButton("Xem YouTube", IconFactory.phosphor("video-camera.svg", 18, Color.WHITE));
        styleControlBtn(ytBtn, new Color(0x3B, 0x3D, 0x44));
        ytBtn.setFont(DiscordTheme.semibold(12f));
        ytBtn.setToolTipText("Xem YouTube cùng nhau với mọi người trong cuộc gọi");
        ytBtn.addActionListener(e -> {
            if (onWatchTogether != null) onWatchTogether.run();
        });
        bar.add(ytBtn);

        JButton settingsBtn = new JButton(IconFactory.phosphor("gear.svg", 18, Color.WHITE));
        styleControlBtn(settingsBtn, new Color(0x3B, 0x3D, 0x44));
        settingsBtn.setToolTipText("Cài đặt thiết bị âm thanh (Microphone & Loa)");
        settingsBtn.addActionListener(e -> {
            if (onOpenVoiceSettings != null) {
                onOpenVoiceSettings.run();
            }
        });
        bar.add(settingsBtn);

        JButton leaveBtn = new JButton("Kết thúc cuộc gọi", IconFactory.phosphor("phone.svg", 18, Color.WHITE));
        styleControlBtn(leaveBtn, DiscordTheme.DANGER);
        leaveBtn.setFont(DiscordTheme.bold(12f));
        leaveBtn.addActionListener(e -> onLeave.run());
        bar.add(leaveBtn);

        return bar;
    }

    private void styleControlBtn(JButton btn, Color bg) {
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
    }

    private void showShareScreenDialog() {
        JFrame parent = (JFrame) SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(parent, "Chia sẻ màn hình", true);
        dialog.setUndecorated(true);
        dialog.setBackground(new Color(0, 0, 0, 0));

        final int[] selectedSource = {1}; // 1 = Toàn màn hình, 2 = Cửa sổ ứng dụng
        final boolean[] hdQuality = {false};

        RoundedPanel p = new RoundedPanel(12);
        p.setBackground(DiscordTheme.SIDEBAR);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x3B, 0x3D, 0x44), 1),
                BorderFactory.createEmptyBorder(20, 22, 18, 22)
        ));
        p.setLayout(new BorderLayout(0, 16));

        JLabel title = new JLabel("Chia sẻ màn hình, cửa sổ hoặc ứng dụng", JLabel.CENTER);
        title.setFont(DiscordTheme.bold(16f));
        title.setForeground(Color.WHITE);
        p.add(title, BorderLayout.NORTH);

        JPanel sources = new JPanel(new GridLayout(1, 2, 14, 0));
        sources.setOpaque(false);

        RoundedPanel screenOpt = buildSourceOption("Chia sẻ toàn màn hình", "Hiển thị toàn bộ màn hình máy tính");
        RoundedPanel windowOpt = buildSourceOption("Chia sẻ cửa sổ", "Chọn một ứng dụng hoặc trò chơi đang mở");

        Runnable updateSelection = () -> {
            styleSourceCard(screenOpt, selectedSource[0] == 1);
            styleSourceCard(windowOpt, selectedSource[0] == 2);
        };
        updateSelection.run();

        Runnable confirmShare = () -> {
            dialog.dispose();
            if (onStartScreenShareConfig != null) {
                onStartScreenShareConfig.accept(selectedSource[0], hdQuality[0]);
            } else if (onStartScreenShare != null) {
                onStartScreenShare.run();
            }
        };

        MouseAdapter screenListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                selectedSource[0] = 1;
                updateSelection.run();
                if (e.getClickCount() == 2) confirmShare.run();
            }
        };
        attachCardClickListener(screenOpt, screenListener);

        MouseAdapter windowListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                selectedSource[0] = 2;
                updateSelection.run();
                if (e.getClickCount() == 2) confirmShare.run();
            }
        };
        attachCardClickListener(windowOpt, windowListener);

        sources.add(screenOpt);
        sources.add(windowOpt);
        p.add(sources, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setOpaque(false);

        JButton qualityBtn = new JButton("Chất lượng: 720p · 30 FPS");
        qualityBtn.setFont(DiscordTheme.semibold(11f));
        qualityBtn.setForeground(DiscordTheme.TEXT_PRIMARY);
        qualityBtn.setBackground(new Color(0x2B, 0x2D, 0x31));
        qualityBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x3B, 0x3D, 0x44), 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        qualityBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        qualityBtn.addActionListener(e -> {
            hdQuality[0] = !hdQuality[0];
            qualityBtn.setText(hdQuality[0] ? "Chất lượng: 1080p · 30 FPS" : "Chất lượng: 720p · 30 FPS");
        });
        bottomBar.add(qualityBtn, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton cancel = new JButton("Hủy");
        cancel.setFont(DiscordTheme.semibold(12f));
        cancel.setForeground(DiscordTheme.TEXT_SECONDARY);
        cancel.setContentAreaFilled(false);
        cancel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cancel.addActionListener(e -> dialog.dispose());
        actions.add(cancel);

        JButton share = new JButton("Bắt đầu chia sẻ");
        share.setFont(DiscordTheme.bold(12f));
        share.setForeground(Color.WHITE);
        share.setBackground(DiscordTheme.ACCENT);
        share.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        share.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        share.addActionListener(e -> confirmShare.run());
        actions.add(share);
        bottomBar.add(actions, BorderLayout.EAST);

        p.add(bottomBar, BorderLayout.SOUTH);

        dialog.setContentPane(p);
        dialog.setSize(520, 270);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private void styleSourceCard(RoundedPanel card, boolean selected) {
        card.setBackground(selected ? new Color(0x35, 0x37, 0x3C) : new Color(0x2B, 0x2D, 0x31));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(selected ? DiscordTheme.ACCENT : new Color(0x3B, 0x3D, 0x44), selected ? 2 : 1),
                BorderFactory.createEmptyBorder(selected ? 17 : 18, selected ? 13 : 14, selected ? 17 : 18, selected ? 13 : 14)
        ));
        card.repaint();
    }

    private RoundedPanel buildSourceOption(String name, String sub) {
        RoundedPanel opt = new RoundedPanel(8);
        opt.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        opt.setLayout(new BoxLayout(opt, BoxLayout.Y_AXIS));

        JLabel icon = new JLabel(IconFactory.phosphor("monitor.svg", 32, DiscordTheme.ACCENT_SOFT));
        icon.setAlignmentX(CENTER_ALIGNMENT);
        opt.add(icon);
        opt.add(Box.createVerticalStrut(10));

        JLabel nameLbl = new JLabel(name);
        nameLbl.setFont(DiscordTheme.bold(13f));
        nameLbl.setForeground(Color.WHITE);
        nameLbl.setAlignmentX(CENTER_ALIGNMENT);
        opt.add(nameLbl);
        opt.add(Box.createVerticalStrut(4));

        JLabel subLbl = new JLabel(sub);
        subLbl.setFont(DiscordTheme.font(11f));
        subLbl.setForeground(DiscordTheme.TEXT_MUTED);
        subLbl.setAlignmentX(CENTER_ALIGNMENT);
        opt.add(subLbl);

        return opt;
    }

    private void attachCardClickListener(JComponent comp, MouseListener listener) {
        comp.addMouseListener(listener);
        for (java.awt.Component child : comp.getComponents()) {
            if (child instanceof JComponent jc) {
                attachCardClickListener(jc, listener);
            } else {
                child.addMouseListener(listener);
            }
        }
    }
}

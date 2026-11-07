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
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Video/voice room interface with live audio status, duration timer, and dynamic participants. */
public final class MeetRoomPanel extends JPanel {
    private final Runnable onLeave;
    private Runnable onOpenVoiceSettings;
    private Runnable onOpenChat;
    private Runnable onWatchTogether;
    private Runnable onStartScreenShare;
    private Runnable onStopScreenShare;
    private Consumer<Boolean> onMuteToggle;
    private Consumer<Boolean> onCamToggle;
    private boolean micMuted = false;
    private boolean micAvailable = true;
    private boolean camMuted = true;
    private boolean screenSharingActive = false;
    private boolean speakerLayoutMode = false;
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
    private JButton micBtn;
    private JButton camBtn;
    private JButton shareBtn;
    private JButton layoutModeBtn;

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

        add(buildHeader(), BorderLayout.NORTH);
        add(centerHost, BorderLayout.CENTER);
        add(buildBottomControls(), BorderLayout.SOUTH);
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

    public void setOnStopScreenShare(Runnable onStopScreenShare) {
        this.onStopScreenShare = onStopScreenShare;
    }

    public void setScreenSharingActive(boolean active) {
        this.screenSharingActive = active;
        if (shareBtn != null) {
            shareBtn.setText(active ? "Dừng chia sẻ" : "Chia sẻ màn hình");
            shareBtn.setBackground(active ? DiscordTheme.DANGER : new Color(0x3B, 0x3D, 0x44));
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
        // Clear all cached video frames from previous calls/sessions
        videoFrames.clear();
        tileViews.clear();
        participants.clear();
        this.pinnedParticipantId = null;
        this.lastSpeakingParticipantId = null;
        this.speakerLayoutMode = false;
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
        String screenId = id + ":screen";
        boolean had = videoFrames.containsKey(screenId);
        if (img != null) {
            videoFrames.put(screenId, img);
            if (!had) {
                String title = (name != null && !name.isBlank()) ? "Màn hình: " + name : "Màn hình chia sẻ";
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

    public void showWatchTogetherStage(javax.swing.JComponent playerComponent, Runnable onStop) {
        watchStageContainer.removeAll();

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DiscordTheme.HAIRLINE),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)
        ));

        JLabel titleLbl = new JLabel("Xem YouTube cùng nhau", IconFactory.phosphor("video-camera.svg", 18, DiscordTheme.ACCENT), JLabel.LEFT);
        titleLbl.setFont(DiscordTheme.bold(14f));
        titleLbl.setForeground(Color.WHITE);
        topBar.add(titleLbl, BorderLayout.WEST);

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
        topBar.add(stopBtn, BorderLayout.EAST);

        watchStageContainer.add(topBar, BorderLayout.NORTH);
        watchStageContainer.add(playerComponent, BorderLayout.CENTER);

        refreshWatchStrip();
        JScrollPane scrollStrip = new JScrollPane(watchStrip, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollStrip.setOpaque(false);
        scrollStrip.getViewport().setOpaque(false);
        scrollStrip.setBorder(BorderFactory.createEmptyBorder());
        scrollStrip.setPreferredSize(new Dimension(0, 105));
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
            smallTile.setPreferredSize(new Dimension(140, 90));
            tileViews.put(p.id(), smallTile);
            watchStrip.add(smallTile);
        }
        watchStrip.revalidate();
        watchStrip.repaint();
    }

    public int getParticipantCount() {
        return participants.size();
    }

    public String stopSessionAndFormatDuration() {
        callTimer.stop();
        setScreenSharingActive(false);
        hideWatchTogetherStage();
        videoFrames.clear();
        tileViews.clear();
        participants.clear();
        int mins = elapsedSeconds / 60;
        int secs = elapsedSeconds % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    private void updateHeaderStatus() {
        int mins = elapsedSeconds / 60;
        int secs = elapsedSeconds % 60;
        statusLabel.setText(String.format("· %02d:%02d · %d người trong cuộc gọi", mins, secs, Math.max(1, participants.size())));
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

        JButton chatBtn = new JButton("Mở đoạn chat", IconFactory.phosphor("chat-circle-dots.svg", 16, DiscordTheme.TEXT_PRIMARY));
        chatBtn.setFont(DiscordTheme.semibold(12f));
        chatBtn.setForeground(DiscordTheme.TEXT_PRIMARY);
        chatBtn.setBackground(new Color(0x2B, 0x2D, 0x31));
        chatBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x3B, 0x3D, 0x44), 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        chatBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        chatBtn.setFocusPainted(false);
        chatBtn.setToolTipText("Xem và gửi tin nhắn văn bản trong khi đang gọi");
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
        RoundedPanel tile = new RoundedPanel(10) {
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                BufferedImage frame = videoFrames.get(id);
                if (frame != null) {
                    Graphics2D g2 = (Graphics2D) graphics.create();
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2.setClip(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                    int w = getWidth();
                    int h = getHeight();
                    boolean isScreen = id.endsWith(":screen");
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
                    g2.drawImage(frame, dx, dy, dw, dh, null);
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
        JLabel nameLbl = new JLabel(isPinned ? ("📌 " + name) : name);
        nameLbl.setFont(DiscordTheme.bold(compact ? 11f : 12f));
        nameLbl.setForeground(Color.WHITE);
        tagPill.add(nameLbl);

        if (!compact && sub != null && !sub.isBlank()) {
            JLabel subLbl = new JLabel("· " + sub);
            subLbl.setFont(DiscordTheme.font(10f));
            subLbl.setForeground(DiscordTheme.TEXT_MUTED);
            tagPill.add(subLbl);
        }

        JPanel southWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        southWrap.setOpaque(false);
        southWrap.add(tagPill);
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

        RoundedPanel p = new RoundedPanel(12);
        p.setBackground(DiscordTheme.SIDEBAR);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x3B, 0x3D, 0x44), 1),
                BorderFactory.createEmptyBorder(18, 18, 18, 18)
        ));
        p.setLayout(new BorderLayout(0, 14));

        JLabel title = new JLabel("Chọn nguồn chia sẻ màn hình");
        title.setFont(DiscordTheme.bold(16f));
        title.setForeground(Color.WHITE);
        p.add(title, BorderLayout.NORTH);

        JPanel sources = new JPanel(new GridLayout(1, 3, 10, 0));
        sources.setOpaque(false);
        sources.add(buildSourceOption("Toàn màn hình", "Chia sẻ toàn bộ máy"));
        sources.add(buildSourceOption("Cửa sổ", "Chọn một ứng dụng"));
        sources.add(buildSourceOption("Màn hình 1", "Màn hình ngoài"));
        p.add(sources, BorderLayout.CENTER);

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
        share.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        share.addActionListener(e -> {
            dialog.dispose();
            if (onStartScreenShare != null) {
                onStartScreenShare.run();
            }
        });
        actions.add(share);
        p.add(actions, BorderLayout.SOUTH);

        dialog.setContentPane(p);
        dialog.setSize(480, 240);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private JPanel buildSourceOption(String name, String sub) {
        RoundedPanel opt = new RoundedPanel(8);
        opt.setBackground(new Color(0x2B, 0x2D, 0x31));
        opt.setBorder(BorderFactory.createEmptyBorder(12, 10, 12, 10));
        opt.setLayout(new BoxLayout(opt, BoxLayout.Y_AXIS));

        JLabel icon = new JLabel(IconFactory.phosphor("monitor.svg", 32, DiscordTheme.ACCENT_SOFT));
        icon.setAlignmentX(CENTER_ALIGNMENT);
        opt.add(icon);
        opt.add(Box.createVerticalStrut(8));

        JLabel nameLbl = new JLabel(name);
        nameLbl.setFont(DiscordTheme.bold(12f));
        nameLbl.setForeground(Color.WHITE);
        nameLbl.setAlignmentX(CENTER_ALIGNMENT);
        opt.add(nameLbl);

        JLabel subLbl = new JLabel(sub);
        subLbl.setFont(DiscordTheme.font(10f));
        subLbl.setForeground(DiscordTheme.TEXT_MUTED);
        subLbl.setAlignmentX(CENTER_ALIGNMENT);
        opt.add(subLbl);

        return opt;
    }
}

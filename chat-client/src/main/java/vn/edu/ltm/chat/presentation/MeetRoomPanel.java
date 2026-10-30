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
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Video/voice room interface with live audio status, duration timer, and dynamic participants. */
public final class MeetRoomPanel extends JPanel {
    private final Runnable onLeave;
    private Runnable onOpenVoiceSettings;
    private Consumer<Boolean> onMuteToggle;
    private boolean micMuted = false;
    private boolean camMuted = true;

    private final JLabel titleLabel = new JLabel("Cuộc gọi thoại");
    private final JLabel statusLabel = new JLabel("· 00:00 · Đang kết nối âm thanh...");
    private final JPanel grid = new JPanel(new GridLayout(1, 2, 12, 12));
    private JButton micBtn;

    private final Map<String, ParticipantEntry> participants = new LinkedHashMap<>();
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

        add(buildHeader(), BorderLayout.NORTH);
        add(buildVideoGrid(), BorderLayout.CENTER);
        add(buildBottomControls(), BorderLayout.SOUTH);
    }

    public void setOnMuteToggle(Consumer<Boolean> onMuteToggle) {
        this.onMuteToggle = onMuteToggle;
    }

    public void setOnOpenVoiceSettings(Runnable onOpenVoiceSettings) {
        this.onOpenVoiceSettings = onOpenVoiceSettings;
    }

    public void startSession(String roomTitle, String selfId, String selfInitials, String selfName, boolean micReady) {
        this.elapsedSeconds = 0;
        this.micMuted = false;
        if (micBtn != null) {
            micBtn.setBackground(new Color(0x3B, 0x3D, 0x44));
        }
        titleLabel.setText(roomTitle != null && !roomTitle.isBlank() ? roomTitle : "Cuộc gọi thoại");
        participants.clear();
        String micSub = micReady ? "Bạn · Mic đang bật" : "Bạn · Chế độ nghe (không tìm thấy mic)";
        participants.put(selfId, new ParticipantEntry(selfId, selfInitials, selfName + " (Bạn)", micSub, false));
        rebuildGrid();
        updateHeaderStatus();
        callTimer.restart();
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
            participants.put(id, new ParticipantEntry(existing.id(), existing.initials(), existing.name(), existing.sub(), speaking));
            rebuildGrid();
        }
    }

    public void removeParticipant(String id) {
        if (id != null && participants.remove(id) != null) {
            rebuildGrid();
            updateHeaderStatus();
        }
    }

    public int getParticipantCount() {
        return participants.size();
    }

    public String stopSessionAndFormatDuration() {
        callTimer.stop();
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
        return header;
    }

    private JPanel buildVideoGrid() {
        grid.setOpaque(false);
        grid.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        return grid;
    }

    private void rebuildGrid() {
        grid.removeAll();
        int count = Math.max(1, participants.size());
        int rows = count <= 2 ? 1 : 2;
        int cols = count == 1 ? 1 : 2;
        grid.setLayout(new GridLayout(rows, cols, 12, 12));

        for (ParticipantEntry p : participants.values()) {
            grid.add(buildParticipantTile(p.initials(), p.name(), p.sub(), p.speaking()));
        }
        grid.revalidate();
        grid.repaint();
    }

    private JPanel buildParticipantTile(String initials, String name, String sub, boolean speaking) {
        RoundedPanel tile = new RoundedPanel(10);
        tile.setBackground(new Color(0x1E, 0x1F, 0x23));
        Color borderColor = speaking ? DiscordTheme.SUCCESS : new Color(0x2B, 0x2D, 0x31);
        tile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, speaking ? 2 : 1),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));
        tile.setLayout(new BorderLayout());

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);

        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

        AvatarBadge avatar = new AvatarBadge(initials, name, 64);
        avatar.setAlignmentX(CENTER_ALIGNMENT);
        inner.add(avatar);
        inner.add(Box.createVerticalStrut(10));

        JLabel status = new JLabel(speaking ? "Đang nói..." : "Đang kết nối âm thanh trực tiếp");
        status.setFont(DiscordTheme.font(11f));
        status.setForeground(speaking ? DiscordTheme.SUCCESS : DiscordTheme.TEXT_MUTED);
        status.setAlignmentX(CENTER_ALIGNMENT);
        inner.add(status);

        center.add(inner);
        tile.add(center, BorderLayout.CENTER);

        JPanel tag = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        tag.setOpaque(false);
        JLabel nameLbl = new JLabel(name);
        nameLbl.setFont(DiscordTheme.bold(12f));
        nameLbl.setForeground(Color.WHITE);
        tag.add(nameLbl);

        JLabel subLbl = new JLabel("· " + sub);
        subLbl.setFont(DiscordTheme.font(10f));
        subLbl.setForeground(DiscordTheme.TEXT_MUTED);
        tag.add(subLbl);

        tile.add(tag, BorderLayout.SOUTH);
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
            if (onMuteToggle != null) {
                onMuteToggle.accept(micMuted);
            }
        });
        bar.add(micBtn);

        JButton camBtn = new JButton(IconFactory.phosphor("video-camera.svg", 18, Color.WHITE));
        styleControlBtn(camBtn, DiscordTheme.DANGER);
        camBtn.setToolTipText("Bật/Tắt camera");
        camBtn.addActionListener(e -> {
            camMuted = !camMuted;
            camBtn.setBackground(camMuted ? DiscordTheme.DANGER : new Color(0x3B, 0x3D, 0x44));
        });
        bar.add(camBtn);

        JButton shareBtn = new JButton("Chia sẻ màn hình", IconFactory.phosphor("monitor.svg", 18, Color.WHITE));
        styleControlBtn(shareBtn, new Color(0x3B, 0x3D, 0x44));
        shareBtn.setFont(DiscordTheme.semibold(12f));
        shareBtn.addActionListener(e -> showShareScreenDialog());
        bar.add(shareBtn);

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
        share.addActionListener(e -> dialog.dispose());
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

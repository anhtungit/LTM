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
import java.awt.Component;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;

/** Meet channel waiting lobby (Canvas 05). */
public final class MeetLobbyPanel extends JPanel {
    private final Runnable onJoinRoom;
    private final Runnable onBackToChat;
    private Runnable onOpenVoiceSettings;
    private boolean micEnabled = true;
    private boolean camEnabled = false;
    private final JLabel headerTitle = new JLabel("phòng-họp");
    private final JLabel centerSubtitle = new JLabel("Kiểm tra thiết bị trước khi bắt đầu cuộc gọi.");

    public MeetLobbyPanel(Runnable onJoinRoom, Runnable onBackToChat) {
        this.onJoinRoom = onJoinRoom;
        this.onBackToChat = onBackToChat;

        setLayout(new BorderLayout());
        setBackground(DiscordTheme.TRANSCRIPT);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
    }

    public void updateRoomInfo(String roomName) {
        String name = (roomName != null && !roomName.isBlank()) ? roomName : "phòng-họp";
        headerTitle.setText(name);
        centerSubtitle.setText("Kiểm tra thiết bị trước khi bắt đầu cuộc gọi với " + name + ".");
    }

    public void setOnOpenVoiceSettings(Runnable onOpenVoiceSettings) {
        this.onOpenVoiceSettings = onOpenVoiceSettings;
    }

    public boolean isMicEnabled() {
        return micEnabled;
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

        JLabel hash = new JLabel(IconFactory.phosphor("headset.svg", 22, DiscordTheme.ACCENT_SOFT));
        left.add(hash);

        headerTitle.setFont(DiscordTheme.bold(16f));
        headerTitle.setForeground(Color.WHITE);
        left.add(headerTitle);

        JLabel desc = new JLabel("· Sảnh chờ cuộc gọi thoại trực tiếp");
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

        RoundedPanel card = new RoundedPanel(12);
        card.setBackground(DiscordTheme.SIDEBAR);
        card.setPreferredSize(new Dimension(540, 480));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x3B, 0x3D, 0x44), 1),
                BorderFactory.createEmptyBorder(24, 24, 24, 24)
        ));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Sẵn sàng vào cuộc gọi?");
        title.setFont(DiscordTheme.bold(20f));
        title.setForeground(Color.WHITE);
        title.setAlignmentX(CENTER_ALIGNMENT);
        card.add(title);

        centerSubtitle.setFont(DiscordTheme.font(12f));
        centerSubtitle.setForeground(DiscordTheme.TEXT_SECONDARY);
        centerSubtitle.setAlignmentX(CENTER_ALIGNMENT);
        card.add(centerSubtitle);
        card.add(Box.createVerticalStrut(18));

        // Camera preview box
        RoundedPanel previewBox = new RoundedPanel(10);
        previewBox.setBackground(new Color(0x15, 0x16, 0x19));
        previewBox.setPreferredSize(new Dimension(490, 200));
        previewBox.setMaximumSize(new Dimension(Short.MAX_VALUE, 200));
        previewBox.setLayout(new GridBagLayout());

        JPanel previewContent = new JPanel();
        previewContent.setOpaque(false);
        previewContent.setLayout(new BoxLayout(previewContent, BoxLayout.Y_AXIS));

        JLabel camIcon = new JLabel(IconFactory.phosphor("headset.svg", 36, DiscordTheme.SUCCESS));
        camIcon.setAlignmentX(CENTER_ALIGNMENT);
        previewContent.add(camIcon);
        previewContent.add(Box.createVerticalStrut(8));

        JLabel camNotice = new JLabel("Âm thanh trực tiếp đã sẵn sàng");
        camNotice.setFont(DiscordTheme.bold(14f));
        camNotice.setForeground(DiscordTheme.TEXT_PRIMARY);
        camNotice.setAlignmentX(CENTER_ALIGNMENT);
        previewContent.add(camNotice);

        JLabel camSub = new JLabel("Truyền giọng nói PCM thời gian thực qua kết nối TCP.");
        camSub.setFont(DiscordTheme.font(11f));
        camSub.setForeground(DiscordTheme.TEXT_MUTED);
        camSub.setAlignmentX(CENTER_ALIGNMENT);
        previewContent.add(camSub);

        previewBox.add(previewContent);
        previewBox.setAlignmentX(CENTER_ALIGNMENT);
        card.add(previewBox);
        card.add(Box.createVerticalStrut(16));

        // Toggle buttons row
        JPanel toggleRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        toggleRow.setOpaque(false);

        JButton micBtn = new JButton("Micrô: Bật", IconFactory.phosphor("microphone.svg", 16, DiscordTheme.TEXT_PRIMARY));
        styleDeviceBtn(micBtn);
        micBtn.setForeground(DiscordTheme.SUCCESS);
        micBtn.addActionListener(e -> {
            micEnabled = !micEnabled;
            micBtn.setText(micEnabled ? "Micrô: Bật" : "Micrô: Tắt");
            micBtn.setForeground(micEnabled ? DiscordTheme.SUCCESS : DiscordTheme.TEXT_PRIMARY);
        });
        toggleRow.add(micBtn);

        JButton camBtn = new JButton("Camera: Tắt", IconFactory.phosphor("video-camera.svg", 16, DiscordTheme.TEXT_PRIMARY));
        styleDeviceBtn(camBtn);
        camBtn.addActionListener(e -> {
            camEnabled = !camEnabled;
            camBtn.setText(camEnabled ? "Camera: Bật" : "Camera: Tắt");
            camBtn.setForeground(camEnabled ? DiscordTheme.SUCCESS : DiscordTheme.TEXT_PRIMARY);
        });
        toggleRow.add(camBtn);

        JButton settingsBtn = new JButton("Cài đặt thiết bị", IconFactory.phosphor("gear.svg", 16, DiscordTheme.TEXT_PRIMARY));
        styleDeviceBtn(settingsBtn);
        settingsBtn.addActionListener(e -> {
            if (onOpenVoiceSettings != null) {
                onOpenVoiceSettings.run();
            }
        });
        toggleRow.add(settingsBtn);

        card.add(toggleRow);
        card.add(Box.createVerticalStrut(18));

        // Join button
        JButton joinBtn = new JButton("Vào phòng");
        joinBtn.setFont(DiscordTheme.bold(14f));
        joinBtn.setForeground(Color.WHITE);
        joinBtn.setBackground(DiscordTheme.ACCENT);
        joinBtn.setAlignmentX(CENTER_ALIGNMENT);
        joinBtn.setPreferredSize(new Dimension(200, 40));
        joinBtn.setMaximumSize(new Dimension(200, 40));
        joinBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        joinBtn.addActionListener(e -> onJoinRoom.run());
        card.add(joinBtn);

        host.add(card);
        return host;
    }

    private void styleDeviceBtn(JButton btn) {
        btn.setFont(DiscordTheme.semibold(12f));
        btn.setForeground(DiscordTheme.TEXT_PRIMARY);
        btn.setBackground(new Color(0x2B, 0x2D, 0x31));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x3B, 0x3D, 0x44), 1),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)
        ));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private JPanel buildRightSidebar() {
        JPanel pane = new JPanel(new BorderLayout());
        pane.setBackground(DiscordTheme.SIDEBAR);
        pane.setPreferredSize(new Dimension(220, 0));
        pane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, DiscordTheme.HAIRLINE),
                BorderFactory.createEmptyBorder(16, 14, 16, 14)
        ));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("ĐANG Ở TRONG PHÒNG");
        title.setFont(DiscordTheme.extrabold(10f));
        title.setForeground(DiscordTheme.TEXT_MUTED);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(12));

        content.add(buildMemberTile("MA", "Minh Anh", "Mẫu · đang trực tuyến"));
        content.add(Box.createVerticalStrut(8));
        content.add(buildMemberTile("KL", "Khánh Linh", "Mẫu · đang trực tuyến"));
        content.add(Box.createVerticalStrut(8));
        content.add(buildMemberTile("HP", "Hữu Phúc", "Mẫu · đang trực tuyến"));

        pane.add(content, BorderLayout.NORTH);
        return pane;
    }

    private JPanel buildMemberTile(String initials, String name, String status) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        row.setPreferredSize(new Dimension(190, 36));
        row.add(new AvatarBadge(initials, name, 28, DiscordTheme.SUCCESS, DiscordTheme.SIDEBAR, true), BorderLayout.WEST);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

        JLabel nameLbl = new JLabel(name);
        nameLbl.setFont(DiscordTheme.semibold(12f));
        nameLbl.setForeground(Color.WHITE);
        nameLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(nameLbl);

        JLabel statLbl = new JLabel(status);
        statLbl.setFont(DiscordTheme.font(10f));
        statLbl.setForeground(DiscordTheme.TEXT_MUTED);
        statLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(statLbl);

        row.add(info, BorderLayout.CENTER);
        return row;
    }
}


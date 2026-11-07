package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.media.VideoCaptureEngine;
import vn.edu.ltm.chat.theme.DiscordTheme;
import vn.edu.ltm.chat.theme.IconFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** Meet channel waiting lobby (Canvas 05). */
public final class MeetLobbyPanel extends JPanel {
    private final Runnable onJoinRoom;
    private final Runnable onBackToChat;
    private Runnable onOpenVoiceSettings;
    private boolean micEnabled = true;
    private boolean camEnabled = false;
    private final VideoCaptureEngine lobbyCamEngine = new VideoCaptureEngine();
    private volatile BufferedImage lobbyPreviewImage;
    private RoundedPanel previewBox;
    private JPanel previewContent;
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
        if (!name.startsWith("#") && !name.isEmpty()) {
            name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
        headerTitle.setText(name);
        centerSubtitle.setText("Kiểm tra thiết bị trước khi bắt đầu cuộc gọi với " + name + ".");
    }

    public void setOnOpenVoiceSettings(Runnable onOpenVoiceSettings) {
        this.onOpenVoiceSettings = onOpenVoiceSettings;
    }

    public boolean isMicEnabled() {
        return micEnabled;
    }

    public boolean isCamEnabled() {
        return camEnabled;
    }

    public void stopLobbyPreview() {
        lobbyCamEngine.stop();
        lobbyPreviewImage = null;
        if (previewContent != null) {
            previewContent.setVisible(true);
        }
        if (previewBox != null) {
            previewBox.repaint();
        }
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
        backBtn.addActionListener(e -> {
            stopLobbyPreview();
            onBackToChat.run();
        });
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
        previewBox = new RoundedPanel(10) {
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                BufferedImage frame = lobbyPreviewImage;
                if (frame != null) {
                    Graphics2D g2 = (Graphics2D) graphics.create();
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2.setClip(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                    int w = getWidth();
                    int h = getHeight();
                    double scale = Math.max((double) w / frame.getWidth(), (double) h / frame.getHeight());
                    int dw = (int) Math.round(frame.getWidth() * scale);
                    int dh = (int) Math.round(frame.getHeight() * scale);
                    int dx = (w - dw) / 2;
                    int dy = (h - dh) / 2;
                    g2.drawImage(frame, dx, dy, dw, dh, null);
                    g2.dispose();
                }
            }
        };
        previewBox.setBackground(new Color(0x15, 0x16, 0x19));
        previewBox.setPreferredSize(new Dimension(490, 200));
        previewBox.setMaximumSize(new Dimension(Short.MAX_VALUE, 200));
        previewBox.setLayout(new GridBagLayout());

        previewContent = new JPanel();
        previewContent.setOpaque(false);
        previewContent.setLayout(new BoxLayout(previewContent, BoxLayout.Y_AXIS));

        JLabel camIcon = new JLabel(IconFactory.phosphor("headset.svg", 36, DiscordTheme.SUCCESS));
        camIcon.setAlignmentX(CENTER_ALIGNMENT);
        previewContent.add(camIcon);
        previewContent.add(Box.createVerticalStrut(8));

        JLabel camNotice = new JLabel("Âm thanh & Camera trực tiếp sẵn sàng");
        camNotice.setFont(DiscordTheme.bold(14f));
        camNotice.setForeground(DiscordTheme.TEXT_PRIMARY);
        camNotice.setAlignmentX(CENTER_ALIGNMENT);
        previewContent.add(camNotice);

        JLabel camSub = new JLabel("Kiểm tra micro và camera trước khi tham gia.");
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
            if (camEnabled) {
                lobbyCamEngine.start(null, img -> SwingUtilities.invokeLater(() -> {
                    if (camEnabled) {
                        lobbyPreviewImage = img;
                        previewContent.setVisible(false);
                        previewBox.repaint();
                    }
                }));
            } else {
                stopLobbyPreview();
            }
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

        // Action buttons row (Join & Cancel)
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        actionRow.setOpaque(false);
        actionRow.setAlignmentX(CENTER_ALIGNMENT);

        JButton cancelBtn = new JButton("Rời sảnh", IconFactory.phosphor("x.svg", 16, DiscordTheme.TEXT_SECONDARY));
        cancelBtn.setFont(DiscordTheme.bold(14f));
        cancelBtn.setForeground(DiscordTheme.TEXT_PRIMARY);
        cancelBtn.setBackground(DiscordTheme.COMPOSER);
        cancelBtn.setPreferredSize(new Dimension(140, 40));
        cancelBtn.setMaximumSize(new Dimension(140, 40));
        cancelBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cancelBtn.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        cancelBtn.addActionListener(e -> {
            stopLobbyPreview();
            if (onBackToChat != null) {
                onBackToChat.run();
            }
        });
        actionRow.add(cancelBtn);

        JButton joinBtn = new JButton("Vào phòng", IconFactory.phosphor("phone.svg", 16, Color.WHITE));
        joinBtn.setFont(DiscordTheme.bold(14f));
        joinBtn.setForeground(Color.WHITE);
        joinBtn.setBackground(DiscordTheme.SUCCESS);
        joinBtn.setPreferredSize(new Dimension(160, 40));
        joinBtn.setMaximumSize(new Dimension(160, 40));
        joinBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        joinBtn.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        joinBtn.addActionListener(e -> {
            stopLobbyPreview();
            onJoinRoom.run();
        });
        actionRow.add(joinBtn);

        card.add(actionRow);

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

}


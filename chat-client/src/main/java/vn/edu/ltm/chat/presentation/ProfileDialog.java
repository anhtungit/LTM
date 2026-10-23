package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.domain.UserProfile;
import vn.edu.ltm.chat.theme.DiscordTheme;
import vn.edu.ltm.chat.theme.IconFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.function.Consumer;

/**
 * Complete User Profile & Settings Modal matching Kombai Canvas 07.
 * Includes interactive live preview, avatar chooser, status selection, and sound settings.
 */
public final class ProfileDialog extends JDialog {
    private final UserProfile user;
    private final Consumer<UserProfile> onSave;

    // Form fields
    private final JTextField displayNameField;
    private final JTextField usernameField;
    private final JTextArea bioArea;
    private final JComboBox<String> statusCombo;
    private String currentAvatarPath;
    private String currentStatus;

    // Live preview components
    private final JPanel previewAvatarHolder = new JPanel(new BorderLayout());
    private final JLabel previewName = new JLabel();
    private final JLabel previewUsername = new JLabel();
    private final JLabel previewStatusDot = new JLabel();
    private final JLabel previewStatusText = new JLabel();
    private final JLabel previewBio = new JLabel();
    private final JPanel formAvatarHolder = new JPanel(new BorderLayout());

    // Navigation cards
    private final CardLayout cards = new CardLayout();
    private final JPanel cardsPanel = new JPanel(cards);
    private JButton profileNavBtn;
    private JButton audioNavBtn;
    private JButton notifNavBtn;

    public ProfileDialog(JFrame parent, UserProfile user, Consumer<UserProfile> onSave) {
        super(parent, "Cài đặt người dùng", true);
        this.user = user;
        this.onSave = onSave;
        this.currentAvatarPath = user.avatarPath();
        this.currentStatus = user.status();

        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));

        this.displayNameField = new JTextField(user.displayName());
        this.usernameField = new JTextField(user.username());
        this.bioArea = new JTextArea(user.bio(), 3, 20);
        this.statusCombo = new JComboBox<>(new String[]{
                "Đang hoạt động", "Chờ", "Không làm phiền", "Ẩn danh"
        });
        selectStatusInCombo(user.status());

        RoundedPanel container = new RoundedPanel(14);
        container.setBackground(DiscordTheme.SIDEBAR);
        container.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x3B, 0x3D, 0x44), 1),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)
        ));
        container.setLayout(new BorderLayout());

        container.add(buildLeftNav(), BorderLayout.WEST);

        cardsPanel.setOpaque(false);
        cardsPanel.add(buildProfileTab(), "profile");
        cardsPanel.add(buildAudioVideoTab(), "audio");
        cardsPanel.add(buildNotificationsTab(), "notif");
        container.add(cardsPanel, BorderLayout.CENTER);

        setContentPane(container);
        setSize(860, 600);
        setLocationRelativeTo(parent);

        setupLivePreviewSync();
    }

    private void selectStatusInCombo(String status) {
        switch (status) {
            case "idle" -> statusCombo.setSelectedItem("Chờ");
            case "dnd" -> statusCombo.setSelectedItem("Không làm phiền");
            case "offline" -> statusCombo.setSelectedItem("Ẩn danh");
            default -> statusCombo.setSelectedItem("Đang hoạt động");
        }
    }

    private String getSelectedStatus() {
        String sel = (String) statusCombo.getSelectedItem();
        if ("Chờ".equals(sel)) return "idle";
        if ("Không làm phiền".equals(sel)) return "dnd";
        if ("Ẩn danh".equals(sel)) return "offline";
        return "online";
    }

    private JPanel buildLeftNav() {
        JPanel nav = new JPanel();
        nav.setBackground(new Color(0x19, 0x1A, 0x1E));
        nav.setPreferredSize(new Dimension(210, 0));
        nav.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, DiscordTheme.HAIRLINE),
                BorderFactory.createEmptyBorder(22, 16, 22, 16)
        ));
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("CÀI ĐẶT NGƯỜI DÙNG");
        title.setFont(DiscordTheme.extrabold(11f));
        title.setForeground(DiscordTheme.TEXT_MUTED);
        nav.add(title);
        nav.add(Box.createVerticalStrut(14));

        profileNavBtn = navButton("Hồ sơ", "user-circle.svg", true);
        profileNavBtn.addActionListener(e -> switchTab("profile", profileNavBtn));
        nav.add(profileNavBtn);
        nav.add(Box.createVerticalStrut(4));

        audioNavBtn = navButton("Âm thanh & video", "headset.svg", false);
        audioNavBtn.addActionListener(e -> switchTab("audio", audioNavBtn));
        nav.add(audioNavBtn);
        nav.add(Box.createVerticalStrut(4));

        notifNavBtn = navButton("Thông báo", "tray.svg", false);
        notifNavBtn.addActionListener(e -> switchTab("notif", notifNavBtn));
        nav.add(notifNavBtn);

        nav.add(Box.createVerticalGlue());

        JPanel versionCard = new JPanel(new BorderLayout());
        versionCard.setOpaque(false);
        JLabel ver = new JLabel("LTM Chat v1.0 · Canvas 07");
        ver.setFont(DiscordTheme.font(10f));
        ver.setForeground(DiscordTheme.TEXT_MUTED);
        versionCard.add(ver, BorderLayout.CENTER);
        nav.add(versionCard);

        return nav;
    }

    private JButton navButton(String text, String iconName, boolean active) {
        JButton btn = new JButton(text, IconFactory.phosphor(iconName, 16,
                active ? Color.WHITE : DiscordTheme.TEXT_SECONDARY));
        btn.setFont(active ? DiscordTheme.bold(12f) : DiscordTheme.semibold(12f));
        btn.setForeground(active ? Color.WHITE : DiscordTheme.TEXT_SECONDARY);
        btn.setBackground(active ? DiscordTheme.SELECTED : new Color(0, 0, 0, 0));
        btn.setContentAreaFilled(active);
        btn.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        return btn;
    }

    private void switchTab(String cardName, JButton activeBtn) {
        cards.show(cardsPanel, cardName);
        for (JButton b : new JButton[]{profileNavBtn, audioNavBtn, notifNavBtn}) {
            boolean act = (b == activeBtn);
            b.setContentAreaFilled(act);
            b.setFont(act ? DiscordTheme.bold(12f) : DiscordTheme.semibold(12f));
            b.setForeground(act ? Color.WHITE : DiscordTheme.TEXT_SECONDARY);
            b.setBackground(act ? DiscordTheme.SELECTED : new Color(0, 0, 0, 0));
            b.repaint();
        }
    }

    private JPanel buildProfileTab() {
        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 18, 24));

        // Top bar
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));

        JLabel h1 = new JLabel("Hồ sơ");
        h1.setFont(DiscordTheme.extrabold(20f));
        h1.setForeground(Color.WHITE);
        titles.add(h1);
        titles.add(Box.createVerticalStrut(2));

        JLabel sub = new JLabel("Thông tin hiển thị với bạn bè và các nhóm.");
        sub.setFont(DiscordTheme.font(12f));
        sub.setForeground(DiscordTheme.TEXT_SECONDARY);
        titles.add(sub);
        topBar.add(titles, BorderLayout.WEST);

        JButton closeBtn = new JButton(IconFactory.phosphor("x.svg", 18, DiscordTheme.TEXT_MUTED));
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dispose());
        topBar.add(closeBtn, BorderLayout.EAST);

        content.add(topBar, BorderLayout.NORTH);

        // Center split pane: Left Form (380px) + Right Preview (240px)
        JPanel center = new JPanel(new BorderLayout(24, 0));
        center.setOpaque(false);
        center.setBorder(BorderFactory.createEmptyBorder(16, 0, 16, 0));

        center.add(buildFormPane(), BorderLayout.CENTER);
        center.add(buildLivePreviewPane(), BorderLayout.EAST);
        content.add(center, BorderLayout.CENTER);

        // Bottom Action Bar
        content.add(buildBottomActionBar(), BorderLayout.SOUTH);
        return content;
    }

    private JPanel buildFormPane() {
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        // 1. Avatar row
        JLabel avLbl = new JLabel("ẢNH ĐẠI DIỆN");
        avLbl.setFont(DiscordTheme.extrabold(11f));
        avLbl.setForeground(DiscordTheme.TEXT_MUTED);
        form.add(avLbl);
        form.add(Box.createVerticalStrut(8));

        JPanel avRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        avRow.setOpaque(false);

        formAvatarHolder.setOpaque(false);
        updateFormAvatar();
        avRow.add(formAvatarHolder);

        JButton changeBtn = new JButton("Đổi ảnh");
        changeBtn.setFont(DiscordTheme.bold(11f));
        changeBtn.setForeground(Color.WHITE);
        changeBtn.setBackground(DiscordTheme.ACCENT);
        changeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        changeBtn.addActionListener(e -> pickCustomAvatar());
        avRow.add(changeBtn);

        JButton removeBtn = new JButton("Xóa ảnh");
        removeBtn.setFont(DiscordTheme.semibold(11f));
        removeBtn.setForeground(DiscordTheme.TEXT_MUTED);
        removeBtn.setContentAreaFilled(false);
        removeBtn.setBorderPainted(false);
        removeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        removeBtn.addActionListener(e -> {
            currentAvatarPath = null;
            updateFormAvatar();
            updateLivePreview();
        });
        avRow.add(removeBtn);

        form.add(avRow);
        form.add(Box.createVerticalStrut(14));

        // 2. Display Name
        JLabel dnLbl = new JLabel("TÊN HIỂN THỊ");
        dnLbl.setFont(DiscordTheme.extrabold(11f));
        dnLbl.setForeground(DiscordTheme.TEXT_MUTED);
        form.add(dnLbl);
        form.add(Box.createVerticalStrut(4));

        displayNameField.setPreferredSize(new Dimension(360, 36));
        displayNameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        DiscordTheme.styleField(displayNameField);
        form.add(displayNameField);
        form.add(Box.createVerticalStrut(12));

        // 3. Username
        JLabel unLbl = new JLabel("TÊN NGƯỜI DÙNG");
        unLbl.setFont(DiscordTheme.extrabold(11f));
        unLbl.setForeground(DiscordTheme.TEXT_MUTED);
        form.add(unLbl);
        form.add(Box.createVerticalStrut(4));

        usernameField.setPreferredSize(new Dimension(360, 36));
        usernameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        DiscordTheme.styleField(usernameField);
        form.add(usernameField);
        form.add(Box.createVerticalStrut(12));

        // 4. Status selector
        JLabel stLbl = new JLabel("TRẠNG THÁI HOẠT ĐỘNG");
        stLbl.setFont(DiscordTheme.extrabold(11f));
        stLbl.setForeground(DiscordTheme.TEXT_MUTED);
        form.add(stLbl);
        form.add(Box.createVerticalStrut(4));

        statusCombo.setFont(DiscordTheme.semibold(12f));
        statusCombo.setBackground(DiscordTheme.SUNKEN);
        statusCombo.setForeground(DiscordTheme.TEXT_PRIMARY);
        statusCombo.setPreferredSize(new Dimension(360, 34));
        statusCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        statusCombo.addActionListener(e -> {
            currentStatus = getSelectedStatus();
            updateLivePreview();
        });
        form.add(statusCombo);
        form.add(Box.createVerticalStrut(12));

        // 5. Bio
        JLabel bioLbl = new JLabel("GIỚI THIỆU VỀ BẠN");
        bioLbl.setFont(DiscordTheme.extrabold(11f));
        bioLbl.setForeground(DiscordTheme.TEXT_MUTED);
        form.add(bioLbl);
        form.add(Box.createVerticalStrut(4));

        bioArea.setLineWrap(true);
        bioArea.setWrapStyleWord(true);
        bioArea.setFont(DiscordTheme.font(12.5f));
        bioArea.setForeground(DiscordTheme.TEXT_PRIMARY);
        bioArea.setBackground(DiscordTheme.SUNKEN);
        bioArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JScrollPane bioScroll = new JScrollPane(bioArea);
        bioScroll.setPreferredSize(new Dimension(360, 60));
        bioScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        bioScroll.setBorder(BorderFactory.createLineBorder(new Color(0x3B, 0x3D, 0x44), 1));
        form.add(bioScroll);

        return form;
    }

    private void updateFormAvatar() {
        formAvatarHolder.removeAll();
        Color pres = user.statusColor();
        AvatarBadge badge = new AvatarBadge(user.initials(), user.id(), 54, pres, DiscordTheme.SIDEBAR, true, currentAvatarPath);
        formAvatarHolder.add(badge, BorderLayout.CENTER);
        formAvatarHolder.revalidate();
        formAvatarHolder.repaint();
    }

    private void pickCustomAvatar() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn ảnh đại diện");
        chooser.setFileFilter(new FileNameExtensionFilter("Ảnh (PNG, JPG, JPEG)", "png", "jpg", "jpeg"));
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            if (selected != null && selected.exists()) {
                currentAvatarPath = selected.getAbsolutePath();
                updateFormAvatar();
                updateLivePreview();
            }
        }
    }

    private JPanel buildLivePreviewPane() {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setPreferredSize(new Dimension(250, 0));

        JLabel title = new JLabel("BẢN XEM TRƯỚC");
        title.setFont(DiscordTheme.extrabold(11f));
        title.setForeground(DiscordTheme.TEXT_MUTED);
        wrapper.add(title);
        wrapper.add(Box.createVerticalStrut(8));

        // Discord user card
        RoundedPanel card = new RoundedPanel(10);
        card.setLayout(new BorderLayout());
        card.setBackground(new Color(0x19, 0x1A, 0x1E));
        card.setBorder(BorderFactory.createLineBorder(new Color(0x35, 0x37, 0x3C), 1));

        // Card banner
        RoundedPanel banner = new RoundedPanel(10);
        banner.setBackground(DiscordTheme.ACCENT);
        banner.setPreferredSize(new Dimension(250, 56));
        card.add(banner, BorderLayout.NORTH);

        // Card body
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(10, 14, 14, 14));

        previewAvatarHolder.setOpaque(false);
        previewAvatarHolder.setAlignmentX(LEFT_ALIGNMENT);
        body.add(previewAvatarHolder);
        body.add(Box.createVerticalStrut(8));

        previewName.setFont(DiscordTheme.extrabold(15f));
        previewName.setForeground(Color.WHITE);
        previewName.setAlignmentX(LEFT_ALIGNMENT);
        body.add(previewName);

        previewUsername.setFont(DiscordTheme.font(11f));
        previewUsername.setForeground(DiscordTheme.TEXT_SECONDARY);
        previewUsername.setAlignmentX(LEFT_ALIGNMENT);
        body.add(previewUsername);
        body.add(Box.createVerticalStrut(8));

        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        statusRow.setOpaque(false);
        statusRow.setAlignmentX(LEFT_ALIGNMENT);
        previewStatusDot.setFont(DiscordTheme.bold(10f));
        previewStatusText.setFont(DiscordTheme.semibold(11f));
        previewStatusText.setForeground(DiscordTheme.TEXT_SECONDARY);
        statusRow.add(previewStatusDot);
        statusRow.add(previewStatusText);
        body.add(statusRow);
        body.add(Box.createVerticalStrut(10));

        JPanel divider = new JPanel();
        divider.setBackground(new Color(0x2B, 0x2D, 0x32));
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        divider.setAlignmentX(LEFT_ALIGNMENT);
        body.add(divider);
        body.add(Box.createVerticalStrut(10));

        JLabel aboutHdr = new JLabel("THÔNG TIN CÁ NHÂN");
        aboutHdr.setFont(DiscordTheme.extrabold(9f));
        aboutHdr.setForeground(DiscordTheme.TEXT_MUTED);
        aboutHdr.setAlignmentX(LEFT_ALIGNMENT);
        body.add(aboutHdr);
        body.add(Box.createVerticalStrut(4));

        previewBio.setFont(DiscordTheme.font(11f));
        previewBio.setForeground(new Color(0xC5, 0xC6, 0xCD));
        previewBio.setAlignmentX(LEFT_ALIGNMENT);
        body.add(previewBio);

        card.add(body, BorderLayout.CENTER);
        wrapper.add(card);

        updateLivePreview();
        return wrapper;
    }

    private void updateLivePreview() {
        String name = displayNameField.getText().trim();
        if (name.isEmpty()) name = user.displayName();
        String uName = usernameField.getText().trim();
        if (uName.isEmpty()) uName = user.username();
        if (!uName.startsWith("@")) uName = "@" + uName;

        previewName.setText(name);
        previewUsername.setText(uName);

        UserProfile tempUser = new UserProfile(user.id(), name, user.email(), uName,
                bioArea.getText().trim(), currentStatus, currentAvatarPath);

        previewAvatarHolder.removeAll();
        AvatarBadge badge = new AvatarBadge(tempUser.initials(), tempUser.id(), 56,
                tempUser.statusColor(), new Color(0x19, 0x1A, 0x1E), true, currentAvatarPath);
        previewAvatarHolder.add(badge, BorderLayout.WEST);
        previewAvatarHolder.revalidate();
        previewAvatarHolder.repaint();

        previewStatusDot.setText("● ");
        previewStatusDot.setForeground(tempUser.statusColor());
        previewStatusText.setText(tempUser.statusLabel());

        String bio = bioArea.getText().trim();
        if (bio.isEmpty()) bio = "Chưa có giới thiệu.";
        previewBio.setText("<html><div style='width:200px;'>" + bio + "</div></html>");
    }

    private void setupLivePreviewSync() {
        DocumentListener syncer = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateLivePreview(); }
            @Override public void removeUpdate(DocumentEvent e) { updateLivePreview(); }
            @Override public void changedUpdate(DocumentEvent e) { updateLivePreview(); }
        };
        displayNameField.getDocument().addDocumentListener(syncer);
        usernameField.getDocument().addDocumentListener(syncer);
        bioArea.getDocument().addDocumentListener(syncer);
    }

    private JPanel buildBottomActionBar() {
        RoundedPanel bottomBar = new RoundedPanel(8);
        bottomBar.setBackground(new Color(0x19, 0x1A, 0x1E));
        bottomBar.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        bottomBar.setLayout(new BorderLayout());

        JLabel saveNotice = new JLabel("Có thay đổi chưa lưu · bản xem trước đang cập nhật trực tiếp");
        saveNotice.setFont(DiscordTheme.font(11f));
        saveNotice.setForeground(DiscordTheme.TEXT_MUTED);
        bottomBar.add(saveNotice, BorderLayout.WEST);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);

        JButton cancel = new JButton("Hủy");
        cancel.setFont(DiscordTheme.semibold(12f));
        cancel.setForeground(DiscordTheme.TEXT_SECONDARY);
        cancel.setContentAreaFilled(false);
        cancel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cancel.addActionListener(e -> dispose());
        btns.add(cancel);

        JButton save = new JButton("Lưu thay đổi");
        save.setFont(DiscordTheme.bold(12f));
        save.setForeground(Color.WHITE);
        save.setBackground(DiscordTheme.ACCENT);
        save.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        save.addActionListener(e -> {
            String newName = displayNameField.getText().trim();
            if (newName.isEmpty()) newName = user.displayName();
            String newUsername = usernameField.getText().trim();
            if (newUsername.isEmpty()) newUsername = user.username();
            if (!newUsername.startsWith("@")) newUsername = "@" + newUsername;
            String newBio = bioArea.getText().trim();

            UserProfile updated = user.withDetails(newName, newUsername, newBio, currentStatus, currentAvatarPath);
            if (onSave != null) {
                onSave.accept(updated);
            }
            dispose();
        });
        btns.add(save);

        bottomBar.add(btns, BorderLayout.EAST);
        return bottomBar;
    }

    private JPanel buildAudioVideoTab() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JLabel h1 = new JLabel("Âm thanh & video");
        h1.setFont(DiscordTheme.extrabold(20f));
        h1.setForeground(Color.WHITE);
        p.add(h1);
        p.add(Box.createVerticalStrut(4));

        JLabel sub = new JLabel("Cấu hình micro, tai nghe và kiểm tra kết nối giọng nói.");
        sub.setFont(DiscordTheme.font(12f));
        sub.setForeground(DiscordTheme.TEXT_SECONDARY);
        p.add(sub);
        p.add(Box.createVerticalStrut(20));

        p.add(makeSectionHeader("THIẾT BỊ ĐẦU VÀO (MICROPHONE)"));
        JComboBox<String> micCombo = new JComboBox<>(new String[]{
                "Mặc định hệ thống (Built-in Audio)", "Microphone ngoài (USB Audio Device)"
        });
        micCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        p.add(micCombo);
        p.add(Box.createVerticalStrut(10));

        p.add(makeSectionHeader("ÂM LƯỢNG ĐẦU VÀO"));
        JSlider micSlider = new JSlider(0, 100, 80);
        micSlider.setOpaque(false);
        p.add(micSlider);
        p.add(Box.createVerticalStrut(20));

        p.add(makeSectionHeader("THIẾT BỊ ĐẦU RA (LOA / TAI NGHE)"));
        JComboBox<String> speakerCombo = new JComboBox<>(new String[]{
                "Mặc định hệ thống (Built-in Speakers)", "Tai nghe (Headphone Jack / USB)"
        });
        speakerCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        p.add(speakerCombo);
        p.add(Box.createVerticalStrut(10));

        p.add(makeSectionHeader("ÂM LƯỢNG ĐẦU RA"));
        JSlider speakerSlider = new JSlider(0, 100, 100);
        speakerSlider.setOpaque(false);
        p.add(speakerSlider);
        p.add(Box.createVerticalStrut(20));

        JButton testMicBtn = new JButton("Kiểm tra micro", IconFactory.phosphor("microphone.svg", 16, Color.WHITE));
        testMicBtn.setBackground(DiscordTheme.ACCENT);
        testMicBtn.setForeground(Color.WHITE);
        testMicBtn.setFont(DiscordTheme.bold(12f));
        testMicBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        testMicBtn.addActionListener(e -> {
            testMicBtn.setText("Đang lắng nghe... (Hoạt động tốt!)");
            testMicBtn.setBackground(DiscordTheme.SUCCESS);
        });
        p.add(testMicBtn);

        p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel buildNotificationsTab() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JLabel h1 = new JLabel("Thông báo");
        h1.setFont(DiscordTheme.extrabold(20f));
        h1.setForeground(Color.WHITE);
        p.add(h1);
        p.add(Box.createVerticalStrut(4));

        JLabel sub = new JLabel("Tùy chọn nhận thông báo tin nhắn và cuộc gọi.");
        sub.setFont(DiscordTheme.font(12f));
        sub.setForeground(DiscordTheme.TEXT_SECONDARY);
        p.add(sub);
        p.add(Box.createVerticalStrut(20));

        p.add(makeToggleRow("Bật thông báo đẩy trên màn hình desktop", true));
        p.add(Box.createVerticalStrut(12));
        p.add(makeToggleRow("Âm thanh báo tin nhắn đến", true));
        p.add(Box.createVerticalStrut(12));
        p.add(makeToggleRow("Thông báo khi có người gắn thẻ @bạn", true));
        p.add(Box.createVerticalStrut(12));
        p.add(makeToggleRow("Thông báo khi có người bắt đầu kênh gặp", false));

        p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel makeSectionHeader(String title) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        JLabel l = new JLabel(title);
        l.setFont(DiscordTheme.extrabold(11f));
        l.setForeground(DiscordTheme.TEXT_MUTED);
        p.add(l, BorderLayout.WEST);
        return p;
    }

    private JPanel makeToggleRow(String title, boolean initial) {
        RoundedPanel row = new RoundedPanel(8);
        row.setLayout(new BorderLayout());
        row.setBackground(new Color(0x1F, 0x20, 0x24));
        row.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        JLabel l = new JLabel(title);
        l.setFont(DiscordTheme.semibold(12f));
        l.setForeground(DiscordTheme.TEXT_PRIMARY);
        row.add(l, BorderLayout.WEST);

        JButton toggle = new JButton(initial ? "BẬT" : "TẮT");
        toggle.setFont(DiscordTheme.bold(10f));
        toggle.setForeground(Color.WHITE);
        toggle.setBackground(initial ? DiscordTheme.SUCCESS : DiscordTheme.SUNKEN);
        toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggle.addActionListener(e -> {
            boolean isNowOn = toggle.getText().equals("TẮT");
            toggle.setText(isNowOn ? "BẬT" : "TẮT");
            toggle.setBackground(isNowOn ? DiscordTheme.SUCCESS : DiscordTheme.SUNKEN);
        });
        row.add(toggle, BorderLayout.EAST);
        return row;
    }
}

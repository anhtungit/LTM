package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.application.ChatService;
import vn.edu.ltm.chat.domain.Conversation;
import vn.edu.ltm.chat.theme.DiscordTheme;
import vn.edu.ltm.chat.theme.IconFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Modal dialog for creating a group chat (Canvas 03). */
public final class CreateGroupDialog extends JDialog {
    public record FriendCandidate(String id, String name, String initials, String role) {}

    private final ChatService chatService;
    private final Consumer<Conversation> onCreated;
    private final JTextField groupNameField = new JTextField();
    private final JTextField searchField = new JTextField();
    private final JLabel selectedSummary = new JLabel("0 đã chọn");
    private final JPanel listPanel = new JPanel();
    private final List<FriendCandidate> candidates = new ArrayList<>();
    private final Set<String> selectedIds = new HashSet<>();

    public CreateGroupDialog(JFrame parent, ChatService chatService, Consumer<Conversation> onCreated) {
        super(parent, "Tạo nhóm", true);
        this.chatService = chatService;
        this.onCreated = onCreated;

        initCandidates();

        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));

        RoundedPanel container = new RoundedPanel(14);
        container.setBackground(DiscordTheme.SIDEBAR);
        container.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x3B, 0x3D, 0x44), 1),
                BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));
        container.setLayout(new BorderLayout(0, 14));

        container.add(buildHeader(), BorderLayout.NORTH);
        container.add(buildBody(), BorderLayout.CENTER);
        container.add(buildActions(), BorderLayout.SOUTH);

        setContentPane(container);
        setSize(460, 600);
        setLocationRelativeTo(parent);
    }

    private void initCandidates() {
        candidates.add(new FriendCandidate("1", "Trần Ngọc Linh", "LN", "Đang hoạt động"));
        candidates.add(new FriendCandidate("2", "Nguyễn Minh Khoa", "MK", "Thiết kế sản phẩm"));
        candidates.add(new FriendCandidate("3", "Lê Bảo Anh", "BA", "Lần cuối hoạt động hôm nay"));
        candidates.add(new FriendCandidate("4", "Phạm Quang Huy", "QH", "Lập trình viên"));
        candidates.add(new FriendCandidate("5", "Đỗ Hoàng Nam", "HN", "Đang hoạt động"));

        // Default 3 selected as in Canvas 03
        selectedIds.add("1");
        selectedIds.add("2");
        selectedIds.add("3");
        updateSummary();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Tạo nhóm");
        title.setFont(DiscordTheme.bold(18f));
        title.setForeground(Color.WHITE);
        left.add(title);

        JLabel subtitle = new JLabel("Chọn người bạn muốn trò chuyện cùng.");
        subtitle.setFont(DiscordTheme.font(12f));
        subtitle.setForeground(DiscordTheme.TEXT_SECONDARY);
        left.add(subtitle);

        header.add(left, BorderLayout.CENTER);

        JButton closeBtn = new JButton(IconFactory.phosphor("x.svg", 16, DiscordTheme.TEXT_MUTED));
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dispose());
        header.add(closeBtn, BorderLayout.EAST);

        return header;
    }

    private JPanel buildBody() {
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        // Group name field
        JLabel nameLabel = new JLabel("<html>Tên nhóm <span style='color:#898C94'>(không bắt buộc)</span></html>");
        nameLabel.setFont(DiscordTheme.semibold(12f));
        nameLabel.setForeground(DiscordTheme.TEXT_PRIMARY);
        nameLabel.setAlignmentX(LEFT_ALIGNMENT);
        body.add(nameLabel);
        body.add(Box.createVerticalStrut(6));

        groupNameField.setPreferredSize(new Dimension(420, 38));
        groupNameField.setMaximumSize(new Dimension(Short.MAX_VALUE, 38));
        groupNameField.putClientProperty("JTextField.placeholderText", "Ví dụ: Đội dự án, Hội bạn thân...");
        DiscordTheme.styleField(groupNameField);
        groupNameField.setAlignmentX(LEFT_ALIGNMENT);
        body.add(groupNameField);
        body.add(Box.createVerticalStrut(14));

        // Friends label + search
        JPanel friendsHeader = new JPanel(new BorderLayout());
        friendsHeader.setOpaque(false);
        friendsHeader.setAlignmentX(LEFT_ALIGNMENT);

        JLabel memberLabel = new JLabel("Thêm thành viên");
        memberLabel.setFont(DiscordTheme.semibold(12f));
        memberLabel.setForeground(DiscordTheme.TEXT_PRIMARY);
        friendsHeader.add(memberLabel, BorderLayout.WEST);

        JLabel maxLabel = new JLabel("Tối đa 10 người");
        maxLabel.setFont(DiscordTheme.font(11f));
        maxLabel.setForeground(DiscordTheme.TEXT_MUTED);
        friendsHeader.add(maxLabel, BorderLayout.EAST);

        body.add(friendsHeader);
        body.add(Box.createVerticalStrut(6));

        searchField.setPreferredSize(new Dimension(420, 36));
        searchField.setMaximumSize(new Dimension(Short.MAX_VALUE, 36));
        searchField.putClientProperty("JTextField.placeholderText", "Tìm hoặc lọc bạn bè...");
        DiscordTheme.styleField(searchField);
        searchField.setAlignmentX(LEFT_ALIGNMENT);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refreshCandidates(); }
            @Override public void removeUpdate(DocumentEvent e) { refreshCandidates(); }
            @Override public void changedUpdate(DocumentEvent e) { refreshCandidates(); }
        });
        body.add(searchField);
        body.add(Box.createVerticalStrut(8));

        // Selected summary
        selectedSummary.setFont(DiscordTheme.semibold(11f));
        selectedSummary.setForeground(DiscordTheme.ACCENT_SOFT);
        selectedSummary.setAlignmentX(LEFT_ALIGNMENT);
        body.add(selectedSummary);
        body.add(Box.createVerticalStrut(8));

        // List
        listPanel.setOpaque(false);
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        refreshCandidates();

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0x27, 0x29, 0x2D), 1));
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        scroll.setAlignmentX(LEFT_ALIGNMENT);
        body.add(scroll);

        return body;
    }

    private void refreshCandidates() {
        listPanel.removeAll();
        String query = searchField.getText().trim().toLowerCase();
        for (FriendCandidate f : candidates) {
            if (!query.isEmpty() && !f.name().toLowerCase().contains(query) && !f.role().toLowerCase().contains(query)) {
                continue;
            }
            listPanel.add(buildCandidateRow(f));
            listPanel.add(Box.createVerticalStrut(4));
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    private JPanel buildCandidateRow(FriendCandidate f) {
        RoundedPanel row = new RoundedPanel(8);
        boolean selected = selectedIds.contains(f.id());
        row.setBackground(selected ? new Color(0x2E, 0x30, 0x36) : DiscordTheme.SIDEBAR);
        row.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        row.setLayout(new BorderLayout(8, 0));

        AvatarBadge avatar = new AvatarBadge(f.initials(), f.name(), 32);
        row.add(avatar, BorderLayout.WEST);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

        JLabel name = new JLabel(f.name());
        name.setFont(DiscordTheme.bold(12f));
        name.setForeground(Color.WHITE);
        info.add(name);

        JLabel role = new JLabel(f.role());
        role.setFont(DiscordTheme.font(10f));
        role.setForeground(DiscordTheme.TEXT_MUTED);
        info.add(role);

        row.add(info, BorderLayout.CENTER);

        JCheckBox check = new JCheckBox();
        check.setOpaque(false);
        check.setSelected(selected);
        check.addActionListener(e -> {
            if (check.isSelected()) {
                selectedIds.add(f.id());
            } else {
                selectedIds.remove(f.id());
            }
            updateSummary();
            refreshCandidates();
        });
        row.add(check, BorderLayout.EAST);

        return row;
    }

    private void updateSummary() {
        int count = selectedIds.size();
        List<String> names = new ArrayList<>();
        for (FriendCandidate c : candidates) {
            if (selectedIds.contains(c.id())) {
                names.add(c.name());
            }
        }
        if (names.isEmpty()) {
            selectedSummary.setText("0 đã chọn");
        } else {
            selectedSummary.setText(count + " đã chọn · " + String.join(", ", names));
        }
    }

    private JPanel buildActions() {
        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);

        JLabel hint = new JLabel("Nhóm sẽ xuất hiện trong danh sách tin nhắn của bạn.");
        hint.setFont(DiscordTheme.font(10f));
        hint.setForeground(DiscordTheme.TEXT_MUTED);
        actions.add(hint, BorderLayout.WEST);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);

        JButton cancelBtn = new JButton("Hủy");
        cancelBtn.setFont(DiscordTheme.semibold(12f));
        cancelBtn.setForeground(DiscordTheme.TEXT_SECONDARY);
        cancelBtn.setContentAreaFilled(false);
        cancelBtn.setBorderPainted(false);
        cancelBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cancelBtn.addActionListener(e -> dispose());
        btns.add(cancelBtn);

        JButton createBtn = new JButton("Tạo nhóm");
        createBtn.setFont(DiscordTheme.bold(12f));
        createBtn.setForeground(Color.WHITE);
        createBtn.setBackground(DiscordTheme.ACCENT);
        createBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        createBtn.addActionListener(e -> {
            String name = groupNameField.getText().trim();
            if (name.isEmpty()) {
                name = "Nhóm bạn thân";
            }
            List<String> memberNames = new ArrayList<>();
            for (FriendCandidate c : candidates) {
                if (selectedIds.contains(c.id())) {
                    memberNames.add(c.name());
                }
            }
            Conversation created = chatService.createGroup(name, memberNames);
            if (onCreated != null) {
                onCreated.accept(created);
            }
            dispose();
        });
        btns.add(createBtn);

        actions.add(btns, BorderLayout.EAST);
        return actions;
    }
}


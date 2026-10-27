package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.application.ChatService;
import vn.edu.ltm.chat.domain.ChatMessage;
import vn.edu.ltm.chat.domain.Conversation;
import vn.edu.ltm.chat.domain.UserProfile;
import vn.edu.ltm.chat.theme.DiscordTheme;
import vn.edu.ltm.chat.theme.IconFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Handles creation and presentation of workspace modal dialogs and file pickers.
 */
final class WorkspaceDialogs {
    private WorkspaceDialogs() {
    }

    static void openInboxDialog(Component parent, Runnable onNavigateChat) {
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(parent);
        InboxDialog dialog = new InboxDialog(frame, notif -> {
            if (onNavigateChat != null) {
                onNavigateChat.run();
            }
        });
        dialog.setVisible(true);
    }

    static void openCreateGroupDialog(Component parent, ChatService chatService, Consumer<Conversation> onCreated) {
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(parent);
        CreateGroupDialog dialog = new CreateGroupDialog(frame, chatService, createdGroup -> {
            if (onCreated != null) {
                onCreated.accept(createdGroup);
            }
        });
        dialog.setVisible(true);
    }

    static void openProfileDialog(Component parent, UserProfile user, ChatService chatService, Consumer<UserProfile> onUpdated) {
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(parent);
        ProfileDialog dialog = new ProfileDialog(frame, user, updatedUser -> {
            UserProfile saved = chatService.updateProfile(updatedUser);
            if (onUpdated != null) {
                onUpdated.accept(saved);
            }
        });
        dialog.setVisible(true);
    }

    static void openViewOnlyProfileDialog(Component parent, UserProfile targetUser) {
        if (targetUser == null) return;
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(frame, "Hồ sơ người dùng", true);
        dialog.setUndecorated(true);
        dialog.setBackground(new Color(0, 0, 0, 0));

        int width = 340;
        RoundedPanel root = new RoundedPanel(12);
        root.setBackground(new Color(0x1E, 0x1F, 0x22));
        root.setLayout(new BorderLayout());
        root.setBorder(BorderFactory.createLineBorder(new Color(0x3B, 0x3D, 0x44), 1));

        JPanel banner = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color c = ChatInfoPanel.getBannerColor(targetUser.id());
                GradientPaint gp = new GradientPaint(0, 0, c, 0, 80, c.darker());
                g.setPaint(gp);
                g.fillRect(0, 0, getWidth(), 80);
                g.setColor(new Color(0x1E, 0x1F, 0x22));
                g.fillOval(16, 36, 80, 80);
                g.dispose();
            }
        };
        banner.setOpaque(false);
        banner.setPreferredSize(new Dimension(width, 125));

        JButton closeBtn = new JButton(IconFactory.phosphor("x.svg", 14, Color.WHITE));
        closeBtn.setBounds(width - 36, 10, 24, 24);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dialog.dispose());
        banner.add(closeBtn);

        AvatarBadge avatar = new AvatarBadge(
                targetUser.initials(),
                targetUser.id(),
                72,
                targetUser.online() ? DiscordTheme.SUCCESS : null,
                new Color(0x1E, 0x1F, 0x22),
                true
        );
        avatar.setBounds(20, 40, 72, 72);
        banner.add(avatar);
        root.add(banner, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(6, 18, 18, 18));

        JLabel nameLabel = new JLabel(targetUser.displayName());
        nameLabel.setFont(DiscordTheme.extrabold(16f));
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setAlignmentX(0.0f);
        body.add(nameLabel);

        JLabel handleLabel = new JLabel(targetUser.username());
        handleLabel.setFont(DiscordTheme.font(12f));
        handleLabel.setForeground(DiscordTheme.TEXT_MUTED);
        handleLabel.setAlignmentX(0.0f);
        body.add(handleLabel);

        body.add(Box.createVerticalStrut(10));

        JPanel divider1 = new JPanel();
        divider1.setBackground(new Color(0x35, 0x36, 0x3C));
        divider1.setMaximumSize(new Dimension(width, 1));
        divider1.setAlignmentX(0.0f);
        body.add(divider1);

        body.add(Box.createVerticalStrut(10));

        JLabel aboutTitle = new JLabel("VỀ TÔI");
        aboutTitle.setFont(DiscordTheme.extrabold(10f));
        aboutTitle.setForeground(new Color(0xB5, 0xBA, 0xC1));
        aboutTitle.setAlignmentX(0.0f);
        body.add(aboutTitle);

        body.add(Box.createVerticalStrut(4));

        String bioText = (targetUser.bio() != null && !targetUser.bio().isBlank())
                ? targetUser.bio()
                : "Thích trò chuyện, nghe nhạc và lập trình cùng bạn bè. ☁️";
        WrappedTextArea bioArea = new WrappedTextArea(bioText, width - 36);
        bioArea.setFont(DiscordTheme.font(12f));
        bioArea.setForeground(new Color(0xDB, 0xDE, 0xE1));
        bioArea.setAlignmentX(0.0f);
        body.add(bioArea);

        body.add(Box.createVerticalStrut(12));

        JLabel memberSinceTitle = new JLabel("THÀNH VIÊN DISCORD TỪ");
        memberSinceTitle.setFont(DiscordTheme.extrabold(10f));
        memberSinceTitle.setForeground(new Color(0xB5, 0xBA, 0xC1));
        memberSinceTitle.setAlignmentX(0.0f);
        body.add(memberSinceTitle);

        body.add(Box.createVerticalStrut(4));

        JLabel memberDate = new JLabel("15 thg 4, 2024");
        memberDate.setFont(DiscordTheme.font(12f));
        memberDate.setForeground(new Color(0xDB, 0xDE, 0xE1));
        memberDate.setAlignmentX(0.0f);
        body.add(memberDate);

        body.add(Box.createVerticalStrut(16));

        JButton dismissBtn = new JButton("Đóng");
        dismissBtn.setFont(DiscordTheme.semibold(12f));
        dismissBtn.setForeground(Color.WHITE);
        dismissBtn.setBackground(new Color(0x35, 0x37, 0x3C));
        dismissBtn.setFocusPainted(false);
        dismissBtn.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
        dismissBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        dismissBtn.setAlignmentX(0.0f);
        dismissBtn.setMaximumSize(new Dimension(width - 36, 32));
        dismissBtn.addActionListener(e -> dialog.dispose());
        body.add(dismissBtn);

        root.add(body, BorderLayout.CENTER);

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    static void openStartDmDialog(Component parent, UserProfile currentUser, ChatService chatService,
                                  List<Conversation> currentConversations, Consumer<Conversation> onSelected) {
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
                    if (u != null && currentUser != null && !u.id().equals(currentUser.id())) {
                        candidates.add(u);
                    }
                }
            }
            if (candidates.isEmpty()) {
                JOptionPane.showMessageDialog(parent,
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
                    parent,
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
                                    for (int j = 0; j < currentConversations.size(); j++) {
                                        if (currentConversations.get(j).id().equals(dm.id())) {
                                            currentConversations.set(j, dm);
                                            exists = true;
                                            break;
                                        }
                                    }
                                    if (!exists) {
                                        currentConversations.add(dm);
                                    }
                                    if (onSelected != null) {
                                        onSelected.accept(dm);
                                    }
                                }
                            }));
                    break;
                }
            }
        }));
    }

    static void downloadAttachmentToFile(Component parent, ChatService chatService, ChatMessage message,
                                         Consumer<String> showSuccess, Consumer<String> showError) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Lưu tệp đính kèm");
        chooser.setSelectedFile(new File(message.attachmentName()));
        int res = chooser.showSaveDialog(parent);
        if (res == JFileChooser.APPROVE_OPTION) {
            File target = chooser.getSelectedFile();
            try {
                byte[] bytes = chatService.downloadAttachment(message.id(), message.attachmentName());
                if (bytes == null || bytes.length == 0) {
                    bytes = ("Nội dung tệp đính kèm: " + message.attachmentName()).getBytes(StandardCharsets.UTF_8);
                }
                Files.write(target.toPath(), bytes);
                if (showSuccess != null) {
                    showSuccess.accept("Đã lưu tệp tại: " + target.getAbsolutePath());
                }
            } catch (Exception ex) {
                if (showError != null) {
                    showError.accept("Không thể lưu tệp: " + ex.getMessage());
                }
            }
        }
    }
}


package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.application.ChatService;
import vn.edu.ltm.chat.domain.ChatMessage;
import vn.edu.ltm.chat.domain.Conversation;
import vn.edu.ltm.chat.domain.UserProfile;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
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


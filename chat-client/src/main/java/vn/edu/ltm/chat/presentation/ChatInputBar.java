package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.domain.ChatMessage;
import vn.edu.ltm.chat.theme.DiscordTheme;
import vn.edu.ltm.chat.theme.IconFactory;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;

/**
 * Dedicated chat input bar component styled like Discord's bottom composer pill.
 * Encapsulates the text composer, dynamic placeholder, file attachments, emoji/sticker picker,
 * and smart resizing.
 */
public final class ChatInputBar extends JPanel {

    public interface ChatInputListener {
        void onSendMessage(String content);
        void onSendAttachment(String filePath, String fileName, long fileSize, boolean failed);
        void onSendSticker(String stickerName);
    }

    private final RichComposerPane composer;
    private final JScrollPane composerScroll;
    private final JLabel composerHint = new JLabel("Bản nháp riêng cho cuộc trò chuyện này");
    private final JButton sendButton = new JButton();
    private final JPanel replyBanner = new JPanel(new BorderLayout(8, 0));
    private final JLabel replyToLabel = new JLabel();
    private ChatMessage replyingMessage = null;
    private ChatInputListener listener;

    public ChatInputBar() {
        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(4, 24, 12, 24));

        composer = new RichComposerPane(this::submitMessage);

        RoundedPanel shell = new RoundedPanel(10);
        shell.setBackground(DiscordTheme.COMPOSER);
        shell.setLayout(new BorderLayout());

        // Reply banner bar
        replyBanner.setOpaque(true);
        replyBanner.setBackground(new Color(0x2B, 0x2D, 0x31));
        replyBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0x38, 0x3A, 0x40)),
                BorderFactory.createEmptyBorder(5, 12, 5, 8)
        ));
        replyBanner.setVisible(false);

        JPanel leftReply = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        leftReply.setOpaque(false);
        leftReply.add(new JLabel(IconFactory.phosphor("arrow-u-up-left.svg", 13, DiscordTheme.TEXT_MUTED)));
        replyToLabel.setFont(DiscordTheme.font(11.5f));
        leftReply.add(replyToLabel);
        replyBanner.add(leftReply, BorderLayout.CENTER);

        JButton cancelReplyBtn = createIconButton("x.svg", "Hủy trả lời", 13, DiscordTheme.TEXT_MUTED);
        cancelReplyBtn.addActionListener(e -> cancelReply());
        replyBanner.add(cancelReplyBtn, BorderLayout.EAST);

        shell.add(replyBanner, BorderLayout.NORTH);

        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setOpaque(false);
        inputRow.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 8));

        // Circular attach button
        JButton attach = createIconButton("plus-circle.svg", "Đính kèm tệp", 22, DiscordTheme.TEXT_SECONDARY);
        attach.addActionListener(e -> showAttachMenu(attach));
        inputRow.add(attach, BorderLayout.WEST);

        composer.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { adjustComposerHeight(); }
            @Override public void removeUpdate(DocumentEvent event) { adjustComposerHeight(); }
            @Override public void changedUpdate(DocumentEvent event) { adjustComposerHeight(); }
        });
        composer.getAccessibleContext().setAccessibleName("Soạn tin nhắn");

        composerScroll = new JScrollPane(composer);
        composerScroll.setBorder(null);
        composerScroll.setOpaque(false);
        composerScroll.getViewport().setOpaque(false);
        composerScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        composerScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        composerScroll.setPreferredSize(new Dimension(0, 32));
        inputRow.add(composerScroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.setOpaque(false);

        // Sticker popup button (Discord style)
        JButton stickerBtn = createIconButton("sticker.svg", "Hình dán (Sticker)", 20, DiscordTheme.TEXT_SECONDARY);
        stickerBtn.addActionListener(e -> {
            if (stickerBtn.isShowing()) {
                EmojiPickerPopup popup = new EmojiPickerPopup(
                        selectedEmoji -> {
                            composer.insertEmoji(selectedEmoji);
                            adjustComposerHeight();
                        },
                        selectedSticker -> {
                            if (listener != null) listener.onSendSticker(selectedSticker);
                        }
                );
                popup.showAbove(stickerBtn);
            }
        });
        actions.add(stickerBtn);

        // Emoji popup button (Discord style)
        JButton emojiBtn = createIconButton("smiley.svg", "Chọn biểu cảm Emoji", 20, DiscordTheme.TEXT_SECONDARY);
        emojiBtn.addActionListener(e -> {
            if (emojiBtn.isShowing()) {
                EmojiPickerPopup popup = new EmojiPickerPopup(
                        selectedEmoji -> {
                            composer.insertEmoji(selectedEmoji);
                            adjustComposerHeight();
                        },
                        selectedSticker -> {
                            if (listener != null) listener.onSendSticker(selectedSticker);
                        }
                );
                popup.showAbove(emojiBtn);
            }
        });
        actions.add(emojiBtn);

        // Send button
        sendButton.setIcon(IconFactory.phosphor("paper-plane-tilt.svg", 16, Color.WHITE));
        sendButton.setToolTipText("Gửi tin nhắn (Enter)");
        sendButton.setPreferredSize(new Dimension(30, 30));
        sendButton.setFocusPainted(false);
        DiscordTheme.stylePrimaryButton(sendButton);
        sendButton.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        sendButton.addActionListener(event -> submitMessage());
        actions.add(sendButton);

        inputRow.add(actions, BorderLayout.EAST);
        shell.add(inputRow, BorderLayout.CENTER);

        JPanel container = new JPanel(new BorderLayout(0, 3));
        container.setOpaque(false);
        container.add(shell, BorderLayout.CENTER);

        composerHint.setFont(DiscordTheme.font(10f));
        composerHint.setForeground(DiscordTheme.TEXT_MUTED);
        composerHint.setBorder(BorderFactory.createEmptyBorder(2, 6, 0, 0));
        container.add(composerHint, BorderLayout.SOUTH);

        JPanel row = new JPanel(new ColumnLayout(1000, 0));
        row.setOpaque(false);
        row.add(container);
        add(row, BorderLayout.CENTER);
    }

    public void setListener(ChatInputListener listener) {
        this.listener = listener;
    }

    public void setTarget(String targetName, boolean isDm) {
        if (targetName == null || targetName.isBlank()) {
            composer.setPlaceholder("Nhập tin nhắn...");
        } else if (isDm) {
            composer.setPlaceholder("Message @" + targetName);
        } else {
            composer.setPlaceholder("Message #" + targetName);
        }
    }

    public void setPlaceholder(String placeholder) {
        composer.setPlaceholder(placeholder);
    }

    public String getMessageText() {
        return composer.getMessageText();
    }

    public void setMessageText(String text) {
        composer.setMessageText(text);
        adjustComposerHeight();
    }

    public void insertEmoji(String emoji) {
        composer.insertEmoji(emoji);
        adjustComposerHeight();
    }

    public void clear() {
        composer.clear();
        adjustComposerHeight();
    }

    @Override
    public boolean requestFocusInWindow() {
        return composer.requestFocusInWindow();
    }

    public void setHint(String text, Color color) {
        composerHint.setText(text);
        composerHint.setForeground(color);
    }

    public void resetHint() {
        composerHint.setText("Bản nháp riêng cho cuộc trò chuyện này");
        composerHint.setForeground(DiscordTheme.TEXT_MUTED);
    }

    public void setReplyingTo(ChatMessage message) {
        if (message == null) {
            cancelReply();
            return;
        }
        this.replyingMessage = message;
        replyToLabel.setText("<html><font color='#949ba4'>Đang trả lời </font><b><font color='#ffffff'>@"
                + (message.senderName() != null ? message.senderName() : "người dùng") + "</font></b></html>");
        replyBanner.setVisible(true);
        revalidate();
        repaint();
        requestFocusInWindow();
    }

    public void cancelReply() {
        this.replyingMessage = null;
        replyBanner.setVisible(false);
        revalidate();
        repaint();
    }

    private void submitMessage() {
        String content = composer.getMessageText();
        if (content == null || content.trim().isEmpty()) {
            composer.requestFocusInWindow();
            return;
        }
        ChatMessage target = this.replyingMessage;
        clear();
        cancelReply();
        composer.requestFocusInWindow();
        if (listener != null) {
            if (target != null) {
                String quote = target.content() != null ? target.content().replace("\n", " ").trim() : "";
                if (quote.length() > 65) quote = quote.substring(0, 62) + "...";
                String finalContent = "> @" + target.senderName() + ": " + quote + "\n" + content.trim();
                listener.onSendMessage(finalContent);
            } else {
                listener.onSendMessage(content.trim());
            }
        }
    }

    private void adjustComposerHeight() {
        int lines = Math.max(1, composer.getMessageText().split("\n", -1).length);
        int height = Math.min(120, Math.max(32, lines * 22 + 10));
        composerScroll.setPreferredSize(new Dimension(0, height));
        composerScroll.revalidate();
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
                if (listener != null) {
                    listener.onSendAttachment(file.getAbsolutePath(), file.getName(), file.length(), false);
                }
            }
        });
        menu.add(chooseFile);

        menu.addSeparator();

        JMenuItem sampleFile = new JMenuItem("Gửi tệp mẫu thành công (PDF 2.4 MB)");
        sampleFile.addActionListener(e -> {
            if (listener != null) {
                listener.onSendAttachment("Tong_hop_bai_giang.pdf", "Tong_hop_bai_giang.pdf", 2_400_000L, false);
            }
        });
        menu.add(sampleFile);

        JMenuItem failFile = new JMenuItem("Gửi tệp mẫu thất bại (Màn 09 demo)");
        failFile.addActionListener(e -> {
            if (listener != null) {
                listener.onSendAttachment("architecture-chat-flow-v2.png", "architecture-chat-flow-v2.png", 2_400_000L, true);
            }
        });
        menu.add(failFile);

        if (source != null && source.isShowing()) {
            try {
                menu.show(source, 0, source.getHeight());
            } catch (Exception ignored) {}
        }
    }

    private static JButton createIconButton(String iconName, String tooltip, int iconSize, Color iconColor) {
        JButton button = new JButton(IconFactory.phosphor(iconName, iconSize, iconColor));
        button.setToolTipText(tooltip);
        button.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }
}


package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.theme.DiscordTheme;
import vn.edu.ltm.chat.theme.IconFactory;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
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
    private ChatInputListener listener;

    public ChatInputBar() {
        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(4, 24, 12, 24));

        composer = new RichComposerPane(this::submitMessage);

        RoundedPanel shell = new RoundedPanel(10);
        shell.setBackground(DiscordTheme.COMPOSER);
        shell.setLayout(new BorderLayout());

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

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        actions.setOpaque(false);

        // Emoji & Sticker popup button
        JButton emojiBtn = createIconButton("smiley.svg", "Emoji & Sticker", 19, DiscordTheme.TEXT_SECONDARY);
        emojiBtn.addActionListener(e -> {
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
        });
        actions.add(emojiBtn);

        // Send button
        sendButton.setIcon(IconFactory.phosphor("paper-plane-tilt.svg", 17, Color.WHITE));
        sendButton.setToolTipText("Gửi tin nhắn");
        sendButton.setPreferredSize(new Dimension(32, 32));
        sendButton.setFocusPainted(true);
        DiscordTheme.stylePrimaryButton(sendButton);
        sendButton.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        sendButton.addActionListener(event -> submitMessage());
        actions.add(sendButton);

        inputRow.add(actions, BorderLayout.EAST);
        shell.add(inputRow, BorderLayout.NORTH);

        // Footer hint bar
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0x3A, 0x3C, 0x42)),
                BorderFactory.createEmptyBorder(5, 10, 6, 10)));

        composerHint.setFont(DiscordTheme.font(10f));
        composerHint.setForeground(DiscordTheme.TEXT_MUTED);
        composerHint.setMinimumSize(new Dimension(0, 0));
        footer.add(composerHint, BorderLayout.CENTER);

        JLabel keys = new JLabel("Enter để gửi · Shift + Enter xuống dòng");
        keys.setFont(DiscordTheme.font(10f));
        keys.setForeground(DiscordTheme.TEXT_MUTED);
        keys.setHorizontalAlignment(SwingConstants.RIGHT);
        keys.setMinimumSize(new Dimension(0, 0));
        footer.add(keys, BorderLayout.EAST);

        shell.add(footer, BorderLayout.SOUTH);

        JPanel row = new JPanel(new ColumnLayout(1000, 0));
        row.setOpaque(false);
        row.add(shell);
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

    private void submitMessage() {
        String content = composer.getMessageText();
        if (content == null || content.trim().isEmpty()) {
            composer.requestFocusInWindow();
            return;
        }
        clear();
        composer.requestFocusInWindow();
        if (listener != null) {
            listener.onSendMessage(content);
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

        menu.show(source, 0, source.getHeight());
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

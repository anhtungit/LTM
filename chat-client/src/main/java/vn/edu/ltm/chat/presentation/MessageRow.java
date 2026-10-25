package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.domain.ChatMessage;
import vn.edu.ltm.chat.theme.DiscordTheme;
import vn.edu.ltm.chat.theme.IconFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;

/** One message row: avatar, author, text, inline editor, file attachment card, and action buttons. */
final class MessageRow extends JPanel {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int AVATAR_SIZE = 34;
    private static final int AVATAR_GAP = 12;

    public interface MessageActionListener {
        void onEdit(ChatMessage message, String newContent);
        void onRecall(ChatMessage message);
        void onDelete(ChatMessage message);
        void onResend(ChatMessage message);
        default void onDownload(ChatMessage message) {}
    }

    private final ChatMessage message;
    private final MessageActionListener listener;
    private final JPanel body = new JPanel(new BorderLayout());
    private final JPanel contentHolder = new JPanel(new BorderLayout(0, 6));
    private final JPanel actionToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
    private WrappedTextArea normalText;
    private boolean isEditing = false;
    private int currentWrapWidth;

    MessageRow(ChatMessage message, int wrapWidth, boolean beginsSenderGroup, MessageActionListener listener) {
        this.message = message;
        this.listener = listener;
        this.currentWrapWidth = wrapWidth;

        setLayout(new BorderLayout(AVATAR_GAP, 0));
        setOpaque(false);
        setAlignmentX(CENTER_ALIGNMENT);
        setBorder(BorderFactory.createEmptyBorder(beginsSenderGroup ? 7 : 2, 0, 4, 0));

        boolean isMe = message.fromCurrentUser();
        if (beginsSenderGroup) {
            AvatarBadge avatar = new AvatarBadge(message.senderInitials(), message.senderId(), AVATAR_SIZE);
            avatar.setToolTipText(isMe ? "Tin nhắn của bạn" : "Tin nhắn từ " + message.senderName());
            add(avatar, isMe ? BorderLayout.EAST : BorderLayout.WEST);
        } else {
            add(Box.createRigidArea(new Dimension(AVATAR_SIZE, 1)), isMe ? BorderLayout.EAST : BorderLayout.WEST);
        }

        body.setOpaque(false);
        if (beginsSenderGroup) {
            body.add(buildAuthorRow(message), BorderLayout.NORTH);
        }

        contentHolder.setOpaque(false);
        buildNormalContent();
        body.add(contentHolder, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!isEditing && message.fromCurrentUser() && !message.recalled()) {
                    actionToolbar.setVisible(true);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!contains(e.getPoint())) {
                    actionToolbar.setVisible(false);
                }
            }
        });
    }

    private JPanel buildAuthorRow(ChatMessage message) {
        JPanel authorRow = new JPanel(new BorderLayout(8, 0));
        authorRow.setOpaque(false);
        boolean isMe = message.fromCurrentUser();

        actionToolbar.setOpaque(false);
        actionToolbar.setVisible(false);

        if (isMe && !message.recalled()) {
            JButton editBtn = createToolbarButton("note-pencil.svg", "Chỉnh sửa tin nhắn", () -> startEdit());
            JButton recallBtn = createToolbarButton("x.svg", "Thu hồi tin nhắn", () -> {
                if (listener != null) listener.onRecall(message);
            });
            JButton deleteBtn = createToolbarButton("trash.svg", "Xóa tin nhắn", () -> {
                if (listener != null) listener.onDelete(message);
            });
            actionToolbar.add(editBtn);
            actionToolbar.add(recallBtn);
            actionToolbar.add(deleteBtn);
        }

        if (isMe) {
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            right.setOpaque(false);

            String timeStr = TIME_FORMAT.format(message.sentAt());
            if (message.edited()) {
                timeStr += " (đã sửa)";
            }
            JLabel time = new JLabel(timeStr);
            time.setFont(DiscordTheme.font(10f));
            time.setForeground(DiscordTheme.TEXT_MUTED);
            right.add(time);

            JLabel author = new JLabel("Bạn");
            author.setFont(DiscordTheme.bold(13f));
            author.setForeground(new Color(0x58, 0x65, 0xF2));
            right.add(author);

            authorRow.add(right, BorderLayout.EAST);
            authorRow.add(actionToolbar, BorderLayout.WEST);
        } else {
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            left.setOpaque(false);

            JLabel author = new JLabel(message.senderName());
            author.setFont(DiscordTheme.bold(13f));
            author.setForeground(Color.WHITE);
            left.add(author);

            String timeStr = TIME_FORMAT.format(message.sentAt());
            if (message.edited()) {
                timeStr += " (đã sửa)";
            }
            JLabel time = new JLabel(timeStr);
            time.setFont(DiscordTheme.font(10f));
            time.setForeground(DiscordTheme.TEXT_MUTED);
            left.add(time);

            authorRow.add(left, BorderLayout.WEST);
            authorRow.add(actionToolbar, BorderLayout.EAST);
        }

        return authorRow;
    }

    private JButton createToolbarButton(String iconName, String tooltip, Runnable action) {
        JButton btn = new JButton(IconFactory.phosphor(iconName, 14, DiscordTheme.TEXT_SECONDARY));
        btn.setToolTipText(tooltip);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusable(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMargin(new Insets(0, 4, 0, 4));
        btn.addActionListener(e -> action.run());
        return btn;
    }

    private void buildNormalContent() {
        contentHolder.removeAll();
        boolean isMe = message.fromCurrentUser();
        int flowAlign = isMe ? FlowLayout.RIGHT : FlowLayout.LEFT;

        if (message.recalled()) {
            RoundedPanel bubble = new RoundedPanel(12);
            bubble.setBackground(new Color(0x2B, 0x2D, 0x31));
            bubble.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
            JLabel recalledLabel = new JLabel("Tin nhắn đã được thu hồi");
            recalledLabel.setFont(DiscordTheme.font(12f));
            recalledLabel.setForeground(DiscordTheme.TEXT_MUTED);
            bubble.add(recalledLabel);

            JPanel holder = new JPanel(new FlowLayout(flowAlign, 0, 0));
            holder.setOpaque(false);
            holder.add(bubble);
            contentHolder.add(holder, BorderLayout.NORTH);
        } else {
            String text = message.content();
            if (text != null && text.startsWith("[Sticker:") && text.endsWith("]")) {
                String inner = text.substring(9, text.length() - 1).trim();
                JPanel holder = new JPanel(new FlowLayout(flowAlign, 0, 0));
                holder.setOpaque(false);
                holder.add(buildStickerCard(inner));
                contentHolder.add(holder, BorderLayout.NORTH);
            } else if (text != null && EmojiAssets.hasEmoji(text.trim())) {
                ImageIcon jumbo = EmojiAssets.getIcon(text.trim(), 44);
                if (jumbo != null) {
                    JLabel emoLabel = new JLabel(jumbo);
                    emoLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
                    emoLabel.setToolTipText(text.trim());
                    JPanel holder = new JPanel(new FlowLayout(flowAlign, 0, 0));
                    holder.setOpaque(false);
                    holder.add(emoLabel);
                    contentHolder.add(holder, BorderLayout.NORTH);
                } else {
                    addNormalText(text, isMe, flowAlign);
                }
            } else {
                addNormalText(text, isMe, flowAlign);
            }

            if (message.hasAttachment()) {
                JPanel attachHolder = new JPanel(new FlowLayout(flowAlign, 0, 4));
                attachHolder.setOpaque(false);
                attachHolder.add(buildAttachmentCard());
                contentHolder.add(attachHolder, BorderLayout.CENTER);
            }
        }
        contentHolder.revalidate();
        contentHolder.repaint();
    }

    private void addNormalText(String text, boolean isMe, int flowAlign) {
        RoundedPanel bubble = new RoundedPanel(14);
        bubble.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
        if (isMe) {
            bubble.setBackground(DiscordTheme.BLURPLE);
        } else {
            bubble.setBackground(new Color(0x2B, 0x2D, 0x31));
        }

        int maxBubbleWidth = Math.max(80, Math.min(520, currentWrapWidth - 60));

        if (EmojiAssets.containsEmoji(text)) {
            String html = EmojiAssets.toHtml(text, maxBubbleWidth);
            if (isMe) {
                html = html.replace("color:#D9DADE", "color:#FFFFFF");
            }
            JLabel emoLabel = new JLabel(html);
            emoLabel.setBorder(null);
            bubble.add(emoLabel);
        } else {
            normalText = new WrappedTextArea(text, maxBubbleWidth);
            normalText.setFont(DiscordTheme.font(13.5f));
            normalText.setForeground(isMe ? Color.WHITE : new Color(0xDB, 0xDE, 0xE1));
            normalText.setBorder(null);
            bubble.add(normalText);
        }

        JPanel holder = new JPanel(new FlowLayout(flowAlign, 0, 0));
        holder.setOpaque(false);
        holder.add(bubble);
        contentHolder.add(holder, BorderLayout.NORTH);
    }

    private JComponent buildStickerCard(String query) {
        EmojiAssets.StickerItem s = EmojiAssets.findSticker(query);
        RoundedPanel card = new RoundedPanel(10);
        card.setLayout(new BorderLayout(10, 0));
        card.setBackground(new Color(0x23, 0x24, 0x28));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x35, 0x37, 0x3C), 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        card.setPreferredSize(new Dimension(200, 72));
        card.setMaximumSize(new Dimension(200, 72));

        String hex = (s != null) ? s.hex() : "1f389";
        String name = (s != null) ? s.name() : query;
        String tag = (s != null) ? s.tag() : "Nhãn dán";
        Color accent = (s != null) ? s.accent() : DiscordTheme.BLURPLE;

        ImageIcon icon = EmojiAssets.getIconByHex(hex, 48);
        JLabel iconLbl = new JLabel(icon, SwingConstants.CENTER);
        card.add(iconLbl, BorderLayout.WEST);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

        JLabel title = new JLabel(name);
        title.setFont(DiscordTheme.bold(12f));
        title.setForeground(DiscordTheme.TEXT_PRIMARY);
        info.add(title);
        info.add(Box.createVerticalStrut(4));

        JLabel tagLbl = new JLabel(" " + tag + " · Sticker ");
        tagLbl.setFont(DiscordTheme.bold(9f));
        tagLbl.setForeground(accent);
        tagLbl.setOpaque(true);
        tagLbl.setBackground(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 35));
        info.add(tagLbl);

        card.add(info, BorderLayout.CENTER);

        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wrapper.setOpaque(false);
        wrapper.add(card);
        return wrapper;
    }

    private JComponent buildAttachmentCard() {
        RoundedPanel card = new RoundedPanel(8);
        boolean failed = message.attachmentFailed();

        int targetWidth = Math.min(currentWrapWidth, 480);
        card.setPreferredSize(new Dimension(targetWidth, 54));
        card.setMaximumSize(new Dimension(targetWidth, 54));

        if (failed) {
            card.setBackground(new Color(0x2F, 0x22, 0x24));
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0x52, 0x29, 0x2C), 1),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)
            ));
        } else {
            card.setBackground(new Color(0x2B, 0x2D, 0x31));
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0x3B, 0x3D, 0x44), 1),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)
            ));
        }
        card.setLayout(new BorderLayout(12, 0));

        // Icon left
        JLabel fileIcon = new JLabel(IconFactory.phosphor(failed ? "file-text.svg" : "file-text.svg",
                24, failed ? DiscordTheme.DANGER : DiscordTheme.ACCENT_SOFT));
        card.add(fileIcon, BorderLayout.WEST);

        // Center info
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        nameRow.setOpaque(false);
        JLabel nameLabel = new JLabel(message.attachmentName());
        nameLabel.setFont(DiscordTheme.bold(12f));
        nameLabel.setForeground(failed ? new Color(0xFF, 0x96, 0x98) : DiscordTheme.TEXT_PRIMARY);
        nameRow.add(nameLabel);

        if (failed) {
            JLabel badge = new JLabel(" MẪU ");
            badge.setOpaque(true);
            badge.setBackground(new Color(0x3C, 0x3E, 0x44));
            badge.setForeground(DiscordTheme.TEXT_SECONDARY);
            badge.setFont(DiscordTheme.bold(9f));
            nameRow.add(badge);
        }
        center.add(nameRow);

        String sizeText = formatFileSize(message.attachmentSize());
        String metaText = failed ? (sizeText + " · Tệp minh họa · Gửi thất bại") : sizeText;
        JLabel metaLabel = new JLabel(metaText);
        metaLabel.setFont(DiscordTheme.font(10f));
        metaLabel.setForeground(failed ? new Color(0xF5, 0x65, 0x65) : DiscordTheme.TEXT_MUTED);
        metaLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 0, 0));
        center.add(metaLabel);

        card.add(center, BorderLayout.CENTER);

        // Right actions
        if (failed) {
            JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            rightActions.setOpaque(false);

            JButton resendBtn = new JButton("Gửi lại", IconFactory.phosphor("arrow-clockwise.svg", 14, new Color(0xFF, 0x96, 0x98)));
            resendBtn.setFont(DiscordTheme.bold(11f));
            resendBtn.setForeground(new Color(0xFF, 0x96, 0x98));
            resendBtn.setContentAreaFilled(false);
            resendBtn.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            resendBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            resendBtn.addActionListener(e -> {
                if (listener != null) listener.onResend(message);
            });
            rightActions.add(resendBtn);

            JButton delBtn = new JButton(IconFactory.phosphor("trash.svg", 14, DiscordTheme.TEXT_MUTED));
            delBtn.setContentAreaFilled(false);
            delBtn.setBorderPainted(false);
            delBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            delBtn.addActionListener(e -> {
                if (listener != null) listener.onDelete(message);
            });
            rightActions.add(delBtn);

            card.add(rightActions, BorderLayout.EAST);
        } else {
            JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            rightActions.setOpaque(false);

            JButton dlBtn = new JButton(IconFactory.phosphor("download-simple.svg", 16, DiscordTheme.TEXT_SECONDARY));
            dlBtn.setToolTipText("Tải tệp xuống");
            dlBtn.setContentAreaFilled(false);
            dlBtn.setBorderPainted(false);
            dlBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            dlBtn.addActionListener(e -> {
                if (listener != null) listener.onDownload(message);
            });
            rightActions.add(dlBtn);

            card.add(rightActions, BorderLayout.EAST);
        }

        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wrapper.setOpaque(false);
        wrapper.add(card);
        return wrapper;
    }

    private void startEdit() {
        isEditing = true;
        actionToolbar.setVisible(false);
        contentHolder.removeAll();

        RoundedPanel editCard = new RoundedPanel(8);
        editCard.setBackground(DiscordTheme.COMPOSER);
        editCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x3D, 0x3F, 0x46), 1),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        editCard.setLayout(new BorderLayout(0, 8));

        JTextArea editArea = new JTextArea(message.content(), 2, 20);
        editArea.setLineWrap(true);
        editArea.setWrapStyleWord(true);
        editArea.setOpaque(false);
        editArea.setFont(DiscordTheme.font(13f));
        editArea.setForeground(DiscordTheme.TEXT_PRIMARY);
        editArea.setCaretColor(Color.WHITE);
        editArea.setBorder(null);

        JScrollPane scroll = new JScrollPane(editArea);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        editCard.add(scroll, BorderLayout.CENTER);

        JPanel bottomRow = new JPanel(new BorderLayout());
        bottomRow.setOpaque(false);

        JLabel editHint = new JLabel("Đang chỉnh sửa tin nhắn · nhấn Esc để hủy");
        editHint.setFont(DiscordTheme.font(10f));
        editHint.setForeground(DiscordTheme.TEXT_MUTED);
        bottomRow.add(editHint, BorderLayout.WEST);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btns.setOpaque(false);

        JButton cancelBtn = new JButton("Hủy");
        cancelBtn.setFont(DiscordTheme.semibold(11f));
        cancelBtn.setForeground(DiscordTheme.TEXT_SECONDARY);
        cancelBtn.setContentAreaFilled(false);
        cancelBtn.setBorderPainted(false);
        cancelBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cancelBtn.addActionListener(e -> {
            isEditing = false;
            buildNormalContent();
        });
        btns.add(cancelBtn);

        JButton saveBtn = new JButton("Lưu thay đổi");
        saveBtn.setFont(DiscordTheme.bold(11f));
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setBackground(DiscordTheme.ACCENT);
        saveBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        saveBtn.addActionListener(e -> {
            String newText = editArea.getText().trim();
            if (!newText.isEmpty() && listener != null) {
                listener.onEdit(message, newText);
            }
            isEditing = false;
            buildNormalContent();
        });
        btns.add(saveBtn);

        bottomRow.add(btns, BorderLayout.EAST);
        editCard.add(bottomRow, BorderLayout.SOUTH);

        contentHolder.add(editCard, BorderLayout.CENTER);
        contentHolder.revalidate();
        contentHolder.repaint();
        editArea.requestFocusInWindow();
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    void updateWrapWidth(int wrapWidth) {
        this.currentWrapWidth = wrapWidth;
        if (normalText != null) {
            normalText.setWrapWidth(Math.max(80, Math.min(520, wrapWidth - 60)));
        }
    }
}

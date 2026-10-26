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
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** One message row: avatar, author, text, inline editor, file attachment card, and action buttons. */
final class MessageRow extends JPanel {
    private static MessageRow activeHoveredRow = null;

    public static void clearActiveHover() {
        if (activeHoveredRow != null) {
            activeHoveredRow.hideToolbar();
            activeHoveredRow = null;
        }
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("M/d/yy, h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter COMPACT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final int AVATAR_SIZE = 34;
    private static final int AVATAR_GAP = 12;

    public interface MessageActionListener {
        void onEdit(ChatMessage message, String newContent);
        void onRecall(ChatMessage message);
        void onDelete(ChatMessage message);
        void onResend(ChatMessage message);
        default void onDownload(ChatMessage message) {}
        default void onReaction(ChatMessage message, String emoji) {}
        default void onReply(ChatMessage message) {}
    }

    private ChatMessage message;
    private final MessageActionListener listener;
    private final JPanel body = new JPanel(new BorderLayout());
    private final JPanel contentHolder = new JPanel(new BorderLayout(0, 6));
    private final RoundedPanel actionToolbar = new RoundedPanel(6);
    private final Timer hideToolbarTimer;
    private final JLabel compactTimeLabel;
    private boolean isHovered = false;
    private WrappedTextArea normalText;
    private boolean isEditing = false;
    private int currentWrapWidth;

    MessageRow(ChatMessage message, int wrapWidth, boolean beginsSenderGroup, MessageActionListener listener) {
        this.message = message;
        this.listener = listener;
        this.currentWrapWidth = wrapWidth;

        this.hideToolbarTimer = new Timer(150, e -> {
            try {
                PointerInfo pi = MouseInfo.getPointerInfo();
                if (pi != null && isShowing()) {
                    Point pt = pi.getLocation();
                    Point loc = getLocationOnScreen();
                    Rectangle bounds = new Rectangle(loc.x, loc.y, getWidth(), getHeight());
                    if (bounds.contains(pt)) {
                        return;
                    }
                }
            } catch (Exception ignored) {}
            hideToolbar();
            if (activeHoveredRow == this) {
                activeHoveredRow = null;
            }
        });
        this.hideToolbarTimer.setRepeats(false);

        setLayout(new BorderLayout(AVATAR_GAP, 0));
        setOpaque(false);
        setAlignmentX(CENTER_ALIGNMENT);
        setBorder(BorderFactory.createEmptyBorder(beginsSenderGroup ? 7 : 2, 4, 4, 4));

        boolean isMe = message.fromCurrentUser();
        if (beginsSenderGroup) {
            AvatarBadge avatar = new AvatarBadge(message.senderInitials(), message.senderId(), AVATAR_SIZE);
            avatar.setToolTipText(isMe ? "Tin nhắn của bạn" : "Tin nhắn từ " + message.senderName());
            add(avatar, BorderLayout.WEST);
            compactTimeLabel = null;
        } else {
            compactTimeLabel = new JLabel(COMPACT_TIME_FORMAT.format(message.sentAt()), SwingConstants.RIGHT);
            compactTimeLabel.setFont(DiscordTheme.font(10f));
            compactTimeLabel.setForeground(DiscordTheme.TEXT_MUTED);
            compactTimeLabel.setPreferredSize(new Dimension(AVATAR_SIZE, 18));
            compactTimeLabel.setVisible(false);
            add(compactTimeLabel, BorderLayout.WEST);
        }

        body.setOpaque(false);
        if (beginsSenderGroup) {
            body.add(buildAuthorRow(message), BorderLayout.NORTH);
        }

        contentHolder.setOpaque(false);

        actionToolbar.setLayout(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        actionToolbar.setBackground(new Color(0x31, 0x33, 0x38));
        actionToolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x3F, 0x42, 0x48), 1),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)
        ));
        actionToolbar.setVisible(false);

        buildNormalContent();
        body.add(contentHolder, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);

        MouseAdapter hoverAdapter = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hideToolbarTimer.stop();
                showToolbar();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hideToolbarTimer.restart();
            }
        };
        addMouseListener(hoverAdapter);
        body.addMouseListener(hoverAdapter);
        contentHolder.addMouseListener(hoverAdapter);
        actionToolbar.addMouseListener(hoverAdapter);
        if (compactTimeLabel != null) {
            compactTimeLabel.addMouseListener(hoverAdapter);
        }
    }

    public void hideToolbar() {
        hideToolbarTimer.stop();
        isHovered = false;
        actionToolbar.setVisible(false);
        if (compactTimeLabel != null) {
            compactTimeLabel.setVisible(false);
        }
        repaint();
    }

    public void showToolbar() {
        if (isEditing || message.recalled()) return;
        if (activeHoveredRow != null && activeHoveredRow != this) {
            activeHoveredRow.hideToolbar();
        }
        activeHoveredRow = this;
        isHovered = true;
        actionToolbar.setVisible(true);
        if (compactTimeLabel != null) {
            compactTimeLabel.setVisible(true);
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (isHovered && !isEditing && !message.recalled()) {
            g.setColor(new Color(0x2E, 0x30, 0x35, 120));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        super.paintComponent(g);
    }

    private JPanel buildAuthorRow(ChatMessage message) {
        JPanel authorRow = new JPanel(new BorderLayout(8, 0));
        authorRow.setOpaque(false);
        boolean isMe = message.fromCurrentUser();

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        JLabel author = new JLabel(message.senderName() != null ? message.senderName() : (isMe ? "Bạn" : "Người dùng"));
        author.setFont(DiscordTheme.bold(13.5f));
        author.setForeground(isMe ? new Color(0x58, 0x65, 0xF2) : Color.WHITE);
        left.add(author);

        String timeStr = TIME_FORMAT.format(message.sentAt());
        if (message.edited()) {
            timeStr += " (đã sửa)";
        }
        JLabel time = new JLabel(timeStr);
        time.setFont(DiscordTheme.font(10.5f));
        time.setForeground(DiscordTheme.TEXT_MUTED);
        left.add(time);

        authorRow.add(left, BorderLayout.WEST);

        JPanel tbWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        tbWrapper.setOpaque(false);
        tbWrapper.add(actionToolbar);
        authorRow.add(tbWrapper, BorderLayout.EAST);
        return authorRow;
    }

    private JButton createEmojiButton(String hex, String emojiChar, String tooltip) {
        ImageIcon icon = EmojiAssets.getIconByHex(hex, 16);
        JButton btn = icon != null ? new JButton(icon) : new JButton(IconFactory.phosphor("smiley.svg", 14, DiscordTheme.TEXT_SECONDARY));
        btn.setToolTipText(tooltip);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusable(false);
        btn.setMargin(new Insets(1, 3, 1, 3));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> {
            if (listener != null) listener.onReaction(message, emojiChar);
        });
        return btn;
    }

    private JButton createToolbarButton(String iconName, String tooltip, Runnable action) {
        JButton btn = new JButton(IconFactory.phosphor(iconName, 14, DiscordTheme.TEXT_SECONDARY));
        btn.setToolTipText(tooltip);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusable(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMargin(new Insets(1, 4, 1, 4));
        btn.addActionListener(e -> action.run());
        return btn;
    }

    private void buildNormalContent() {
        contentHolder.removeAll();
        boolean isMe = message.fromCurrentUser();

        // Setup actionToolbar buttons
        actionToolbar.removeAll();
        if (!message.recalled()) {
            actionToolbar.add(createEmojiButton("1f44d", "👍", "Thích (Like)"));
            actionToolbar.add(createEmojiButton("2764", "❤️", "Yêu thích (Heart)"));
            actionToolbar.add(createEmojiButton("1f602", "😂", "Haha"));

            JButton addReactionBtn = createToolbarButton("smiley.svg", "Thêm biểu cảm", () -> {
                if (listener != null) listener.onReaction(message, "👍");
            });
            actionToolbar.add(addReactionBtn);

            JButton replyBtn = createToolbarButton("arrow-u-up-left.svg", "Trả lời", () -> {
                if (listener != null) listener.onReply(message);
            });
            actionToolbar.add(replyBtn);

            if (isMe) {
                if (!message.hasAttachment()) {
                    JButton editBtn = createToolbarButton("note-pencil.svg", "Chỉnh sửa tin nhắn", () -> startEdit());
                    actionToolbar.add(editBtn);
                }
                JButton recallBtn = createToolbarButton("arrow-clockwise.svg", "Thu hồi tin nhắn", () -> {
                    if (listener != null) listener.onRecall(message);
                });
                JButton deleteBtn = createToolbarButton("trash.svg", "Xóa tin nhắn", () -> {
                    if (listener != null) listener.onDelete(message);
                });
                actionToolbar.add(recallBtn);
                actionToolbar.add(deleteBtn);
            }
        }

        if (message.recalled()) {
            JLabel recalledLabel = new JLabel("Tin nhắn đã được thu hồi");
            recalledLabel.setFont(DiscordTheme.font(12f).deriveFont(java.awt.Font.ITALIC));
            recalledLabel.setForeground(DiscordTheme.TEXT_MUTED);
            recalledLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

            JPanel holder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            holder.setOpaque(false);
            holder.add(recalledLabel);
            contentHolder.add(holder, BorderLayout.NORTH);
        } else {
            JComponent mainContentComp = null;
            String text = message.content();
            boolean hasAttachment = message.hasAttachment();
            boolean isDefaultAttachmentText = hasAttachment && (text == null || text.isBlank() || text.startsWith("Đã gửi tệp:") || text.equals(message.attachmentName()));

            if (hasAttachment && isDefaultAttachmentText) {
                mainContentComp = buildAttachmentCard();
            } else if (text != null && text.startsWith("[Sticker:") && text.endsWith("]")) {
                String inner = text.substring(9, text.length() - 1).trim();
                mainContentComp = buildStickerCard(inner);
            } else if (text != null && EmojiAssets.hasEmoji(text.trim())) {
                ImageIcon jumbo = EmojiAssets.getIcon(text.trim(), 44);
                if (jumbo != null) {
                    JLabel emoLabel = new JLabel(jumbo);
                    emoLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
                    emoLabel.setToolTipText(text.trim());
                    mainContentComp = emoLabel;
                } else {
                    mainContentComp = createBubbleText(text, isMe);
                }
            } else {
                mainContentComp = createBubbleText(text != null ? text : "", isMe);
            }

            JPanel rowWrapper = new JPanel(new BorderLayout(8, 0));
            rowWrapper.setOpaque(false);
            rowWrapper.add(mainContentComp, BorderLayout.WEST);
            if (!body.isAncestorOf(actionToolbar)) {
                JPanel tbWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
                tbWrapper.setOpaque(false);
                tbWrapper.add(actionToolbar);
                rowWrapper.add(tbWrapper, BorderLayout.EAST);
            }
            contentHolder.add(rowWrapper, BorderLayout.NORTH);

            // Link embed card preview
            String firstUrl = extractFirstUrl(text);
            if (firstUrl != null) {
                JPanel linkHolder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
                linkHolder.setOpaque(false);
                linkHolder.add(buildLinkPreviewCard(firstUrl));
                contentHolder.add(linkHolder, BorderLayout.CENTER);
            }

            if (hasAttachment && !isDefaultAttachmentText) {
                JPanel attachHolder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
                attachHolder.setOpaque(false);
                attachHolder.add(buildAttachmentCard());
                contentHolder.add(attachHolder, BorderLayout.SOUTH);
            }
        }
        contentHolder.revalidate();
        contentHolder.repaint();
    }

    private static String extractFirstUrl(String text) {
        if (text == null) return null;
        int idx = text.indexOf("http://");
        if (idx == -1) idx = text.indexOf("https://");
        if (idx == -1) return null;
        int end = text.indexOf(' ', idx);
        if (end == -1) end = text.indexOf('\n', idx);
        if (end == -1) end = text.length();
        return text.substring(idx, end).trim();
    }

    private JPanel buildLinkPreviewCard(String url) {
        RoundedPanel card = new RoundedPanel(8);
        card.setLayout(new BorderLayout(12, 0));
        card.setBackground(new Color(0x2B, 0x2D, 0x31));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x20, 0x22, 0x25), 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 14)
        ));

        // Red envelope / invite icon
        JLabel iconBadge = new JLabel(IconFactory.phosphor("envelope-simple-open.svg", 22, new Color(0xED, 0x42, 0x45)));
        JPanel iconPill = new RoundedPanel(8);
        iconPill.setBackground(new Color(0x3B, 0x2A, 0x2E));
        iconPill.setLayout(new GridBagLayout());
        iconPill.setPreferredSize(new Dimension(42, 42));
        iconPill.add(iconBadge);
        card.add(iconPill, BorderLayout.WEST);

        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setOpaque(false);

        JLabel subtitle = new JLabel("You've been invited, but");
        subtitle.setFont(DiscordTheme.font(11f));
        subtitle.setForeground(DiscordTheme.TEXT_MUTED);
        details.add(subtitle);
        details.add(Box.createVerticalStrut(2));

        String titleText = url.contains("discord.gg") ? "Invalid Invite" : "Liên kết đính kèm";
        JLabel title = new JLabel(titleText);
        title.setFont(DiscordTheme.bold(13f));
        title.setForeground(url.contains("discord.gg") ? new Color(0xED, 0x42, 0x45) : DiscordTheme.TEXT_PRIMARY);
        details.add(title);
        details.add(Box.createVerticalStrut(2));

        String hintText = url.contains("discord.gg") ? "Ask for a new invite" : url;
        JLabel hint = new JLabel(hintText);
        hint.setFont(DiscordTheme.font(11f));
        hint.setForeground(DiscordTheme.TEXT_MUTED);
        details.add(hint);

        card.add(details, BorderLayout.CENTER);
        return card;
    }

    private JComponent createBubbleText(String text, boolean isMe) {
        normalText = new WrappedTextArea(text, Math.max(80, Math.min(520, currentWrapWidth - 60)));
        normalText.setFont(DiscordTheme.font(13.5f));
        normalText.setForeground(DiscordTheme.TEXT_PRIMARY);
        normalText.setOpaque(false);
        normalText.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        return normalText;
    }

    private JComponent buildStickerCard(String stickerId) {
        EmojiAssets.StickerItem item = null;
        for (EmojiAssets.StickerItem s : EmojiAssets.STICKERS) {
            if (s.id().equals(stickerId)) {
                item = s;
                break;
            }
        }
        if (item == null) {
            return new JLabel("[Sticker: " + stickerId + "]");
        }

        RoundedPanel card = new RoundedPanel(8);
        card.setBackground(new Color(0x2B, 0x2D, 0x31));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x20, 0x22, 0x25), 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        card.setLayout(new BorderLayout(10, 0));

        ImageIcon stickerIcon = EmojiAssets.getIconByHex(item.hex(), 40);
        JLabel iconLabel = new JLabel(stickerIcon != null ? stickerIcon : IconFactory.phosphor("sticker.svg", 32, item.accent()));
        card.add(iconLabel, BorderLayout.WEST);

        JPanel meta = new JPanel();
        meta.setLayout(new BoxLayout(meta, BoxLayout.Y_AXIS));
        meta.setOpaque(false);

        JLabel nameLbl = new JLabel(item.name());
        nameLbl.setFont(DiscordTheme.bold(12.5f));
        nameLbl.setForeground(DiscordTheme.TEXT_PRIMARY);
        meta.add(nameLbl);

        JLabel tagLbl = new JLabel(item.tag() + " · Sticker");
        tagLbl.setFont(DiscordTheme.font(10.5f));
        tagLbl.setForeground(item.accent());
        meta.add(tagLbl);

        card.add(meta, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildAttachmentCard() {
        RoundedPanel card = new RoundedPanel(8);
        card.setLayout(new BorderLayout(10, 0));
        card.setBackground(new Color(0x2B, 0x2D, 0x31));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x20, 0x22, 0x25), 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        JLabel fileIcon = new JLabel(IconFactory.phosphor("file-text.svg", 24, DiscordTheme.ACCENT));
        card.add(fileIcon, BorderLayout.WEST);

        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setOpaque(false);

        String fileName = message.attachmentName() != null ? message.attachmentName() : "tệp_đính_kèm";
        JLabel nameLabel = new JLabel(fileName);
        nameLabel.setFont(DiscordTheme.semibold(12.5f));
        nameLabel.setForeground(new Color(0x00, 0xA8, 0xFC));
        nameLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        details.add(nameLabel);

        String sizeStr = formatFileSize(message.attachmentSize());
        JLabel sizeLabel = new JLabel(sizeStr);
        sizeLabel.setFont(DiscordTheme.font(10.5f));
        sizeLabel.setForeground(DiscordTheme.TEXT_MUTED);
        details.add(sizeLabel);

        card.add(details, BorderLayout.CENTER);

        if (message.hasAttachment()) {
            JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            rightActions.setOpaque(false);

            JButton dlBtn = new JButton(IconFactory.phosphor("download-simple.svg", 16, DiscordTheme.TEXT_SECONDARY));
            dlBtn.setToolTipText("Tải tệp về máy");
            dlBtn.setContentAreaFilled(false);
            dlBtn.setBorderPainted(false);
            dlBtn.setFocusable(false);
            dlBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            dlBtn.addActionListener(e -> {
                if (listener != null) listener.onDownload(message);
            });
            rightActions.add(dlBtn);

            card.add(rightActions, BorderLayout.EAST);
        }

        return card;
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

    public ChatMessage getMessage() {
        return message;
    }

    public void markRecalled(ChatMessage updated) {
        this.message = updated;
        if (actionToolbar != null) {
            actionToolbar.setVisible(false);
        }
        buildNormalContent();
        revalidate();
        repaint();
    }

    public void updateMessage(ChatMessage updated) {
        this.message = updated;
        buildNormalContent();
        revalidate();
        repaint();
    }

    void updateWrapWidth(int wrapWidth) {
        if (this.currentWrapWidth == wrapWidth) {
            return;
        }
        this.currentWrapWidth = wrapWidth;
        if (normalText != null) {
            normalText.setWrapWidth(Math.max(80, Math.min(520, wrapWidth - 60)));
        }
    }
}

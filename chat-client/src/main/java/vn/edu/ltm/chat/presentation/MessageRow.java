package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.domain.ChatMessage;
import vn.edu.ltm.chat.theme.DiscordTheme;
import vn.edu.ltm.chat.theme.IconFactory;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

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
        default void onReact(ChatMessage message, String emoji) {}
        default void onReaction(ChatMessage message, String emoji) { onReact(message, emoji); }
        default void onReply(ChatMessage message) {}
    }

    private ChatMessage message;
    private final MessageActionListener listener;
    private final JPanel body = new JPanel(new BorderLayout());
    private final JPanel contentHolder = new JPanel(new BorderLayout(0, 6));
    private final RoundedPanel actionToolbar = new RoundedPanel(6);
    private final Timer hideToolbarTimer;
    private final JLabel compactTimeLabel;
    private final MouseAdapter hoverAdapter;
    private boolean isHovered = false;
    private WrappedTextArea normalText;
    private boolean isEditing = false;
    private int currentWrapWidth;
    private final String currentUserId;
    private String parsedReplyAuthor = null;
    private String parsedReplySnippet = null;
    private String parsedMainContent = null;

    MessageRow(ChatMessage message, int wrapWidth, boolean beginsSenderGroup, MessageActionListener listener) {
        this(message, wrapWidth, beginsSenderGroup, null, listener);
    }

    MessageRow(ChatMessage message, int wrapWidth, boolean beginsSenderGroup, String currentUserId, MessageActionListener listener) {
        this.message = message;
        this.currentUserId = currentUserId;
        this.listener = listener;
        this.currentWrapWidth = wrapWidth;
        parseReplyHeader();

        this.hideToolbarTimer = new Timer(200, e -> {
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
            compactTimeLabel = new JLabel("", SwingConstants.RIGHT);
            compactTimeLabel.setFont(DiscordTheme.font(10f));
            compactTimeLabel.setForeground(DiscordTheme.TEXT_MUTED);
            compactTimeLabel.setPreferredSize(new Dimension(AVATAR_SIZE, 18));
            compactTimeLabel.setMinimumSize(new Dimension(AVATAR_SIZE, 18));
            compactTimeLabel.setMaximumSize(new Dimension(AVATAR_SIZE, 18));

            JPanel westHolder = new JPanel(new BorderLayout());
            westHolder.setOpaque(false);
            westHolder.setPreferredSize(new Dimension(AVATAR_SIZE, 18));
            westHolder.setMinimumSize(new Dimension(AVATAR_SIZE, 18));
            westHolder.setMaximumSize(new Dimension(AVATAR_SIZE, 18));
            westHolder.add(compactTimeLabel, BorderLayout.CENTER);
            add(westHolder, BorderLayout.WEST);
        }

        body.setOpaque(false);
        if (parsedReplyAuthor != null) {
            JPanel topStack = new JPanel();
            topStack.setOpaque(false);
            topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));
            topStack.add(buildReplySpineRow(parsedReplyAuthor, parsedReplySnippet));
            if (beginsSenderGroup) {
                topStack.add(buildAuthorRow(message));
            }
            body.add(topStack, BorderLayout.NORTH);
        } else if (beginsSenderGroup) {
            body.add(buildAuthorRow(message), BorderLayout.NORTH);
        }

        contentHolder.setOpaque(false);

        actionToolbar.setLayout(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        actionToolbar.setBackground(new Color(0x31, 0x33, 0x38));
        actionToolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x3F, 0x42, 0x48), 1),
                BorderFactory.createEmptyBorder(1, 3, 1, 3)
        ));
        actionToolbar.setVisible(false);

        buildNormalContent();
        body.add(contentHolder, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);

        this.hoverAdapter = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hideToolbarTimer.stop();
                showToolbar();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                Point p = getMousePosition();
                if (p != null) {
                    return;
                }
                hideToolbarTimer.restart();
            }
        };
        attachHoverRecursively(this);
    }

    public void showToolbar() {
        if (isEditing || message.recalled()) return;
        if (activeHoveredRow != null && activeHoveredRow != this) {
            activeHoveredRow.hideToolbar();
        }
        activeHoveredRow = this;
        isHovered = true;
        if (!actionToolbar.isVisible()) {
            actionToolbar.setVisible(true);
        }
        if (compactTimeLabel != null) {
            compactTimeLabel.setText(COMPACT_TIME_FORMAT.format(message.sentAt()));
        }
        repaint();
    }

    public void hideToolbar() {
        hideToolbarTimer.stop();
        isHovered = false;
        if (actionToolbar.isVisible()) {
            actionToolbar.setVisible(false);
        }
        if (compactTimeLabel != null) {
            compactTimeLabel.setText("");
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

    private void parseReplyHeader() {
        String raw = message.content();
        parsedReplyAuthor = null;
        parsedReplySnippet = null;
        if (raw != null && raw.startsWith("> @")) {
            int newlineIdx = raw.indexOf('\n');
            if (newlineIdx > 0) {
                String headerLine = raw.substring(0, newlineIdx).trim();
                int colonIdx = headerLine.indexOf(':');
                if (colonIdx > 3) {
                    parsedReplyAuthor = headerLine.substring(3, colonIdx).trim();
                    parsedReplySnippet = headerLine.substring(colonIdx + 1).trim();
                    parsedMainContent = raw.substring(newlineIdx + 1).trim();
                    return;
                }
            }
        }
        parsedMainContent = raw != null ? raw : "";
    }

    private JComponent buildReplySpineRow(String replyAuthor, String replySnippet) {
        JPanel spinePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        spinePanel.setOpaque(false);
        spinePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));

        JLabel branchIcon = new JLabel("╭─");
        branchIcon.setFont(new Font("Monospaced", Font.BOLD, 12));
        branchIcon.setForeground(new Color(0x4E, 0x50, 0x58));
        spinePanel.add(branchIcon);

        JLabel authorLabel = new JLabel("@" + replyAuthor);
        authorLabel.setFont(DiscordTheme.bold(11f));
        authorLabel.setForeground(new Color(0x58, 0x65, 0xF2));
        spinePanel.add(authorLabel);

        String snippet = replySnippet != null ? replySnippet.replace("\n", " ").trim() : "";
        if (snippet.length() > 65) snippet = snippet.substring(0, 62) + "...";
        JLabel snippetLabel = new JLabel(snippet);
        snippetLabel.setFont(DiscordTheme.font(11f));
        snippetLabel.setForeground(DiscordTheme.TEXT_MUTED);
        spinePanel.add(snippetLabel);

        return spinePanel;
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
        JLabel time = new JLabel(timeStr);
        time.setFont(DiscordTheme.font(10.5f));
        time.setForeground(DiscordTheme.TEXT_MUTED);
        left.add(time);

        authorRow.add(left, BorderLayout.WEST);
        authorRow.add(createToolbarWrapper(actionToolbar), BorderLayout.EAST);
        return authorRow;
    }

    private static JPanel createToolbarWrapper(JComponent toolbar) {
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0)) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(d.width, 0); // 0 height prevents vertical layout shift when hovered
            }
        };
        wrapper.setOpaque(false);
        wrapper.add(toolbar);
        return wrapper;
    }

    private JButton createEmojiToolbarButton(String hex, String emojiChar, String tooltip) {
        ImageIcon icon = EmojiAssets.getIconByHex(hex, 16);
        JButton btn = icon != null ? new JButton(icon) : new JButton(IconFactory.phosphor("smiley.svg", 14, DiscordTheme.TEXT_SECONDARY));
        btn.setToolTipText(tooltip);
        setupHoverButton(btn);
        btn.addActionListener(e -> {
            if (listener != null) listener.onReact(message, emojiChar);
        });
        return btn;
    }

    private JButton createToolbarButton(String iconName, String tooltip, Runnable action) {
        JButton btn = new JButton(IconFactory.phosphor(iconName, 14, DiscordTheme.TEXT_SECONDARY));
        btn.setToolTipText(tooltip);
        setupHoverButton(btn);
        if (action != null) btn.addActionListener(e -> action.run());
        return btn;
    }

    private JComponent createToolbarSeparator() {
        JPanel sep = new JPanel();
        sep.setPreferredSize(new Dimension(1, 14));
        sep.setBackground(new Color(0x4E, 0x50, 0x58));
        sep.setOpaque(true);
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 5));
        wrap.setOpaque(false);
        wrap.add(sep);
        return wrap;
    }

    private void showMoreMenu(JButton anchor, boolean isMe) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(new Color(0x11, 0x12, 0x14));
        menu.setBorder(BorderFactory.createLineBorder(new Color(0x23, 0x24, 0x28)));

        // Top quick reaction emoji row (matching Discord video)
        JPanel quickEmojiRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        quickEmojiRow.setOpaque(true);
        quickEmojiRow.setBackground(new Color(0x1E, 0x1F, 0x22));
        quickEmojiRow.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        String[][] quickEmojis = {
                {"1f4a9", "💩", ":poop: Click to react"},
                {"2764", "❤️", ":heart: Click to react"},
                {"1f44d", "👍", ":thumbsup: Click to react"},
                {"1f602", "😂", ":joy: Click to react"}
        };
        for (String[] emo : quickEmojis) {
            JButton emoBtn = new JButton();
            ImageIcon icon = EmojiAssets.getIconByHex(emo[0], 18);
            if (icon != null) emoBtn.setIcon(icon);
            else emoBtn.setText(emo[1]);
            emoBtn.setToolTipText(emo[2]);
            emoBtn.setPreferredSize(new Dimension(34, 30));
            emoBtn.setBackground(new Color(0x2B, 0x2D, 0x31));
            emoBtn.setBorder(BorderFactory.createLineBorder(new Color(0x38, 0x3A, 0x40), 1));
            emoBtn.setFocusPainted(false);
            emoBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            emoBtn.addActionListener(e -> {
                menu.setVisible(false);
                if (listener != null) listener.onReact(message, emo[1]);
            });
            quickEmojiRow.add(emoBtn);
        }
        menu.add(quickEmojiRow);
        menu.addSeparator();

        JMenuItem addReactionItem = createMenuItem("Thêm biểu cảm...", "smiley.svg", () -> {
            showQuickReactionPopup(anchor);
        });
        menu.add(addReactionItem);

        if (isMe && !message.hasAttachment()) {
            JMenuItem editItem = createMenuItem("Chỉnh sửa tin nhắn", "note-pencil.svg", this::startEdit);
            menu.add(editItem);
        }

        JMenuItem replyItem = createMenuItem("Trả lời", "arrow-u-up-left.svg", () -> {
            if (listener != null) listener.onReply(message);
        });
        menu.add(replyItem);

        JMenuItem copyItem = createMenuItem("Sao chép văn bản", "file-text.svg", () -> {
            String c = parsedMainContent != null ? parsedMainContent : message.content();
            if (c != null && !c.isBlank()) {
                java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(c);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
            }
        });
        menu.add(copyItem);

        if (isMe) {
            menu.addSeparator();
            JMenuItem recallItem = createMenuItem("Thu hồi tin nhắn", "arrow-clockwise.svg", () -> {
                if (listener != null) listener.onRecall(message);
            });
            menu.add(recallItem);

            JMenuItem deleteItem = createMenuItem("Xóa tin nhắn", "trash.svg", () -> {
                if (listener != null) listener.onDelete(message);
            });
            deleteItem.setForeground(new Color(0xF2, 0x3F, 0x43));
            menu.add(deleteItem);
        }

        menu.show(anchor, 0, anchor.getHeight() + 2);
    }

    private JMenuItem createMenuItem(String text, String iconName, Runnable action) {
        JMenuItem item = new JMenuItem(text, IconFactory.phosphor(iconName, 14, DiscordTheme.TEXT_SECONDARY));
        item.setFont(DiscordTheme.font(12f));
        item.setForeground(DiscordTheme.TEXT_PRIMARY);
        item.setBackground(new Color(0x11, 0x12, 0x14));
        item.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        item.addActionListener(e -> {
            if (action != null) action.run();
        });
        return item;
    }

    private void buildNormalContent() {
        contentHolder.removeAll();
        boolean isMe = message.fromCurrentUser();

        // Re-setup actionToolbar buttons according to current message state
        actionToolbar.removeAll();
        if (!message.recalled()) {
            // Quick 1-click reaction buttons (matching Discord)
            actionToolbar.add(createEmojiToolbarButton("1f4a9", "💩", ":poop: Click to react"));
            actionToolbar.add(createEmojiToolbarButton("2764", "❤️", ":heart: Click to react"));
            actionToolbar.add(createEmojiToolbarButton("1f44d", "👍", ":thumbsup: Click to react"));

            actionToolbar.add(createToolbarSeparator());

            JButton reactBtn = createToolbarButton("smiley.svg", "Thêm biểu cảm khác...", null);
            reactBtn.addActionListener(e -> showQuickReactionPopup(reactBtn));
            actionToolbar.add(reactBtn);

            JButton replyBtn = createToolbarButton("arrow-u-up-left.svg", "Trả lời", () -> {
                if (listener != null) listener.onReply(message);
            });
            actionToolbar.add(replyBtn);

            JButton moreBtn = createToolbarButton("dots-three.svg", "Tùy chọn khác", null);
            moreBtn.addActionListener(e -> showMoreMenu(moreBtn, isMe));
            actionToolbar.add(moreBtn);
        } else {
            actionToolbar.setVisible(false);
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
            String text = parsedMainContent != null ? parsedMainContent : (message.content() != null ? message.content() : "");
            boolean hasAttachment = message.hasAttachment();
            boolean isDefaultAttachmentText = hasAttachment && (text == null || text.isBlank() || text.startsWith("Đã gửi tệp:") || text.equals(message.attachmentName()));

            java.util.List<String> emojis = EmojiAssets.parseAllEmojis(text);
            if (hasAttachment && isDefaultAttachmentText) {
                mainContentComp = buildAttachmentCard();
            } else if (text != null && text.startsWith("[Sticker:") && text.endsWith("]")) {
                String inner = text.substring(9, text.length() - 1).trim();
                mainContentComp = buildStickerCard(inner);
            } else if (emojis.size() == 1) {
                ImageIcon jumbo = EmojiAssets.getIcon(emojis.get(0), 44);
                if (jumbo != null) {
                    JLabel emoLabel = new JLabel(jumbo);
                    emoLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
                    emoLabel.setToolTipText(emojis.get(0));
                    mainContentComp = emoLabel;
                } else {
                    mainContentComp = createBubbleText(text, isMe);
                }
            } else if (emojis.size() > 1) {
                JPanel emoStream = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
                emoStream.setOpaque(false);
                for (String emo : emojis) {
                    ImageIcon icon = EmojiAssets.getIcon(emo, 24);
                    if (icon != null) {
                        JLabel lbl = new JLabel(icon);
                        lbl.setToolTipText(emo);
                        emoStream.add(lbl);
                    }
                }
                mainContentComp = emoStream;
            } else {
                mainContentComp = createBubbleText(text != null ? text : "", isMe);
            }

            JPanel stack = new JPanel();
            stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
            stack.setOpaque(false);

            JPanel rowWrapper = new JPanel(new BorderLayout(8, 0));
            rowWrapper.setOpaque(false);
            rowWrapper.add(mainContentComp, BorderLayout.WEST);

            if (!body.isAncestorOf(actionToolbar)) {
                rowWrapper.add(createToolbarWrapper(actionToolbar), BorderLayout.EAST);
            }
            rowWrapper.setAlignmentX(0.0f);
            stack.add(rowWrapper);

            if (hasAttachment && !isDefaultAttachmentText) {
                JPanel attachHolder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
                attachHolder.setOpaque(false);
                attachHolder.add(buildAttachmentCard());
                attachHolder.setAlignmentX(0.0f);
                stack.add(attachHolder);
            }

            if (DiscordInviteCard.containsInvite(text)) {
                String code = DiscordInviteCard.extractInviteCode(text);
                JPanel inviteHolder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 6));
                inviteHolder.setOpaque(false);
                inviteHolder.add(new DiscordInviteCard(code));
                inviteHolder.setAlignmentX(0.0f);
                stack.add(inviteHolder);
            }

            JComponent reactionsRow = buildReactionsRow();
            if (reactionsRow != null) {
                reactionsRow.setAlignmentX(0.0f);
                stack.add(reactionsRow);
            }

            contentHolder.add(stack, BorderLayout.CENTER);
        }
        contentHolder.revalidate();
        contentHolder.repaint();
        attachHoverRecursively(this);
    }

    private void attachHoverRecursively(Component comp) {
        if (comp == null) return;
        comp.removeMouseListener(hoverAdapter);
        comp.addMouseListener(hoverAdapter);
        if (comp instanceof Container container) {
            for (Component child : container.getComponents()) {
                attachHoverRecursively(child);
            }
        }
    }

    private void showQuickReactionPopup(Component invoker) {
        javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
        popup.setBackground(new Color(0x2B, 0x2D, 0x31));
        popup.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x3B, 0x3D, 0x44), 1),
                BorderFactory.createEmptyBorder(3, 4, 3, 4)
        ));

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        panel.setOpaque(false);

        String[] quickEmojis = {"👍", "❤️", "😂", "😮", "😢", "😡", "🎉", "🔥"};
        for (String emo : quickEmojis) {
            JButton btn = new JButton();
            ImageIcon icon = EmojiAssets.getIcon(emo, 20);
            if (icon != null) {
                btn.setIcon(icon);
            } else {
                btn.setText(emo);
                btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
            }
            btn.setToolTipText(emo);
            setupHoverButton(btn);
            btn.addActionListener(e -> {
                popup.setVisible(false);
                if (listener != null) {
                    listener.onReact(message, emo);
                }
            });
            panel.add(btn);
        }

        JButton moreBtn = new JButton(IconFactory.phosphor("plus.svg", 16, DiscordTheme.TEXT_SECONDARY));
        moreBtn.setToolTipText("Thêm biểu cảm khác...");
        setupHoverButton(moreBtn);
        moreBtn.addActionListener(e -> {
            popup.setVisible(false);
            EmojiPickerPopup picker = new EmojiPickerPopup(emo -> {
                if (listener != null) {
                    listener.onReact(message, emo);
                }
            }, null);
            picker.show(invoker, 0, -365);
        });
        panel.add(moreBtn);

        popup.add(panel);
        int popY = -42;
        int popX = 0;
        try {
            int popW = panel.getPreferredSize().width;
            popX = -popW / 2 + invoker.getWidth() / 2;
            if (invoker.isShowing()) {
                Point loc = invoker.getLocationOnScreen();
                if (loc.y < 130) {
                    popY = invoker.getHeight() + 4;
                }
                int screenW = invoker.getGraphicsConfiguration().getBounds().width;
                if (loc.x + popX + popW > screenW - 20) {
                    popX = screenW - 20 - loc.x - popW;
                }
                if (loc.x + popX < 10) {
                    popX = 10 - loc.x;
                }
            }
        } catch (Exception ignored) {}
        popup.show(invoker, popX, popY);
    }

    private static void setupHoverButton(JButton btn) {
        btn.setContentAreaFilled(false);
        btn.setBorder(BorderFactory.createEmptyBorder(4, 5, 4, 5));
        btn.setFocusable(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setContentAreaFilled(true);
                btn.setBackground(new Color(0x35, 0x37, 0x3C));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                btn.setContentAreaFilled(false);
            }
        });
    }

    private JComponent buildReactionsRow() {
        Map<String, java.util.List<String>> reactions = message.reactions();
        if (reactions == null || reactions.isEmpty()) {
            return null;
        }

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row.setOpaque(false);

        for (Map.Entry<String, java.util.List<String>> entry : reactions.entrySet()) {
            String emo = entry.getKey();
            java.util.List<String> userList = entry.getValue();
            if (userList == null || userList.isEmpty()) continue;

            boolean myReaction = (currentUserId != null && userList.contains(currentUserId))
                    || userList.contains("me");

            RoundedPanel pill = new RoundedPanel(8);
            pill.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 2));
            pill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            Color bg = myReaction ? new Color(0x58, 0x65, 0xF2, 45) : new Color(0x2B, 0x2D, 0x31);
            Color border = myReaction ? DiscordTheme.BLURPLE : new Color(0x3B, 0x3D, 0x44);
            pill.setBackground(bg);
            pill.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(border, 1),
                    BorderFactory.createEmptyBorder(2, 6, 2, 6)
            ));

            ImageIcon icon = EmojiAssets.getIcon(emo, 16);
            JLabel emoLbl;
            if (icon != null) {
                emoLbl = new JLabel(icon);
            } else {
                emoLbl = new JLabel(emo);
                emoLbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
                emoLbl.setForeground(DiscordTheme.TEXT_PRIMARY);
            }
            pill.add(emoLbl);

            JLabel countLbl = new JLabel(String.valueOf(userList.size()));
            countLbl.setFont(DiscordTheme.bold(11.5f));
            countLbl.setForeground(myReaction ? DiscordTheme.BLURPLE : DiscordTheme.TEXT_SECONDARY);
            pill.add(countLbl);

            pill.setToolTipText(String.join(", ", userList));

            pill.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (listener != null) {
                        listener.onReact(message, emo);
                    }
                }
            });

            row.add(pill);
        }

        return row;
    }

    private JComponent createBubbleText(String text, boolean isMe) {
        normalText = new WrappedTextArea(text, Math.max(80, Math.min(520, currentWrapWidth - 60)));
        normalText.setFont(DiscordTheme.font(13.5f));
        normalText.setForeground(DiscordTheme.TEXT_PRIMARY);
        normalText.setOpaque(false);
        normalText.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        if (!message.edited()) {
            return normalText;
        }

        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        wrapper.setOpaque(false);
        wrapper.add(normalText);

        JLabel editedTag = new JLabel("(đã chỉnh sửa)");
        editedTag.setFont(DiscordTheme.font(10f));
        editedTag.setForeground(DiscordTheme.TEXT_MUTED);
        wrapper.add(editedTag);
        return wrapper;
    }

    private JComponent buildStickerCard(String stickerId) {
        EmojiAssets.StickerItem item = null;
        for (EmojiAssets.StickerItem s : EmojiAssets.STICKERS) {
            if (s.id().equalsIgnoreCase(stickerId)
                    || s.name().equalsIgnoreCase(stickerId)
                    || s.id().replace("-", " ").equalsIgnoreCase(stickerId)
                    || s.id().replace("-", "").equalsIgnoreCase(stickerId.replace(" ", ""))) {
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
        editCard.setLayout(new BorderLayout(0, 6));

        String editInitialText = parsedMainContent != null ? parsedMainContent : message.content();
        JTextArea editArea = new JTextArea(editInitialText, 2, 20);
        editArea.setLineWrap(true);
        editArea.setWrapStyleWord(true);
        editArea.setOpaque(false);
        editArea.setFont(DiscordTheme.font(13f));
        editArea.setForeground(DiscordTheme.TEXT_PRIMARY);
        editArea.setCaretColor(Color.WHITE);
        editArea.setBorder(null);

        Runnable saveAction = () -> {
            String newText = editArea.getText().trim();
            if (!newText.isEmpty() && listener != null && !newText.equals(editInitialText)) {
                String fullNewContent = parsedReplyAuthor != null
                        ? ("> @" + parsedReplyAuthor + ": " + parsedReplySnippet + "\n" + newText)
                        : newText;
                listener.onEdit(message, fullNewContent);
            }
            isEditing = false;
            buildNormalContent();
        };

        Runnable cancelAction = () -> {
            isEditing = false;
            buildNormalContent();
        };

        editArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "save-edit");
        editArea.getActionMap().put("save-edit", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { saveAction.run(); }
        });
        editArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), "insert-newline");
        editArea.getActionMap().put("insert-newline", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { editArea.replaceSelection("\n"); }
        });
        editArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel-edit");
        editArea.getActionMap().put("cancel-edit", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { cancelAction.run(); }
        });

        JScrollPane scroll = new JScrollPane(editArea);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        editCard.add(scroll, BorderLayout.CENTER);

        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        bottomRow.setOpaque(false);

        JLabel escPrefix = new JLabel("escape để");
        escPrefix.setFont(DiscordTheme.font(10.5f));
        escPrefix.setForeground(DiscordTheme.TEXT_MUTED);

        JLabel cancelLink = new JLabel("huỷ");
        cancelLink.setFont(DiscordTheme.font(10.5f));
        cancelLink.setForeground(new Color(0x00, 0xA8, 0xFC));
        cancelLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cancelLink.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { cancelAction.run(); }
        });

        JLabel dot = new JLabel("• enter để");
        dot.setFont(DiscordTheme.font(10.5f));
        dot.setForeground(DiscordTheme.TEXT_MUTED);

        JLabel saveLink = new JLabel("lưu");
        saveLink.setFont(DiscordTheme.font(10.5f));
        saveLink.setForeground(new Color(0x00, 0xA8, 0xFC));
        saveLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        saveLink.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { saveAction.run(); }
        });

        bottomRow.add(escPrefix);
        bottomRow.add(cancelLink);
        bottomRow.add(dot);
        bottomRow.add(saveLink);

        editCard.add(bottomRow, BorderLayout.SOUTH);

        contentHolder.add(editCard, BorderLayout.CENTER);
        contentHolder.revalidate();
        contentHolder.repaint();
        editArea.requestFocusInWindow();
        editArea.selectAll();
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
        parseReplyHeader();
        if (actionToolbar != null) {
            actionToolbar.setVisible(false);
        }
        buildNormalContent();
        revalidate();
        repaint();
    }

    public void updateMessage(ChatMessage updated) {
        this.message = updated;
        parseReplyHeader();
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

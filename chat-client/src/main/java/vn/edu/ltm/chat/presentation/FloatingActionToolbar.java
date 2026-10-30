package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.theme.DiscordTheme;
import vn.edu.ltm.chat.theme.IconFactory;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Discord-style floating action toolbar displayed at the top-right of a message row on hover.
 * Contains: 💩, ❤️, 👍, vertical separator, add reaction (smiley), reply (arrow), and more options (...).
 */
final class FloatingActionToolbar extends JPanel {

    public interface Listener {
        void onReact(String emoji);
        void onAddReaction(Component invoker);
        void onEdit();
        void onReply();
        void onMore(Component invoker);
    }

    private final Listener listener;
    private final boolean canEdit;

    FloatingActionToolbar(boolean canEdit, Listener listener) {
        this.canEdit = canEdit;
        this.listener = listener;
        setOpaque(false);
        setLayout(new FlowLayout(FlowLayout.CENTER, 2, 2));
        setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        buildButtons();
    }

    private void buildButtons() {
        removeAll();

        // 1. 💩 (Poop)
        add(new EmojiButton("1f4a9", "💩", ":poop: Phản ứng biểu cảm", emoji -> {
            if (listener != null) listener.onReact(emoji);
        }));

        // 2. ❤️ (Red heart)
        add(new EmojiButton("2764", "❤️", ":heart: Phản ứng biểu cảm", emoji -> {
            if (listener != null) listener.onReact(emoji);
        }));

        // 3. 👍 (Thumbs up)
        add(new EmojiButton("1f44d", "👍", ":thumbsup: Phản ứng biểu cảm", emoji -> {
            if (listener != null) listener.onReact(emoji);
        }));

        // 4. Vertical divider |
        add(createSeparator());

        // 5. Add reaction (smiley face)
        ToolbarIconButton addReactionBtn = new ToolbarIconButton(
                "smiley.svg",
                "Thêm phản ứng",
                inv -> {
                    if (listener != null) listener.onAddReaction(inv);
                }
        );
        add(addReactionBtn);

        // Optional: Edit pencil if sender can edit this message
        if (canEdit) {
            ToolbarIconButton editBtn = new ToolbarIconButton(
                    "note-pencil.svg",
                    "Chỉnh sửa tin nhắn",
                    inv -> {
                        if (listener != null) listener.onEdit();
                    }
            );
            add(editBtn);
        }

        // 6. Reply (curved arrow)
        ToolbarIconButton replyBtn = new ToolbarIconButton(
                "arrow-u-up-left.svg",
                "Trả lời",
                inv -> {
                    if (listener != null) listener.onReply();
                }
        );
        add(replyBtn);

        // 7. More options (...)
        ToolbarIconButton moreBtn = new ToolbarIconButton(
                "dots-three.svg",
                "Tùy chọn khác",
                inv -> {
                    if (listener != null) listener.onMore(inv);
                }
        );
        add(moreBtn);
    }

    private static JComponent createSeparator() {
        JPanel sep = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(new Color(0x4E, 0x50, 0x58));
                g.fillRect(3, 4, 1, getHeight() - 8);
            }
        };
        sep.setOpaque(false);
        sep.setPreferredSize(new Dimension(8, 28));
        return sep;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Discord floating background
        g2.setColor(new Color(0x31, 0x33, 0x38));
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

        // Discord subtle border
        g2.setColor(new Color(0x3F, 0x42, 0x48));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

        g2.dispose();
        super.paintComponent(g);
    }

    private static final class EmojiButton extends JButton {
        private boolean isHovered = false;

        EmojiButton(String hex, String emojiChar, String tooltip, java.util.function.Consumer<String> action) {
            ImageIcon icon = EmojiAssets.getIconByHex(hex, 18);
            if (icon != null) {
                setIcon(icon);
            } else {
                setText(emojiChar);
                setFont(DiscordTheme.font(14f));
            }
            setToolTipText(tooltip);
            setPreferredSize(new Dimension(28, 28));
            setFocusable(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    isHovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    isHovered = false;
                    repaint();
                }
            });

            if (action != null) {
                addActionListener(e -> action.accept(emojiChar));
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (isHovered) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x35, 0x37, 0x3C));
                g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 6, 6);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    private static final class ToolbarIconButton extends JButton {
        private final String iconName;
        private boolean isHovered = false;

        ToolbarIconButton(String iconName, String tooltip, java.util.function.Consumer<JButton> action) {
            super(IconFactory.phosphor(iconName, 16, DiscordTheme.TEXT_SECONDARY));
            this.iconName = iconName;
            setToolTipText(tooltip);
            setPreferredSize(new Dimension(28, 28));
            setFocusable(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    isHovered = true;
                    setIcon(IconFactory.phosphor(iconName, 16, DiscordTheme.TEXT_PRIMARY));
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    isHovered = false;
                    setIcon(IconFactory.phosphor(iconName, 16, DiscordTheme.TEXT_SECONDARY));
                    repaint();
                }
            });

            if (action != null) {
                addActionListener(e -> action.accept(this));
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (isHovered) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x35, 0x37, 0x3C));
                g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 6, 6);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}


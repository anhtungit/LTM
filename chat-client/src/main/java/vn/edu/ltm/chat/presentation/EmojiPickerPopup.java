package vn.edu.ltm.chat.presentation;

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
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

/** Full-featured Emoji & Sticker popup with 100% offline Twemoji graphics. */
public final class EmojiPickerPopup extends JPopupMenu {
    private final Consumer<String> onEmojiSelected;
    private final Consumer<String> onStickerSelected;

    public EmojiPickerPopup(Consumer<String> onEmojiSelected, Consumer<String> onStickerSelected) {
        this.onEmojiSelected = onEmojiSelected;
        this.onStickerSelected = onStickerSelected;

        setBackground(DiscordTheme.SIDEBAR);
        setBorder(BorderFactory.createLineBorder(new Color(0x3B, 0x3D, 0x44), 1));

        RoundedPanel container = new RoundedPanel(10);
        container.setBackground(DiscordTheme.SIDEBAR);
        container.setPreferredSize(new Dimension(420, 360));
        container.setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(DiscordTheme.bold(11f));
        tabs.setBackground(DiscordTheme.SIDEBAR);
        tabs.setForeground(DiscordTheme.TEXT_SECONDARY);

        tabs.addTab("Mặt cười", IconFactory.phosphor("smiley.svg", 15, DiscordTheme.TEXT_SECONDARY),
                buildEmojiGrid(EmojiData.EMOTION));
        tabs.addTab("Cử chỉ", IconFactory.phosphor("users.svg", 15, DiscordTheme.TEXT_SECONDARY),
                buildEmojiGrid(EmojiData.GESTURE));
        tabs.addTab("Trái tim", EmojiAssets.getIconByHex("2764", 15),
                buildEmojiGrid(EmojiData.HEART));
        tabs.addTab("Biểu tượng", IconFactory.phosphor("compass.svg", 15, DiscordTheme.TEXT_SECONDARY),
                buildEmojiGrid(EmojiData.OBJECT));
        tabs.addTab("Sticker", IconFactory.phosphor("sticker.svg", 15, DiscordTheme.TEXT_SECONDARY),
                buildStickerGrid());

        container.add(tabs, BorderLayout.CENTER);
        add(container);
    }

    private JComponent buildEmojiGrid(List<EmojiData.EmojiItem> items) {
        JPanel grid = new JPanel(new GridLayout(0, 8, 4, 4));
        grid.setBackground(DiscordTheme.SIDEBAR);
        grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        for (EmojiData.EmojiItem item : items) {
            ImageIcon icon = EmojiAssets.getIconByHex(item.hex(), 26);
            JButton btn = new JButton();
            if (icon != null) {
                btn.setIcon(icon);
            } else {
                btn.setText(item.emoji());
            }
            btn.setPreferredSize(new Dimension(36, 36));
            btn.setContentAreaFilled(false);
            btn.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            btn.setFocusPainted(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.setToolTipText(item.emoji());

            btn.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    btn.setContentAreaFilled(true);
                    btn.setBackground(DiscordTheme.HOVER);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    btn.setContentAreaFilled(false);
                }
            });

            btn.addActionListener(e -> {
                if (onEmojiSelected != null) {
                    onEmojiSelected.accept(item.emoji());
                }
                setVisible(false);
            });
            grid.add(btn);
        }

        JPanel anchor = new JPanel(new BorderLayout());
        anchor.setBackground(DiscordTheme.SIDEBAR);
        anchor.add(grid, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(anchor);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        return scroll;
    }

    private JComponent buildStickerGrid() {
        JPanel grid = new JPanel(new GridLayout(0, 2, 8, 8));
        grid.setBackground(DiscordTheme.SIDEBAR);
        grid.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        for (EmojiAssets.StickerItem s : EmojiAssets.STICKERS) {
            RoundedPanel card = new RoundedPanel(8);
            card.setLayout(new BorderLayout(8, 0));
            card.setBackground(new Color(0x23, 0x24, 0x28));
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0x35, 0x37, 0x3C), 1),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)
            ));
            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            ImageIcon icon = EmojiAssets.getIconByHex(s.hex(), 42);
            JLabel iconLbl = new JLabel(icon, SwingConstants.CENTER);
            card.add(iconLbl, BorderLayout.WEST);

            JPanel info = new JPanel();
            info.setOpaque(false);
            info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

            JLabel title = new JLabel(s.name());
            title.setFont(DiscordTheme.bold(12f));
            title.setForeground(DiscordTheme.TEXT_PRIMARY);
            info.add(title);
            info.add(Box.createVerticalStrut(4));

            JLabel tag = new JLabel(" " + s.tag() + " ");
            tag.setFont(DiscordTheme.bold(9f));
            tag.setForeground(s.accent());
            tag.setOpaque(true);
            tag.setBackground(new Color(s.accent().getRed(), s.accent().getGreen(), s.accent().getBlue(), 40));
            info.add(tag);

            card.add(info, BorderLayout.CENTER);

            card.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    card.setBackground(DiscordTheme.HOVER);
                    card.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(s.accent(), 1),
                            BorderFactory.createEmptyBorder(8, 8, 8, 8)
                    ));
                    card.repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    card.setBackground(new Color(0x23, 0x24, 0x28));
                    card.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(0x35, 0x37, 0x3C), 1),
                            BorderFactory.createEmptyBorder(8, 8, 8, 8)
                    ));
                    card.repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (onStickerSelected != null) {
                        onStickerSelected.accept(s.name());
                    }
                    setVisible(false);
                }
            });

            grid.add(card);
        }

        JPanel anchor = new JPanel(new BorderLayout());
        anchor.setBackground(DiscordTheme.SIDEBAR);
        anchor.add(grid, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(anchor);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        return scroll;
    }

    public void showAbove(JComponent invoker) {
        if (invoker != null && invoker.isShowing()) {
            try {
                show(invoker, 0, -365);
            } catch (Exception ignored) {}
        }
    }
}

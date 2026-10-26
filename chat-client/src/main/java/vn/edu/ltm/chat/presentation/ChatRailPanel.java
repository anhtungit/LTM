package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.domain.Conversation;
import vn.edu.ltm.chat.theme.DiscordTheme;
import vn.edu.ltm.chat.theme.IconFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Vertical navigation rail panel on the leftmost edge (Discord server/DM icons).
 */
final class ChatRailPanel extends JPanel {
    private final Supplier<List<Conversation>> conversationsSupplier;
    private final Runnable onSelectDm;
    private final Consumer<Conversation> onSelectGroup;
    private final Runnable onCreateGroup;
    private String activeGroupId = "dm";

    ChatRailPanel(Supplier<List<Conversation>> conversationsSupplier,
                  Runnable onSelectDm,
                  Consumer<Conversation> onSelectGroup,
                  Runnable onCreateGroup) {
        this.conversationsSupplier = conversationsSupplier;
        this.onSelectDm = onSelectDm;
        this.onSelectGroup = onSelectGroup;
        this.onCreateGroup = onCreateGroup;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(DiscordTheme.RAIL);
        setBorder(BorderFactory.createEmptyBorder(14, 6, 14, 6));
        setPreferredSize(new Dimension(72, 0));

        rebuild();
    }

    void setActiveGroupId(String activeGroupId) {
        this.activeGroupId = activeGroupId;
        rebuild();
    }

    String getActiveGroupId() {
        return activeGroupId;
    }

    void rebuild() {
        removeAll();

        // 1. Direct Messages Tile
        boolean isDmActive = "dm".equals(activeGroupId);
        RailTile dmTile = new RailTile(null,
                IconFactory.phosphor("chat-circle-dots.svg", 25, Color.WHITE),
                isDmActive, true, 48);
        dmTile.setToolTipText("Tin nhắn trực tiếp");
        dmTile.addActionListener(e -> {
            activeGroupId = "dm";
            if (onSelectDm != null) onSelectDm.run();
            rebuild();
        });
        add(railEntry(dmTile, isDmActive));
        add(Box.createVerticalStrut(10));
        add(railDivider());
        add(Box.createVerticalStrut(10));

        // 2. Dynamic Group Tiles for all group servers
        List<Conversation> convs = conversationsSupplier != null ? conversationsSupplier.get() : List.of();
        for (Conversation conv : convs) {
            if (conv.isGroup()) {
                boolean isActiveGroup = conv.id().equals(activeGroupId);
                RailTile groupTile = new RailTile(conv.initials(), null, isActiveGroup, true, 48);
                groupTile.setToolTipText(conv.displayName() + " · Bấm để vào nhóm");
                groupTile.addActionListener(e -> {
                    activeGroupId = conv.id();
                    if (onSelectGroup != null) onSelectGroup.accept(conv);
                    rebuild();
                });
                add(railEntry(groupTile, isActiveGroup));
                add(Box.createVerticalStrut(8));
            }
        }

        // 3. Add Group Tile (+)
        RailTile addTile = new RailTile(null,
                IconFactory.phosphor("plus.svg", 20, DiscordTheme.SUCCESS), false, false, 48);
        addTile.setToolTipText("Tạo nhóm mới");
        addTile.addActionListener(e -> {
            if (onCreateGroup != null) onCreateGroup.run();
        });
        add(railEntry(addTile, false));

        add(Box.createVerticalGlue());
        revalidate();
        repaint();
    }

    private JComponent railEntry(JComponent tile, boolean active) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(CENTER_ALIGNMENT);
        row.setMaximumSize(new Dimension(60, 48));

        JComponent pill = new JPanel();
        pill.setOpaque(false);
        if (active) {
            RoundedPanel marker = new RoundedPanel(4);
            marker.setBackground(DiscordTheme.TEXT_PRIMARY);
            pill = marker;
        }
        Dimension pillSize = new Dimension(4, 32);
        pill.setPreferredSize(pillSize);
        pill.setMinimumSize(pillSize);
        pill.setMaximumSize(pillSize);
        row.add(pill);
        row.add(Box.createHorizontalStrut(8));
        row.add(tile);
        return row;
    }

    private JComponent railDivider() {
        JPanel divider = new JPanel();
        divider.setBackground(new Color(0x34, 0x36, 0x3B));
        divider.setAlignmentX(CENTER_ALIGNMENT);
        divider.setPreferredSize(new Dimension(32, 1));
        divider.setMaximumSize(new Dimension(32, 1));
        divider.setMinimumSize(new Dimension(32, 1));
        return divider;
    }
}

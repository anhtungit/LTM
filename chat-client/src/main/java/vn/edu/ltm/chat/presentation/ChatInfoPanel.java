package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.domain.Conversation;
import vn.edu.ltm.chat.theme.DiscordTheme;
import vn.edu.ltm.chat.theme.IconFactory;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Sidebar information panel displaying details about the active conversation,
 * such as group members, channels, user profile and online presence.
 */
final class ChatInfoPanel extends JPanel {
    static final int PANEL_WIDTH = 220;

    private final Function<String, String> displayNameResolver;
    private final Predicate<String> onlineResolver;

    ChatInfoPanel(Function<String, String> displayNameResolver, Predicate<String> onlineResolver) {
        this.displayNameResolver = Objects.requireNonNull(displayNameResolver);
        this.onlineResolver = Objects.requireNonNull(onlineResolver);

        setBackground(DiscordTheme.SIDEBAR);
        setLayout(new GridBagLayout());
        setPreferredSize(new Dimension(PANEL_WIDTH, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, DiscordTheme.HAIRLINE),
                BorderFactory.createEmptyBorder(18, 14, 14, 14)));
    }

    void update(Conversation conversation, String selectedChannel) {
        removeAll();
        if (conversation == null) {
            revalidate();
            repaint();
            return;
        }

        int innerWidth = PANEL_WIDTH - 28;
        int row = 0;

        if (conversation.isGroup()) {
            JLabel section = new JLabel("THÀNH VIÊN — " + conversation.members().size());
            section.setFont(DiscordTheme.extrabold(10f));
            section.setForeground(DiscordTheme.TEXT_SECONDARY);
            row = addInfoRow(section, row, 12);

            for (String member : conversation.members()) {
                JPanel memberRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
                memberRow.setOpaque(false);
                String displayName = displayNameResolver.apply(member);
                boolean isOnline = onlineResolver.test(member);
                String initials = computeInitials(displayName);
                Color presence = isOnline ? DiscordTheme.SUCCESS : null;
                memberRow.add(new AvatarBadge(initials, member, 26, presence, DiscordTheme.SIDEBAR, true));

                JLabel mName = new JLabel(displayName);
                mName.setFont(DiscordTheme.semibold(12f));
                mName.setForeground(DiscordTheme.TEXT_PRIMARY);
                memberRow.add(mName);

                row = addInfoRow(memberRow, row, 8);
            }

            JPanel line = new JPanel();
            line.setBackground(new Color(0x33, 0x35, 0x3B));
            line.setPreferredSize(new Dimension(innerWidth, 1));
            row = addInfoRow(line, row, 12);

            JLabel chanTitle = new JLabel("THÔNG TIN KÊNH");
            chanTitle.setFont(DiscordTheme.extrabold(10f));
            chanTitle.setForeground(DiscordTheme.TEXT_SECONDARY);
            row = addInfoRow(chanTitle, row, 6);

            JLabel hashName = new JLabel("#" + selectedChannel);
            hashName.setFont(DiscordTheme.bold(12f));
            hashName.setForeground(Color.WHITE);
            row = addInfoRow(hashName, row, 4);

            WrappedTextArea about = new WrappedTextArea(conversation.about(), innerWidth);
            about.setFont(DiscordTheme.font(11f));
            about.setForeground(new Color(0xC6, 0xC7, 0xCD));
            row = addInfoRow(about, row, 0);

        } else {
            JLabel section = new JLabel("THÔNG TIN");
            section.setFont(DiscordTheme.extrabold(10f));
            section.setForeground(DiscordTheme.TEXT_SECONDARY);
            row = addInfoRow(section, row, 16);

            JPanel identity = new JPanel(new GridBagLayout());
            identity.setOpaque(false);

            boolean isPeerOnline = onlineResolver.test(conversation.id());
            AvatarBadge avatar = new AvatarBadge(conversation.initials(), conversation.id(), 74,
                    isPeerOnline ? DiscordTheme.SUCCESS : null,
                    DiscordTheme.SIDEBAR, true);
            identity.add(avatar, centeredCell(GridBagConstraints.NONE, 0));

            JLabel name = new JLabel(conversation.displayName());
            name.setFont(DiscordTheme.extrabold(15f));
            name.setForeground(Color.WHITE);
            name.setHorizontalAlignment(SwingConstants.CENTER);
            identity.add(name, centeredCell(GridBagConstraints.HORIZONTAL, 1));

            JLabel handle = new JLabel("@" + conversation.id());
            handle.setFont(DiscordTheme.font(11f));
            handle.setForeground(DiscordTheme.TEXT_MUTED);
            handle.setHorizontalAlignment(SwingConstants.CENTER);
            identity.add(handle, centeredCell(GridBagConstraints.HORIZONTAL, 2));

            JLabel presence = new JLabel(isPeerOnline ? "Đang hoạt động" : "Ngoại tuyến");
            presence.setFont(DiscordTheme.semibold(10f));
            presence.setForeground(isPeerOnline ? DiscordTheme.SUCCESS : DiscordTheme.TEXT_MUTED);
            presence.setHorizontalAlignment(SwingConstants.CENTER);
            padRight(presence);
            identity.add(presence, centeredCell(GridBagConstraints.HORIZONTAL, 3));
            row = addInfoRow(identity, row, 14);

            JPanel line = new JPanel();
            line.setBackground(new Color(0x33, 0x35, 0x3B));
            line.setPreferredSize(new Dimension(innerWidth, 1));
            row = addInfoRow(line, row, 12);

            JLabel aboutTitle = new JLabel("GIỚI THIỆU");
            aboutTitle.setFont(DiscordTheme.extrabold(10f));
            aboutTitle.setForeground(DiscordTheme.TEXT_SECONDARY);
            row = addInfoRow(aboutTitle, row, 6);

            WrappedTextArea about = new WrappedTextArea(conversation.about(), innerWidth);
            about.setFont(DiscordTheme.font(11f));
            about.setForeground(new Color(0xC6, 0xC7, 0xCD));
            row = addInfoRow(about, row, 0);
        }

        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        GridBagConstraints spacerConstraints = new GridBagConstraints();
        spacerConstraints.gridx = 0;
        spacerConstraints.gridy = row;
        spacerConstraints.weightx = 1;
        spacerConstraints.weighty = 1;
        spacerConstraints.fill = GridBagConstraints.BOTH;
        add(spacer, spacerConstraints);

        RoundedPanel note = new RoundedPanel(6);
        note.setBackground(DiscordTheme.SUNKEN);
        note.setLayout(new BorderLayout(6, 0));
        note.setBorder(BorderFactory.createEmptyBorder(8, 9, 8, 9));
        JLabel lock = new JLabel(IconFactory.phosphor("info.svg", 13, DiscordTheme.TEXT_MUTED));
        lock.setVerticalAlignment(SwingConstants.TOP);
        note.add(lock, BorderLayout.WEST);
        WrappedTextArea noteText = new WrappedTextArea("Dữ liệu minh hoạ theo mẫu canvas Kombai.",
                innerWidth - 30);
        noteText.setFont(DiscordTheme.font(10f));
        noteText.setForeground(DiscordTheme.TEXT_MUTED);
        note.add(noteText, BorderLayout.CENTER);
        addInfoRow(note, row + 1, 0);

        revalidate();
        repaint();
    }

    void clear() {
        removeAll();
        revalidate();
        repaint();
    }

    private static String computeInitials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    private GridBagConstraints centeredCell(int fill, int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 1;
        constraints.fill = fill;
        constraints.anchor = GridBagConstraints.NORTH;
        int topGap = switch (row) {
            case 1 -> 10;
            case 2 -> 2;
            case 3 -> 8;
            default -> 0;
        };
        constraints.insets = new Insets(topGap, 0, 0, 0);
        return constraints;
    }

    private int addInfoRow(JComponent component, int row, int bottomGap) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(0, 0, bottomGap, 0);
        add(component, constraints);
        return row + 1;
    }

    private void padRight(JLabel label) {
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
    }
}

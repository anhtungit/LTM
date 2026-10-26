package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.theme.DiscordTheme;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Centered date divider line between messages sent on different days (Discord style).
 */
final class DateDividerRow extends JPanel {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("d 'tháng' M, yyyy", Locale.forLanguageTag("vi-VN"));

    DateDividerRow(LocalDate date) {
        setOpaque(false);
        setLayout(new GridBagLayout());
        setAlignmentX(CENTER_ALIGNMENT);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        setBorder(BorderFactory.createEmptyBorder(14, 0, 10, 0));

        String labelText;
        LocalDate today = LocalDate.now();
        if (date.equals(today)) {
            labelText = "Hôm nay";
        } else if (date.equals(today.minusDays(1))) {
            labelText = "Hôm qua";
        } else {
            labelText = FORMATTER.format(date);
        }

        JPanel leftLine = createLine();
        JPanel rightLine = createLine();

        JLabel badge = new JLabel(labelText);
        badge.setFont(DiscordTheme.bold(11.5f));
        badge.setForeground(DiscordTheme.TEXT_MUTED);
        badge.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        c.gridy = 0;

        c.gridx = 0;
        add(leftLine, c);

        c.fill = GridBagConstraints.NONE;
        c.weightx = 0.0;
        c.gridx = 1;
        add(badge, c);

        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        c.gridx = 2;
        add(rightLine, c);
    }

    private static JPanel createLine() {
        JPanel line = new JPanel();
        line.setBackground(new Color(0x4E, 0x50, 0x58));
        line.setPreferredSize(new Dimension(10, 1));
        line.setMinimumSize(new Dimension(10, 1));
        return line;
    }
}

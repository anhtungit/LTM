package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.theme.DiscordTheme;

import javax.swing.Icon;
import javax.swing.JButton;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Interactive square/circle tile button used in the left rail. */
final class RailTile extends JButton {
    private final String label;
    private final Icon icon;
    private boolean active;
    private final boolean rounded;
    private final int size;

    RailTile(String label, Icon icon, boolean active, boolean rounded, int size) {
        this.label = label;
        this.icon = icon;
        this.active = active;
        this.rounded = rounded;
        this.size = size;
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(size, size));
        setMinimumSize(new Dimension(size, size));
        setMaximumSize(new Dimension(size, size));
        if (label != null) {
            setToolTipText(label);
        }
    }

    public void setActive(boolean active) {
        this.active = active;
        repaint();
    }

    public boolean isActive() {
        return active;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(active ? DiscordTheme.ACCENT : (getModel().isRollover() ? DiscordTheme.SELECTED : DiscordTheme.TILE_IDLE));
        if (rounded) {
            g.fillRoundRect(0, 0, size, size, size / 3, size / 3);
        } else {
            g.fillOval(0, 0, size, size);
        }

        if (icon != null) {
            int iconSize = icon.getIconWidth();
            icon.paintIcon(this, g, (size - iconSize) / 2, (size - icon.getIconHeight()) / 2);
        } else if (label != null) {
            g.setFont(DiscordTheme.extrabold(13f));
            g.setColor(active ? java.awt.Color.WHITE : DiscordTheme.TEXT_SECONDARY);
            FontMetrics metrics = g.getFontMetrics();
            int x = (size - metrics.stringWidth(label)) / 2;
            int y = (size - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString(label, x, y);
        }
        g.dispose();
    }
}

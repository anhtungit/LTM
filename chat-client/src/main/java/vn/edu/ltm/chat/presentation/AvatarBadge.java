package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.theme.DiscordTheme;

import javax.accessibility.AccessibleContext;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.io.File;

/** Round initials or image avatar, optionally with presence dot. */
final class AvatarBadge extends JComponent {
    private static final Color[][] PALETTE = {
            {new Color(0xD2, 0x8B, 0x82), new Color(0x28, 0x1B, 0x1A)},
            {new Color(0x89, 0xA9, 0xD0), new Color(0x15, 0x21, 0x31)},
            {new Color(0xA5, 0xB8, 0x7F), new Color(0x20, 0x27, 0x19)},
            {new Color(0xC4, 0x9E, 0xC8), new Color(0x29, 0x1D, 0x2B)},
            {new Color(0x5E, 0x68, 0xF7), Color.WHITE}
    };

    private final String initials;
    private final String imagePath;
    private Image cachedImage;
    private final Color background;
    private final Color foreground;
    private final int size;
    private final Color presence;
    private final Color presenceRing;
    private final boolean presenceRingVisible;

    AvatarBadge(String initials, String seed, int size) {
        this(initials, seed, size, null, DiscordTheme.SIDEBAR, false, null);
    }

    AvatarBadge(String initials, String seed, int size, Color presence, Color presenceRing, boolean drawRing) {
        this(initials, seed, size, presence, presenceRing, drawRing, null);
    }

    AvatarBadge(String initials, String seed, int size, Color presence, Color presenceRing, boolean drawRing, String imagePath) {
        this.initials = initials == null || initials.isBlank() ? "?" : initials;
        this.imagePath = imagePath;
        int index = Math.floorMod(seed == null ? this.initials.hashCode() : seed.hashCode(), PALETTE.length);
        this.background = PALETTE[index][0];
        this.foreground = PALETTE[index][1];
        this.size = DiscordTheme.scale(size);
        this.presence = presence;
        this.presenceRing = presenceRing;
        this.presenceRingVisible = drawRing;
        setOpaque(false);
        setPreferredSize(new Dimension(this.size, this.size));
        setMinimumSize(new Dimension(this.size, this.size));
        setMaximumSize(new Dimension(this.size, this.size));
        getAccessibleContext().setAccessibleName("Ảnh đại diện " + this.initials);
    }

    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleAvatarBadge();
        }
        return accessibleContext;
    }

    protected class AccessibleAvatarBadge extends AccessibleJComponent {
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (imagePath != null && !imagePath.isBlank() && cachedImage == null) {
            try {
                File file = new File(imagePath);
                if (file.exists()) {
                    cachedImage = ImageIO.read(file);
                }
            } catch (Exception ignored) {
            }
        }

        if (cachedImage != null) {
            Shape oldClip = g.getClip();
            g.setClip(new Ellipse2D.Float(0, 0, size - 1, size - 1));
            g.drawImage(cachedImage, 0, 0, size, size, null);
            g.setClip(oldClip);
        } else {
            g.setColor(background);
            g.fillOval(0, 0, size - 1, size - 1);

            g.setFont(DiscordTheme.extrabold(Math.max(9f, size * 0.36f)));
            g.setColor(foreground);
            FontMetrics metrics = g.getFontMetrics();
            int x = (size - metrics.stringWidth(initials)) / 2;
            int y = (size - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString(initials, x, y);
        }

        if (presence != null) {
            int dot = Math.max(9, Math.round(size * 0.3f));
            int inset = Math.max(1, size / 24);
            int x0 = size - dot - inset;
            int y0 = size - dot - inset;
            if (presenceRingVisible && presenceRing != null) {
                int ring = Math.max(2, size / 14);
                g.setColor(presenceRing);
                g.fillOval(x0 - ring, y0 - ring, dot + ring * 2, dot + ring * 2);
            }
            g.setColor(presence);
            g.fillOval(x0, y0, dot, dot);
        }
        g.dispose();
    }
}

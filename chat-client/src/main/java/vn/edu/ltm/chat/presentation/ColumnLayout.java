package vn.edu.ltm.chat.presentation;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager2;

/**
 * Vertical column that caps its content at a maximum width and centres it horizontally.
 *
 * <p>Before this existed, the transcript and composer used {@link javax.swing.BoxLayout}, which
 * only ever gives children their preferred width, so the message column and composer collapsed to
 * a narrow strip and the composer shrank instead of filling the transcript width.</p>
 */
final class ColumnLayout implements LayoutManager2 {
    private final int maxWidth;
    private final int sideGap;

    ColumnLayout(int maxWidth, int sideGap) {
        this.maxWidth = maxWidth;
        this.sideGap = sideGap;
    }

    @Override
    public void addLayoutComponent(Component component, Object constraints) {
        // Children are laid out in the order they were added.
    }

    @Override
    public void addLayoutComponent(String name, Component component) {
        // Children are laid out in the order they were added.
    }

    @Override
    public void removeLayoutComponent(Component component) {
        // Nothing to release.
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        int width = contentWidth(parent);
        int wrapWidth = wrapWidth(width);
        int height = 0;
        for (Component child : parent.getComponents()) {
            if (child instanceof MessageRow row) {
                row.updateWrapWidth(wrapWidth);
            }
            height += child.getPreferredSize().height;
        }
        Insets insets = parent.getInsets();
        return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        Insets insets = parent.getInsets();
        return new Dimension(240 + insets.left + insets.right, insets.top + insets.bottom);
    }

    @Override
    public Dimension maximumLayoutSize(Container parent) {
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public float getLayoutAlignmentX(Container parent) {
        return 0.5f;
    }

    @Override
    public float getLayoutAlignmentY(Container parent) {
        return 0f;
    }

    @Override
    public void invalidateLayout(Container parent) {
        // Measurements are computed on demand.
    }

    @Override
    public void layoutContainer(Container parent) {
        Insets insets = parent.getInsets();
        int available = parent.getWidth() - insets.left - insets.right;
        int width = Math.max(240, Math.min(maxWidth, available));
        int x = insets.left + Math.max(0, (available - width) / 2);
        int y = insets.top;
        int wrapWidth = wrapWidth(width);

        for (Component child : parent.getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            if (child instanceof MessageRow row) {
                row.updateWrapWidth(wrapWidth);
            }
            int height = child.getPreferredSize().height;
            child.setBounds(x, y, width, height);
            y += height;
        }
    }

    private int contentWidth(Container parent) {
        Insets insets = parent.getInsets();
        int available = parent.getWidth() - insets.left - insets.right;
        if (available <= 0) {
            return maxWidth;
        }
        return Math.max(240, Math.min(maxWidth, available));
    }

    private int wrapWidth(int width) {
        return Math.max(160, width - sideGap);
    }
}

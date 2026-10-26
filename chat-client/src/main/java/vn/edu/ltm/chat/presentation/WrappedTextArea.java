package vn.edu.ltm.chat.presentation;

import javax.swing.JTextArea;
import javax.swing.text.View;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;

/**
 * Read-only text block whose wrap width is supplied by its owner instead of by the current
 * component size.
 */
final class WrappedTextArea extends JTextArea {
    private int wrapWidth;
    private boolean isPainting = false;

    WrappedTextArea(String text, int wrapWidth) {
        super(text);
        this.wrapWidth = wrapWidth;
        setEditable(false);
        setFocusable(true);
        setLineWrap(true);
        setWrapStyleWord(true);
        setOpaque(false);
        setBorder(null);
        setMargin(new java.awt.Insets(0, 0, 0, 0));
    }

    void setWrapWidth(int wrapWidth) {
        if (this.wrapWidth == wrapWidth) {
            return;
        }
        this.wrapWidth = wrapWidth;
    }

    @Override
    public void paint(Graphics g) {
        isPainting = true;
        try {
            super.paint(g);
        } finally {
            isPainting = false;
        }
    }

    @Override
    public void revalidate() {
        if (isPainting) {
            return;
        }
        super.revalidate();
    }

    @Override
    public Dimension getPreferredSize() {
        String text = getText();
        if (text == null || text.isEmpty()) {
            return new Dimension(0, 0);
        }
        float height = 0;
        if (getUI() != null) {
            View view = getUI().getRootView(this);
            if (view != null) {
                view.setSize((float) wrapWidth, (float) Short.MAX_VALUE);
                height = view.getPreferredSpan(View.Y_AXIS);
            }
        }
        if (height <= 0) {
            height = super.getPreferredSize().height;
        }
        FontMetrics fm = getFontMetrics(getFont());
        String[] lines = text.split("\\r?\\n");
        int maxLineW = 0;
        for (String l : lines) {
            maxLineW = Math.max(maxLineW, fm.stringWidth(l));
        }
        int actualWidth = Math.min(wrapWidth, Math.max(24, maxLineW + 8));
        if (maxLineW < wrapWidth) {
            return new Dimension(actualWidth, (int) Math.ceil(height));
        }
        return new Dimension(wrapWidth, (int) Math.ceil(height));
    }
}

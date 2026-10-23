package vn.edu.ltm.chat.presentation;

import javax.swing.JTextArea;
import java.awt.Dimension;

/**
 * Read-only text block whose wrap width is supplied by its owner instead of by the current
 * component size. Swing asks for a preferred size before it assigns widths, so measuring from
 * {@code getWidth()} made message bubbles wrap incorrectly after the first layout pass.
 */
final class WrappedTextArea extends JTextArea {
    private int wrapWidth;

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
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        setSize(new Dimension(wrapWidth, Short.MAX_VALUE));
        Dimension measured = super.getPreferredSize();
        return new Dimension(wrapWidth, measured.height);
    }
}

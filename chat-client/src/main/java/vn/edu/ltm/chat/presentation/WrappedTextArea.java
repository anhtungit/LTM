package vn.edu.ltm.chat.presentation;

import javax.swing.JTextArea;
import java.awt.Dimension;
import java.awt.FontMetrics;

/**
 * Read-only text block whose wrap width is supplied by its owner instead of by the current
 * component size.
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
        setSize(new Dimension(wrapWidth, Short.MAX_VALUE));
    }

    @Override
    public Dimension getPreferredSize() {
        if (getWidth() != wrapWidth) {
            setSize(new Dimension(wrapWidth, Short.MAX_VALUE));
        }
        Dimension measured = super.getPreferredSize();
        String text = getText();
        if (text != null && !text.isEmpty()) {
            FontMetrics fm = getFontMetrics(getFont());
            String[] lines = text.split("\\r?\\n");
            int maxLineW = 0;
            for (String l : lines) {
                maxLineW = Math.max(maxLineW, fm.stringWidth(l));
            }
            int actualWidth = Math.min(wrapWidth, Math.max(24, maxLineW + 8));
            if (maxLineW < wrapWidth) {
                return new Dimension(actualWidth, measured.height);
            }
        }
        return new Dimension(wrapWidth, measured.height);
    }
}

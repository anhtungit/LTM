package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.theme.DiscordTheme;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.DocumentFilter;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/** Text composer with real inline Twemoji icons and smart Enter/Shift+Enter key bindings. */
public final class RichComposerPane extends JTextPane {
    private final Runnable onSendAction;

    public RichComposerPane(Runnable onSendAction) {
        this.onSendAction = onSendAction;

        setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        setBackground(DiscordTheme.COMPOSER);
        setForeground(new Color(0xD3, 0xD4, 0xD8));
        setCaretColor(DiscordTheme.TEXT_PRIMARY);
        setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        setToolTipText("Enter để gửi · Shift + Enter để xuống dòng");

        // Set custom document filter to automatically convert typed/pasted emojis to inline icons
        DefaultStyledDocument doc = new DefaultStyledDocument();
        doc.setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                    throws BadLocationException {
                if (string != null && EmojiAssets.containsEmoji(string)) {
                    insertWithEmojis(fb, offset, string, cleanTextAttrs(attr));
                } else {
                    super.insertString(fb, offset, string, cleanTextAttrs(attr));
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                    throws BadLocationException {
                if (text != null && EmojiAssets.containsEmoji(text)) {
                    fb.remove(offset, length);
                    insertWithEmojis(fb, offset, text, cleanTextAttrs(attrs));
                } else {
                    super.replace(fb, offset, length, text, cleanTextAttrs(attrs));
                }
            }
        });
        setStyledDocument(doc);

        setupKeyBindings();
    }

    private AttributeSet cleanTextAttrs(AttributeSet attr) {
        SimpleAttributeSet clean = attr != null ? new SimpleAttributeSet(attr) : new SimpleAttributeSet();
        clean.removeAttribute("emoji");
        clean.removeAttribute(StyleConstants.IconAttribute);
        return clean;
    }

    private void insertWithEmojis(DocumentFilter.FilterBypass fb, int offset, String text, AttributeSet attr)
            throws BadLocationException {
        int cursor = 0;
        int len = text.length();
        while (cursor < len) {
            boolean matched = false;
            // Check for emoji match at current cursor
            for (String emo : EmojiData.EMOTION.stream().map(EmojiData.EmojiItem::emoji).toList()) {
                if (text.startsWith(emo, cursor)) {
                    ImageIcon icon = EmojiAssets.getIcon(emo, 20);
                    if (icon != null) {
                        SimpleAttributeSet emoAttrs = new SimpleAttributeSet();
                        StyleConstants.setIcon(emoAttrs, icon);
                        emoAttrs.addAttribute("emoji", emo);
                        fb.insertString(offset, " ", emoAttrs);
                        offset += 1;
                        cursor += emo.length();
                        matched = true;
                        break;
                    }
                }
            }
            if (!matched) {
                fb.insertString(offset, text.substring(cursor, cursor + 1), cleanTextAttrs(attr));
                offset += 1;
                cursor += 1;
            }
        }
    }

    private void setupKeyBindings() {
        // Enter -> Send
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "send-message");
        getActionMap().put("send-message", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (onSendAction != null) {
                    onSendAction.run();
                }
            }
        });

        // Shift + Enter -> New line
        getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), "insert-break");
        getActionMap().put("insert-break", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    getStyledDocument().insertString(getCaretPosition(), "\n", null);
                } catch (BadLocationException ignored) {
                }
            }
        });
    }

    public void insertEmoji(String emoji) {
        ImageIcon icon = EmojiAssets.getIcon(emoji, 20);
        if (icon != null) {
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setIcon(attrs, icon);
            attrs.addAttribute("emoji", emoji);
            try {
                int pos = getCaretPosition();
                getStyledDocument().insertString(pos, " ", attrs);
                setCaretPosition(pos + 1);
            } catch (BadLocationException ignored) {
            }
        } else {
            replaceSelection(emoji);
        }
        // Critical: Reset input attributes so newly typed characters never inherit the emoji icon or attribute!
        javax.swing.text.MutableAttributeSet inputAttrs = getInputAttributes();
        if (inputAttrs != null) {
            inputAttrs.removeAttributes(inputAttrs);
        }
        requestFocusInWindow();
    }

    public String getMessageText() {
        StyledDocument doc = getStyledDocument();
        StringBuilder sb = new StringBuilder();
        try {
            for (int i = 0; i < doc.getLength(); i++) {
                Element el = doc.getCharacterElement(i);
                AttributeSet as = el.getAttributes();
                Object icon = as.getAttribute(StyleConstants.IconAttribute);
                Object emo = as.getAttribute("emoji");
                if (icon != null && emo != null) {
                    sb.append(emo);
                } else {
                    sb.append(doc.getText(i, 1));
                }
            }
        } catch (BadLocationException ignored) {
        }
        return sb.toString();
    }

    public void setMessageText(String text) {
        clear();
        if (text != null && !text.isEmpty()) {
            try {
                getStyledDocument().insertString(0, text, null);
            } catch (BadLocationException ignored) {
            }
        }
    }

    public void clear() {
        setText("");
        javax.swing.text.MutableAttributeSet inputAttrs = getInputAttributes();
        if (inputAttrs != null) {
            inputAttrs.removeAttributes(inputAttrs);
        }
    }

    public boolean isMessageEmpty() {
        return getMessageText().trim().isEmpty();
    }
}


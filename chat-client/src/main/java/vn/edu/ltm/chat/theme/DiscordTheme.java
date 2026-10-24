package vn.edu.ltm.chat.theme;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;

/**
 * Design tokens for the Discord-like desktop shell.
 *
 * <p>Type is rendered from four static Nunito Sans faces (400/600/700/800). The previous
 * variable font file only ever produced its ExtraLight default instance under the JDK,
 * which made every label thinner than the approved design.</p>
 */
public final class DiscordTheme {
    public static final Color RAIL = new Color(0x15, 0x16, 0x19);
    public static final Color SIDEBAR = new Color(0x1E, 0x1F, 0x23);
    public static final Color TRANSCRIPT = new Color(0x27, 0x29, 0x2D);
    public static final Color COMPOSER = new Color(0x31, 0x33, 0x38);
    public static final Color SELECTED = new Color(0x3B, 0x3D, 0x44);
    public static final Color HOVER = new Color(0x27, 0x28, 0x2D);
    public static final Color BANNER = new Color(0x24, 0x26, 0x2A);
    public static final Color SUNKEN = new Color(0x17, 0x18, 0x1B);
    public static final Color ACCOUNT_BAR = new Color(0x19, 0x1A, 0x1E);
    public static final Color TILE_IDLE = new Color(0x24, 0x26, 0x2B);
    public static final Color TEXT_PRIMARY = new Color(0xEA, 0xEB, 0xEE);
    public static final Color TEXT_SECONDARY = new Color(0xAF, 0xB1, 0xB6);
    public static final Color TEXT_MUTED = new Color(0x89, 0x8C, 0x94);
    public static final Color ACCENT = new Color(0x5E, 0x68, 0xF7);
    public static final Color BLURPLE = ACCENT;
    public static final Color ACCENT_HOVER = new Color(0x50, 0x59, 0xE8);
    public static final Color ACCENT_SOFT = new Color(0x8F, 0x98, 0xFF);
    public static final Color SUCCESS = new Color(0x43, 0xC0, 0x7A);
    public static final Color WARNING = new Color(0xF0, 0xBB, 0x3B);
    public static final Color DANGER = new Color(0xF5, 0x47, 0x48);
    public static final Color SEPARATOR = new Color(0x3B, 0x3D, 0x44);
    public static final Color HAIRLINE = new Color(0x20, 0x21, 0x25);

    public static final Font FONT = loadFace("NunitoSans-Regular.ttf");
    private static final Font SEMIBOLD = loadFace("NunitoSans-SemiBold.ttf");
    private static final Font BOLD = loadFace("NunitoSans-Bold.ttf");
    private static final Font EXTRABOLD = loadFace("NunitoSans-ExtraBold.ttf");

    private DiscordTheme() {
    }

    public static int scale(int value) {
        return com.formdev.flatlaf.util.UIScale.scale(value);
    }

    public static float scale(float value) {
        return com.formdev.flatlaf.util.UIScale.scale(value);
    }

    public static Font font(float size) {
        return FONT.deriveFont(scale(size));
    }

    public static Font semibold(float size) {
        return SEMIBOLD.deriveFont(scale(size));
    }

    public static Font bold(float size) {
        return BOLD.deriveFont(scale(size));
    }

    public static Font extrabold(float size) {
        return EXTRABOLD.deriveFont(scale(size));
    }

    public static void install() {
        try {
            FlatDarkLaf.setup();
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể cài giao diện FlatLaf tối.", exception);
        }

        UIManager.put("defaultFont", font(14f));
        UIManager.put("Button.arc", 8);
        UIManager.put("Component.arc", 8);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.trackArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("Component.focusColor", ACCENT);
        UIManager.put("Component.borderColor", SEPARATOR);
        UIManager.put("Panel.background", TRANSCRIPT);
        UIManager.put("TextField.background", COMPOSER);
        UIManager.put("PasswordField.background", COMPOSER);
        UIManager.put("TextArea.background", COMPOSER);
        UIManager.put("Label.foreground", TEXT_PRIMARY);
        UIManager.put("ToolTip.background", SUNKEN);
        UIManager.put("ToolTip.foreground", TEXT_PRIMARY);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(SEPARATOR));
    }

    public static void styleField(JComponent component) {
        component.putClientProperty(FlatClientProperties.STYLE,
                "arc: 8; background: #313338; borderColor: #3B3D44; focusColor: #5E68F7; " +
                        "margin: 0,12,0,12;");
        component.setFont(font(14f));
        component.setForeground(TEXT_PRIMARY);
    }

    public static void stylePrimaryButton(JComponent component) {
        component.putClientProperty(FlatClientProperties.STYLE,
                "arc: 8; background: #5E68F7; foreground: #FFFFFF; focusColor: #8F98FF; " +
                        "borderWidth: 0; hoverBackground: #5059E8;");
        component.setFont(bold(14f));
    }

    public static void styleSecondaryButton(JComponent component) {
        component.putClientProperty(FlatClientProperties.STYLE,
                "arc: 8; background: #1E1F23; foreground: #EAEBEE; borderColor: #3B3D44; " +
                        "focusColor: #5E68F7; hoverBackground: #27292D;");
        component.setFont(semibold(14f));
    }

    private static Font loadFace(String fileName) {
        try (InputStream input = DiscordTheme.class.getResourceAsStream("/fonts/" + fileName)) {
            if (input != null) {
                Font face = Font.createFont(Font.TRUETYPE_FONT, input);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(face);
                return face;
            }
        } catch (Exception ignored) {
            // Fall through to the system font so the app still starts with readable text.
        }
        return new Font(Font.DIALOG, Font.PLAIN, 14);
    }
}

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
    public static final Color RAIL = new Color(0x1E, 0x1F, 0x22);
    public static final Color SIDEBAR = new Color(0x2B, 0x2D, 0x31);
    public static final Color TRANSCRIPT = new Color(0x31, 0x33, 0x38);
    public static final Color COMPOSER = new Color(0x38, 0x3A, 0x40);
    public static final Color SELECTED = new Color(0x35, 0x37, 0x3C);
    public static final Color HOVER = new Color(0x35, 0x37, 0x3C);
    public static final Color BANNER = new Color(0x2B, 0x2D, 0x31);
    public static final Color SUNKEN = new Color(0x1E, 0x1F, 0x22);
    public static final Color ACCOUNT_BAR = new Color(0x23, 0x24, 0x28);
    public static final Color TILE_IDLE = new Color(0x31, 0x33, 0x38);
    public static final Color TEXT_PRIMARY = new Color(0xF2, 0xF3, 0xF5);
    public static final Color TEXT_SECONDARY = new Color(0x94, 0x9B, 0xA4);
    public static final Color TEXT_MUTED = new Color(0x80, 0x84, 0x8E);
    public static final Color ACCENT = new Color(0x58, 0x65, 0xF2);
    public static final Color BLURPLE = ACCENT;
    public static final Color ACCENT_HOVER = new Color(0x47, 0x52, 0xC4);
    public static final Color ACCENT_SOFT = new Color(0x58, 0x65, 0xF2, 180);
    public static final Color SUCCESS = new Color(0x23, 0xA5, 0x5A);
    public static final Color WARNING = new Color(0xF0, 0xB2, 0x32);
    public static final Color DANGER = new Color(0xF2, 0x3F, 0x43);
    public static final Color SEPARATOR = new Color(0x35, 0x36, 0x3C);
    public static final Color HAIRLINE = new Color(0x1F, 0x20, 0x23);

    public static final Font FONT = loadFace("NunitoSans-Regular.ttf");
    private static final Font SEMIBOLD = loadFace("NunitoSans-SemiBold.ttf");
    private static final Font BOLD = loadFace("NunitoSans-Bold.ttf");
    private static final Font EXTRABOLD = loadFace("NunitoSans-ExtraBold.ttf");

    private DiscordTheme() {
    }

    public static Font font(float size) {
        return FONT.deriveFont(size);
    }

    public static Font semibold(float size) {
        return SEMIBOLD.deriveFont(size);
    }

    public static Font bold(float size) {
        return BOLD.deriveFont(size);
    }

    public static Font extrabold(float size) {
        return EXTRABOLD.deriveFont(size);
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

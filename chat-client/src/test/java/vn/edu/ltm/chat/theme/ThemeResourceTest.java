package vn.edu.ltm.chat.theme;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeResourceTest {
    @Test
    void loadsBundledVietnameseFont() {
        assertTrue(DiscordTheme.FONT.getName().contains("Nunito Sans"));
    }

    @Test
    void loadsBundledPhosphorSvgIcon() {
        FlatSVGIcon icon = (FlatSVGIcon) IconFactory.phosphor("chat-circle-dots.svg", 20, Color.WHITE);

        assertTrue(icon.hasFound());
        assertEquals(20, icon.getIconWidth());
        assertEquals(20, icon.getIconHeight());
    }
}

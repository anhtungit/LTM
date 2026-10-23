package vn.edu.ltm.chat.theme;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.Icon;
import java.awt.Color;

public final class IconFactory {
    private IconFactory() {
    }

    public static Icon phosphor(String fileName, int size, Color color) {
        FlatSVGIcon icon = new FlatSVGIcon("icons/" + fileName, size, size);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(ignored -> color));
        return icon;
    }
}

package vn.edu.ltm.chat.app;

import vn.edu.ltm.chat.theme.DiscordTheme;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.Locale;

public final class AppMain {
    private AppMain() {
    }

    public static void main(String[] args) {
        initHiDpiScaling();
        DiscordTheme.install();
        UIManager.put("TitlePane.unifiedBackground", true);
        SwingUtilities.invokeLater(() -> new AppController().start());
    }

    private static void initHiDpiScaling() {
        if (System.getProperty("flatlaf.uiScale") != null || System.getProperty("sun.java2d.uiScale") != null) {
            return;
        }

        String envScale = System.getenv("CHAT_UI_SCALE");
        if (envScale == null || envScale.isBlank()) {
            envScale = System.getenv("GDK_SCALE");
        }
        if (envScale != null && !envScale.isBlank()) {
            try {
                double s = Double.parseDouble(envScale.trim());
                if (s >= 0.5 && s <= 4.0) {
                    System.setProperty("flatlaf.uiScale", String.format(Locale.US, "%.2f", s));
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        // Auto-detect from GNOME / X11 desktop properties (e.g. gnome.Xft/DPI)
        try {
            Object dpiProp = Toolkit.getDefaultToolkit().getDesktopProperty("gnome.Xft/DPI");
            if (dpiProp instanceof Number num) {
                // GNOME stores DPI * 1024 (e.g. 96 * 1024 = 98304 for 100%, 192 * 1024 = 196608 for 200%)
                double dpi = num.doubleValue() / 1024.0;
                if (dpi >= 105.0) {
                    double scale = dpi / 96.0;
                    System.setProperty("flatlaf.uiScale", String.format(Locale.US, "%.2f", scale));
                    return;
                }
            }
        } catch (Exception ignored) {
        }

        // Fallback: check screen DPI or dimensions
        try {
            int screenDpi = Toolkit.getDefaultToolkit().getScreenResolution();
            if (screenDpi >= 120) {
                double scale = (double) screenDpi / 96.0;
                System.setProperty("flatlaf.uiScale", String.format(Locale.US, "%.2f", scale));
                return;
            }
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            if (screenSize.height >= 1440 && screenSize.width >= 2560) {
                System.setProperty("flatlaf.uiScale", "1.75");
            }
        } catch (Exception ignored) {
        }
    }
}


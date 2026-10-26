package vn.edu.ltm.chat.app;

import vn.edu.ltm.chat.theme.DiscordTheme;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AppMain {
    private AppMain() {
    }

    public static void main(String[] args) {
        // 1. Explicit CLI scale argument has highest priority (--scale=1.25 or -scale 1.25)
        String cliScale = null;
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (args[i].startsWith("--scale=")) {
                    cliScale = args[i].substring("--scale=".length());
                } else if ((args[i].equals("-scale") || args[i].equals("--scale")) && i + 1 < args.length) {
                    cliScale = args[i + 1];
                }
            }
        }
        if (cliScale != null && !cliScale.isBlank()) {
            System.setProperty("sun.java2d.uiScale", cliScale);
        }

        // 2. Sync scaling to the ACTIVE Windows display setting rather than hardware native (EDID) setting:
        // On monitors (e.g. 4K) where user sets resolution/scale in Windows to another setting (e.g. 1080p, 100%, 125%),
        // Java2D by default queries hardware EDID / GetDpiForMonitor and erroneously applies the native 4K 2.0x+ scale.
        // We detect the user's active configured desktop DPI and sync uiScale to match Windows setting.
        if (System.getProperty("sun.java2d.uiScale") == null
                && System.getProperty("chat.scale.checked") == null) {
            try {
                double activeScale = detectActiveWindowsScale();
                GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
                double hardwareScale = gd.getDefaultConfiguration().getDefaultTransform().getScaleX();

                // If Java2D's hardware scale differs from the active configured Windows setting by > 5%:
                if (Math.abs(hardwareScale - activeScale) > 0.05) {
                    String scaleStr = String.format(Locale.ROOT, "%.2f", activeScale);
                    if (scaleStr.endsWith(".00")) {
                        scaleStr = scaleStr.substring(0, scaleStr.length() - 3);
                    } else if (scaleStr.endsWith("0")) {
                        scaleStr = scaleStr.substring(0, scaleStr.length() - 1);
                    }
                    relaunchWithScale(scaleStr, args);
                    return;
                }
            } catch (Throwable ignored) {
                // Ignore headless or system exceptions and continue normally
            }
        }

        DiscordTheme.install();
        UIManager.put("TitlePane.unifiedBackground", true);
        SwingUtilities.invokeLater(() -> new AppController().start());
    }

    private static double detectActiveWindowsScale() {
        int regDpi = readWindowsAppliedDpi();
        if (regDpi > 0) {
            return regDpi / 96.0;
        }
        try {
            int tkDpi = Toolkit.getDefaultToolkit().getScreenResolution();
            if (tkDpi > 0) {
                return tkDpi / 96.0;
            }
        } catch (Throwable ignored) {
        }
        return 1.0;
    }

    private static int readWindowsAppliedDpi() {
        try {
            Process p = new ProcessBuilder("reg", "query", "HKCU\\Control Panel\\Desktop\\WindowMetrics", "/v", "AppliedDPI")
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.contains("AppliedDPI") && line.contains("REG_DWORD")) {
                        String[] tokens = line.split("\\s+");
                        String hex = tokens[tokens.length - 1];
                        if (hex.startsWith("0x") || hex.startsWith("0X")) {
                            return Integer.parseInt(hex.substring(2), 16);
                        }
                        return Integer.parseInt(hex);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private static void relaunchWithScale(String targetScale, String[] args) {
        try {
            String javaBin = ProcessHandle.current().info().command().orElse("java");
            List<String> cmd = new ArrayList<>();
            cmd.add(javaBin);
            cmd.add("-Dsun.java2d.uiScale=" + targetScale);
            cmd.add("-Dchat.scale.checked=true");
            String cp = System.getProperty("java.class.path");
            if (cp != null && cp.endsWith(".jar") && !cp.contains(File.pathSeparator)) {
                cmd.add("-jar");
                cmd.add(cp);
            } else {
                cmd.add("-cp");
                cmd.add(cp != null ? cp : ".");
                cmd.add(AppMain.class.getName());
            }
            if (args != null) {
                for (String arg : args) {
                    cmd.add(arg);
                }
            }
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.inheritIO();
            Process process = pb.start();
            System.exit(process.waitFor());
        } catch (Exception ignored) {
            // If relaunch fails, continue running in current process
        }
    }
}

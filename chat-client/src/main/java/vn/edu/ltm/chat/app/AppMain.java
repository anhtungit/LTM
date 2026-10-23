package vn.edu.ltm.chat.app;

import vn.edu.ltm.chat.theme.DiscordTheme;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class AppMain {
    private AppMain() {
    }

    public static void main(String[] args) {
        DiscordTheme.install();
        UIManager.put("TitlePane.unifiedBackground", true);
        SwingUtilities.invokeLater(() -> new AppController().start());
    }
}

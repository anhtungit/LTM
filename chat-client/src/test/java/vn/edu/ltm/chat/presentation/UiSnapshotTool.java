package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.application.ChatService;
import vn.edu.ltm.chat.domain.UserProfile;
import vn.edu.ltm.chat.infrastructure.demo.InMemoryChatGateway;
import vn.edu.ltm.chat.theme.DiscordTheme;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.InvocationTargetException;

/**
 * Dev-only helper: renders the real panels offscreen to PNG so layout regressions can be checked
 * without relying on window screenshots.
 *
 * <p>Usage: {@code UiSnapshotTool <output-dir>}.</p>
 */
public final class UiSnapshotTool {
    private static final String DIR = "target/ui-snapshots";

    private UiSnapshotTool() {
    }

    public static void main(String[] args) throws Exception {
        String outputDir = args.length > 0 ? args[0] : DIR;
        File dir = new File(outputDir);
        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new IllegalStateException("Không tạo được thư mục " + outputDir);
        }
        DiscordTheme.install();
        SwingUtilities.invokeAndWait(() -> {
            render(new LoginPanel((email, password) -> { }, message -> { }), 1360, 786,
                    new File(dir, "login-1360x786.png"));
            render(new LoginPanel((email, password) -> { }, message -> { }), 1024, 640,
                    new File(dir, "login-1024x640.png"));
            if (args.length > 1 && "debug".equals(args[1])) {
                dump(new LoginPanel((email, password) -> { }, message -> { }), 1360, 900, 0);
                dump(new ChatWorkspace(
                        new UserProfile("demo-user", "Minh Nguyễn", "minh.nguyen@example.com"),
                        new ChatService(new InMemoryChatGateway()), () -> { }), 1360, 900, 0);
            }

            ChatService chatService = new ChatService(new InMemoryChatGateway());
            UserProfile user = new UserProfile("demo-user", "Minh Nguyễn", "minh.nguyen@example.com");
            render(new ChatWorkspace(user, chatService, () -> { }), 1360, 786,
                    new File(dir, "chat-1360x786.png"));
            render(new ChatWorkspace(user, chatService, () -> { }), 1000, 660,
                    new File(dir, "chat-1000x660.png"));
        });
        System.out.println("Snapshots written to " + dir.getAbsolutePath());
    }

    private static void render(JPanel panel, int width, int height, File target) {
        try {
            JFrame frame = new JFrame();
            frame.setContentPane(panel);
            frame.addNotify();
            for (int pass = 0; pass < 4; pass++) {
                frame.setSize(new Dimension(width, height));
                frame.validate();
                layoutTree(panel);
            }

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            panel.printAll(graphics);
            graphics.dispose();
            ImageIO.write(image, "png", target);
            frame.dispose();
            System.out.println("  " + target.getName());
        } catch (Exception exception) {
            throw new IllegalStateException("Không render được " + target.getName(), exception);
        }
    }

    private static void dump(JPanel panel, int width, int height, int depth) {
        JFrame frame = new JFrame();
        frame.setContentPane(panel);
        frame.setSize(new Dimension(width, height));
        frame.addNotify();
        frame.validate();
        layoutTree(panel);
        printBounds(panel, 0);
        frame.dispose();
    }

    private static void printBounds(Component component, int depth) {
        StringBuilder line = new StringBuilder("  ".repeat(depth));
        line.append(component.getClass().getSimpleName())
                .append(" [").append(component.getX()).append(',').append(component.getY())
                .append(' ').append(component.getWidth()).append('x').append(component.getHeight())
                .append("] pref=").append(component.getPreferredSize().width)
                .append('x').append(component.getPreferredSize().height)
                .append(" max=").append(component.getMaximumSize().width)
                .append('x').append(component.getMaximumSize().height);
        String text = textOf(component);
        if (!text.isEmpty()) {
            line.append(" \"").append(text).append('"');
        }
        System.out.println(line);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                printBounds(child, depth + 1);
            }
        }
    }

    private static String textOf(Component component) {
        String raw = "";
        if (component instanceof javax.swing.JLabel label) {
            raw = label.getText();
        } else if (component instanceof javax.swing.AbstractButton button) {
            raw = button.getText();
        } else if (component instanceof javax.swing.text.JTextComponent textComponent) {
            raw = textComponent.getText();
        }
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replaceAll("<[^>]*>", "").replace("\n", " ").trim();
        return cleaned.length() > 48 ? cleaned.substring(0, 48) + "…" : cleaned;
    }

    private static void layoutTree(Component component) {
        component.doLayout();
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                layoutTree(child);
            }
        }
        if (component instanceof JPanel panel && panel.getLayout() instanceof BorderLayout) {
            panel.revalidate();
        }
    }
}

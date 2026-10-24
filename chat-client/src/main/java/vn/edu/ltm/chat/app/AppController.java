package vn.edu.ltm.chat.app;

import vn.edu.ltm.chat.application.ChatGateway;
import vn.edu.ltm.chat.application.ChatService;
import vn.edu.ltm.chat.application.LoginService;
import vn.edu.ltm.chat.infrastructure.network.TcpChatGateway;
import vn.edu.ltm.chat.domain.UserProfile;
import vn.edu.ltm.chat.presentation.ChatWorkspace;
import vn.edu.ltm.chat.presentation.LoginPanel;
import vn.edu.ltm.chat.theme.DiscordTheme;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.util.Arrays;

public final class AppController {
    private static final String LOGIN_VIEW = "login";
    private static final String CHAT_VIEW = "chat";

    private final ChatGateway gateway = new TcpChatGateway();
    private final LoginService loginService = new LoginService(gateway);
    private final ChatService chatService = new ChatService(gateway);
    private final CardLayout viewLayout = new CardLayout();
    private final JPanel viewHost = new JPanel(viewLayout);
    private final JFrame frame = new JFrame("Chat đa luồng");
    private final LoginPanel loginPanel = new LoginPanel(this::login, this::showUnavailableAction);
    private UserProfile activeUser;
    private ChatWorkspace chatWorkspace;

    public void start() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Rectangle usableBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int minW = DiscordTheme.scale(980);
        int minH = DiscordTheme.scale(620);
        int prefW = DiscordTheme.scale(1360);
        int prefH = DiscordTheme.scale(900);
        frame.setMinimumSize(new Dimension(Math.min(minW, usableBounds.width), Math.min(minH, usableBounds.height)));
        frame.setSize(Math.min(prefW, usableBounds.width), Math.min(prefH, usableBounds.height));
        frame.setLocationRelativeTo(null);
        viewHost.setBorder(BorderFactory.createEmptyBorder());
        viewHost.add(loginPanel, LOGIN_VIEW);
        frame.setContentPane(viewHost);
        showLogin();
        frame.setVisible(true);
    }

    private void login(String email, char[] password) {
        try {
            activeUser = loginService.login(email, password);
            chatWorkspace = new ChatWorkspace(activeUser, chatService, this::logout);
            viewHost.add(chatWorkspace, CHAT_VIEW);
            viewLayout.show(viewHost, CHAT_VIEW);
            String modeLabel = chatService.isServerConnected() ? "máy chủ TCP:8888" : "demo cục bộ";
            frame.setTitle("Chat đa luồng — " + activeUser.displayName() + " · " + modeLabel);
        } catch (IllegalArgumentException exception) {
            loginPanel.showError(exception.getMessage());
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    private void logout() {
        activeUser = null;
        chatWorkspace = null;
        loginPanel.clearPassword();
        showLogin();
    }

    private void showLogin() {
        frame.setTitle("Chat đa luồng — đăng nhập");
        viewLayout.show(viewHost, LOGIN_VIEW);
        loginPanel.requestEmailFocus();
    }

    private void showUnavailableAction(String action) {
        if ("Tạo tài khoản".equals(action)) {
            promptRegister();
            return;
        }
        loginPanel.showNotice(action + " chưa có trong bản hiện tại.");
    }

    private void promptRegister() {
        vn.edu.ltm.chat.presentation.RegisterDialog dialog = new vn.edu.ltm.chat.presentation.RegisterDialog(
                frame, loginService, user -> {
            activeUser = user;
            chatWorkspace = new ChatWorkspace(activeUser, chatService, this::logout);
            viewHost.add(chatWorkspace, CHAT_VIEW);
            viewLayout.show(viewHost, CHAT_VIEW);
            String modeLabel = chatService.isServerConnected() ? "máy chủ TCP:8888" : "demo cục bộ";
            frame.setTitle("Chat đa luồng — " + activeUser.displayName() + " · " + modeLabel);
        });
        dialog.setVisible(true);
    }
}

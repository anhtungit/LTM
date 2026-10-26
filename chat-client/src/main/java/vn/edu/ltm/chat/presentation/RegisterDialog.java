package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.application.LoginService;
import vn.edu.ltm.chat.domain.UserProfile;
import vn.edu.ltm.chat.theme.DiscordTheme;
import vn.edu.ltm.chat.theme.IconFactory;

import javax.swing.BorderFactory;
import javax.swing.border.Border;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Consumer;

/**
 * Modal dialog for registering a new user account.
 */
public final class RegisterDialog extends JDialog {
    private final LoginService loginService;
    private final Consumer<UserProfile> onRegistered;

    private final JTextField emailField = new JTextField();
    private final JTextField nameField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JLabel errorLabel = new JLabel(" ");
    private final JButton passwordVisibilityButton = new JButton();
    private final char defaultEchoChar;

    public RegisterDialog(JFrame parent, LoginService loginService, Consumer<UserProfile> onRegistered) {
        super(parent, "Đăng ký tài khoản", true);
        this.loginService = loginService;
        this.onRegistered = onRegistered;
        this.defaultEchoChar = passwordField.getEchoChar();

        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));

        RoundedPanel container = new RoundedPanel(14);
        container.setBackground(DiscordTheme.SIDEBAR);
        container.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x3B, 0x3D, 0x44), 1),
                BorderFactory.createEmptyBorder(22, 24, 20, 24)
        ));
        container.setLayout(new BorderLayout(0, 14));

        container.add(buildHeader(), BorderLayout.NORTH);
        container.add(buildBody(), BorderLayout.CENTER);
        container.add(buildActions(), BorderLayout.SOUTH);

        setContentPane(container);
        setSize(420, 480);
        setLocationRelativeTo(parent);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Tạo tài khoản mới");
        title.setFont(DiscordTheme.bold(18f));
        title.setForeground(Color.WHITE);
        left.add(title);

        JLabel subtitle = new JLabel("Đăng ký tài khoản để trò chuyện trên máy chủ.");
        subtitle.setFont(DiscordTheme.font(12f));
        subtitle.setForeground(DiscordTheme.TEXT_SECONDARY);
        left.add(subtitle);

        header.add(left, BorderLayout.CENTER);

        JButton closeBtn = new JButton(IconFactory.phosphor("x.svg", 16, DiscordTheme.TEXT_MUTED));
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dispose());
        header.add(closeBtn, BorderLayout.EAST);

        return header;
    }

    private JPanel buildBody() {
        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);

        int row = 0;
        row = addField(body, "Email", emailField, "ten@vi-du.com", row);
        row = addField(body, "Tên hiển thị (Tùy chọn)", nameField, "Ví dụ: Minh Nguyễn", row);
        row = addPasswordField(body, row);

        errorLabel.setFont(DiscordTheme.font(11f));
        errorLabel.setForeground(DiscordTheme.DANGER);
        errorLabel.setPreferredSize(new Dimension(360, 18));
        errorLabel.setText(" ");

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 0, 0);
        body.add(errorLabel, c);

        return body;
    }

    private int addField(JPanel body, String labelText, JTextField field, String placeholder, int row) {
        JLabel label = new JLabel(labelText);
        label.setFont(DiscordTheme.semibold(12f));
        label.setForeground(DiscordTheme.TEXT_PRIMARY);

        field.setPreferredSize(new Dimension(360, 38));
        field.setMinimumSize(new Dimension(120, 38));
        field.putClientProperty("JTextField.placeholderText", placeholder);
        DiscordTheme.styleField(field);

        GridBagConstraints c1 = new GridBagConstraints();
        c1.gridx = 0;
        c1.gridy = row++;
        c1.weightx = 1;
        c1.fill = GridBagConstraints.HORIZONTAL;
        c1.insets = new Insets(row == 1 ? 0 : 10, 0, 4, 0);
        body.add(label, c1);

        GridBagConstraints c2 = new GridBagConstraints();
        c2.gridx = 0;
        c2.gridy = row++;
        c2.weightx = 1;
        c2.fill = GridBagConstraints.HORIZONTAL;
        body.add(field, c2);

        return row;
    }

    private int addPasswordField(JPanel body, int row) {
        JLabel label = new JLabel("Mật khẩu");
        label.setFont(DiscordTheme.semibold(12f));
        label.setForeground(DiscordTheme.TEXT_PRIMARY);

        RoundedPanel box = new RoundedPanel(8);
        box.setBackground(DiscordTheme.COMPOSER);
        Border defaultBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DiscordTheme.SEPARATOR, 1),
                BorderFactory.createEmptyBorder(0, 12, 0, 8)
        );
        Border focusedBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x5E, 0x68, 0xF7), 1),
                BorderFactory.createEmptyBorder(0, 12, 0, 8)
        );
        box.setBorder(defaultBorder);
        box.setPreferredSize(new Dimension(360, 38));
        box.setMinimumSize(new Dimension(120, 38));
        box.setLayout(new BorderLayout());

        passwordField.putClientProperty("JPasswordField.placeholderText", "Nhập mật khẩu");
        passwordField.setBorder(null);
        passwordField.setOpaque(false);
        passwordField.setFont(DiscordTheme.font(14f));
        passwordField.setForeground(DiscordTheme.TEXT_PRIMARY);
        passwordField.setCaretColor(DiscordTheme.TEXT_PRIMARY);
        passwordField.addActionListener(e -> submitRegister());
        passwordField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                box.setBorder(focusedBorder);
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                box.setBorder(defaultBorder);
            }
        });
        box.add(passwordField, BorderLayout.CENTER);

        passwordVisibilityButton.setIcon(IconFactory.phosphor("eye.svg", 18, DiscordTheme.TEXT_SECONDARY));
        passwordVisibilityButton.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        passwordVisibilityButton.setContentAreaFilled(false);
        passwordVisibilityButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        passwordVisibilityButton.addActionListener(e -> {
            boolean visible = passwordField.getEchoChar() == 0;
            passwordField.setEchoChar(visible ? defaultEchoChar : (char) 0);
            passwordVisibilityButton.setIcon(IconFactory.phosphor(
                    visible ? "eye.svg" : "eye-slash.svg", 18, DiscordTheme.TEXT_SECONDARY));
        });
        box.add(passwordVisibilityButton, BorderLayout.EAST);

        GridBagConstraints c1 = new GridBagConstraints();
        c1.gridx = 0;
        c1.gridy = row++;
        c1.weightx = 1;
        c1.fill = GridBagConstraints.HORIZONTAL;
        c1.insets = new Insets(10, 0, 4, 0);
        body.add(label, c1);

        GridBagConstraints c2 = new GridBagConstraints();
        c2.gridx = 0;
        c2.gridy = row++;
        c2.weightx = 1;
        c2.fill = GridBagConstraints.HORIZONTAL;
        body.add(box, c2);

        return row;
    }

    private JPanel buildActions() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);

        JButton cancelBtn = new JButton("Hủy");
        cancelBtn.setFont(DiscordTheme.semibold(12f));
        cancelBtn.setForeground(DiscordTheme.TEXT_SECONDARY);
        cancelBtn.setContentAreaFilled(false);
        cancelBtn.setBorderPainted(false);
        cancelBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cancelBtn.addActionListener(e -> dispose());
        actions.add(cancelBtn);

        JButton regBtn = new JButton("Đăng ký");
        regBtn.setFont(DiscordTheme.bold(12f));
        regBtn.setForeground(Color.WHITE);
        regBtn.setBackground(DiscordTheme.ACCENT);
        regBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        regBtn.addActionListener(e -> submitRegister());
        actions.add(regBtn);

        return actions;
    }

    private void submitRegister() {
        String email = emailField.getText().trim();
        String name = nameField.getText().trim();
        char[] pass = passwordField.getPassword();

        if (email.isEmpty()) {
            errorLabel.setText("Vui lòng nhập địa chỉ email.");
            emailField.requestFocusInWindow();
            return;
        }
        if (pass == null || pass.length == 0) {
            errorLabel.setText("Vui lòng nhập mật khẩu.");
            passwordField.requestFocusInWindow();
            return;
        }

        final char[] passCopy = java.util.Arrays.copyOf(pass, pass.length);
        java.util.Arrays.fill(pass, '\0');
        errorLabel.setForeground(DiscordTheme.WARNING);
        errorLabel.setText("Đang đăng ký tài khoản trên máy chủ...");

        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return loginService.register(email, passCopy, name.isEmpty() ? null : name);
            } finally {
                java.util.Arrays.fill(passCopy, '\0');
            }
        }).whenComplete((user, throwable) -> javax.swing.SwingUtilities.invokeLater(() -> {
            if (throwable != null) {
                Throwable cause = throwable instanceof java.util.concurrent.CompletionException && throwable.getCause() != null
                        ? throwable.getCause() : throwable;
                errorLabel.setForeground(DiscordTheme.DANGER);
                errorLabel.setText(cause.getMessage() != null ? cause.getMessage() : "Đăng ký thất bại.");
                return;
            }
            if (onRegistered != null) {
                onRegistered.accept(user);
            }
            dispose();
        }));
    }
}


package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.theme.DiscordTheme;
import vn.edu.ltm.chat.theme.IconFactory;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Demo sign-in screen.
 *
 * <p>The card keeps a fixed content column and a natural height, and the whole screen scrolls when
 * the window is shorter than the card. Two earlier problems are addressed here: the card used to
 * have a hard-coded height that was clipped on shorter displays, and the card body used a
 * {@link BoxLayout} column that collapsed the fields to roughly two thirds of the available width
 * and pushed them to the right edge, which also cut off the subtitle.</p>
 */
public final class LoginPanel extends JPanel {
    private static final int CONTENT_WIDTH = DiscordTheme.scale(368);
    private static final int FIELD_HEIGHT = DiscordTheme.scale(42);
    private static final int SIDE_PADDING = DiscordTheme.scale(40);

    private final BiConsumer<String, char[]> onLogin;
    private final Consumer<String> onUnavailableAction;
    private final JTextField emailField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton passwordVisibilityButton = new JButton();
    private final char defaultEchoChar = passwordField.getEchoChar();

    public LoginPanel(BiConsumer<String, char[]> onLogin, Consumer<String> onUnavailableAction) {
        this.onLogin = onLogin;
        this.onUnavailableAction = onUnavailableAction;

        setLayout(new BorderLayout());
        setBackground(DiscordTheme.RAIL);
        add(buildScrollHost(), BorderLayout.CENTER);
    }

    private JComponent buildScrollHost() {
        JPanel host = new CenteredBody();
        host.setLayout(new GridBagLayout());
        host.setBackground(DiscordTheme.RAIL);

        // Two vertical springs keep the card centred when the window is tall enough, and let it
        // start at the top (scrollable, never clipped) when the window is shorter than the card.
        GridBagConstraints topSpring = new GridBagConstraints();
        topSpring.gridx = 0;
        topSpring.gridy = 0;
        topSpring.weightx = 1;
        topSpring.weighty = 1;
        topSpring.fill = GridBagConstraints.BOTH;
        host.add(Box.createGlue(), topSpring);

        GridBagConstraints cardConstraints = new GridBagConstraints();
        cardConstraints.gridx = 0;
        cardConstraints.gridy = 1;
        cardConstraints.weightx = 1;
        cardConstraints.anchor = GridBagConstraints.CENTER;
        cardConstraints.insets = new Insets(20, 20, 20, 20);
        host.add(buildCard(), cardConstraints);

        GridBagConstraints bottomSpring = new GridBagConstraints();
        bottomSpring.gridx = 0;
        bottomSpring.gridy = 2;
        bottomSpring.weightx = 1;
        bottomSpring.weighty = 1;
        bottomSpring.fill = GridBagConstraints.BOTH;
        host.add(Box.createGlue(), bottomSpring);

        JScrollPane scroll = new JScrollPane(host);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        return scroll;
    }

    private JPanel buildCard() {
        RoundedPanel card = new RoundedPanel(14);
        card.setBackground(DiscordTheme.SIDEBAR);
        card.setLayout(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(24, SIDE_PADDING, 22, SIDE_PADDING));
        card.add(buildContent(), BorderLayout.CENTER);
        return card;
    }

    private JComponent buildContent() {
        JPanel content = new JPanel(new GridBagLayout()) {
            @Override
            public Dimension getPreferredSize() {
                Dimension preferred = super.getPreferredSize();
                return new Dimension(CONTENT_WIDTH, preferred.height);
            }
        };
        content.setOpaque(false);

        int row = 0;
        row = addRow(content, buildHeader(), row, 18);
        row = addRow(content, fieldLabel("Email", emailField), row, 6);
        row = addRow(content, emailFieldInput(), row, 14);
        row = addRow(content, passwordLabelRow(), row, 6);
        row = addRow(content, passwordInputRow(), row, 14);
        row = addRow(content, demoNotice(), row, 6);
        row = addRow(content, statusRow(), row, 6);
        row = addRow(content, loginButton(), row, 8);
        row = addRow(content, createAccountButton(), row, 16);
        row = addRow(content, separator(), row, 12);
        addRow(content, centeredLabel("Kết nối tự động · đồng bộ thời gian thực qua TCP/SQLite", 12,
                DiscordTheme.TEXT_MUTED), row, 0);
        return content;
    }

    private int addRow(JPanel content, JComponent component, int row, int bottomGap) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, bottomGap, 0);
        content.add(component, constraints);
        return row + 1;
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        RoundedPanel mark = new RoundedPanel(16);
        mark.setBackground(DiscordTheme.ACCENT);
        mark.setLayout(new BorderLayout());
        mark.setAlignmentX(CENTER_ALIGNMENT);
        Dimension markSize = new Dimension(44, 44);
        mark.setPreferredSize(markSize);
        mark.setMinimumSize(markSize);
        mark.setMaximumSize(markSize);
        JLabel markIcon = new JLabel(IconFactory.phosphor("chat-circle-dots.svg", 24, Color.WHITE));
        markIcon.setHorizontalAlignment(SwingConstants.CENTER);
        mark.add(markIcon, BorderLayout.CENTER);
        header.add(mark);
        header.add(Box.createVerticalStrut(8));

        JLabel productName = centeredLabel("Chat đa luồng", 14, DiscordTheme.TEXT_PRIMARY);
        productName.setFont(DiscordTheme.bold(14f));
        header.add(productName);
        header.add(Box.createVerticalStrut(14));

        JLabel headline = centeredLabel("Chào mừng trở lại", 23, DiscordTheme.TEXT_PRIMARY);
        headline.setFont(DiscordTheme.extrabold(23f));
        header.add(headline);
        header.add(Box.createVerticalStrut(6));

        JLabel subtitle = new JLabel("<html><div align='center'>"
                + "Đăng nhập để tiếp tục trò chuyện cùng nhóm."
                + "</div></html>");
        subtitle.setAlignmentX(CENTER_ALIGNMENT);
        subtitle.setFont(DiscordTheme.font(14f));
        subtitle.setForeground(DiscordTheme.TEXT_SECONDARY);
        subtitle.setHorizontalAlignment(SwingConstants.CENTER);
        header.add(subtitle);
        return header;
    }

    private JLabel fieldLabel(String title, JComponent target) {
        JLabel label = new JLabel("<html>" + escape(title)
                + "&nbsp;<span style='color:#F54748'>*</span></html>");
        label.setFont(DiscordTheme.bold(13f));
        label.setForeground(DiscordTheme.TEXT_PRIMARY);
        label.setLabelFor(target);
        return label;
    }

    private JComponent emailFieldInput() {
        emailField.setName("email");
        emailField.setToolTipText("Nhập email đã đăng ký trên máy chủ.");
        emailField.putClientProperty("JTextField.placeholderText", "ten@vi-du.com");
        emailField.setPreferredSize(new Dimension(CONTENT_WIDTH, FIELD_HEIGHT));
        emailField.setMinimumSize(new Dimension(120, FIELD_HEIGHT));
        DiscordTheme.styleField(emailField);
        emailField.addActionListener(event -> passwordField.requestFocusInWindow());
        return emailField;
    }

    private JComponent passwordLabelRow() {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.add(fieldLabel("Mật khẩu", passwordField), BorderLayout.WEST);

        JButton forgotPassword = new JButton("Quên mật khẩu?");
        forgotPassword.setBorderPainted(false);
        forgotPassword.setContentAreaFilled(false);
        forgotPassword.setFocusable(true);
        forgotPassword.setForeground(DiscordTheme.ACCENT_SOFT);
        forgotPassword.setFont(DiscordTheme.semibold(12f));
        forgotPassword.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        forgotPassword.getAccessibleContext().setAccessibleName("Quên mật khẩu (chưa kết nối máy chủ)");
        forgotPassword.addActionListener(event -> onUnavailableAction.accept("Khôi phục mật khẩu"));
        row.add(forgotPassword, BorderLayout.EAST);
        return row;
    }

    private JComponent passwordInputRow() {
        RoundedPanel box = new RoundedPanel(8);
        box.setBackground(DiscordTheme.COMPOSER);
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DiscordTheme.SEPARATOR, 1),
                BorderFactory.createEmptyBorder(0, 12, 0, 8)
        ));
        box.setPreferredSize(new Dimension(CONTENT_WIDTH, FIELD_HEIGHT));
        box.setLayout(new BorderLayout());

        passwordField.setName("password");
        passwordField.setToolTipText("Nhập mật khẩu tài khoản.");
        passwordField.putClientProperty("JPasswordField.placeholderText", "Nhập mật khẩu");
        passwordField.setBorder(null);
        passwordField.setOpaque(false);
        passwordField.setFont(DiscordTheme.font(14f));
        passwordField.setForeground(DiscordTheme.TEXT_PRIMARY);
        passwordField.setCaretColor(DiscordTheme.TEXT_PRIMARY);
        passwordField.addActionListener(event -> submit());
        box.add(passwordField, BorderLayout.CENTER);

        passwordVisibilityButton.setIcon(IconFactory.phosphor("eye.svg", 18, DiscordTheme.TEXT_SECONDARY));
        passwordVisibilityButton.setToolTipText("Hiện mật khẩu");
        passwordVisibilityButton.getAccessibleContext().setAccessibleName("Hiện mật khẩu");
        passwordVisibilityButton.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        passwordVisibilityButton.setContentAreaFilled(false);
        passwordVisibilityButton.setFocusable(true);
        passwordVisibilityButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        passwordVisibilityButton.addActionListener(this::togglePasswordVisibility);
        box.add(passwordVisibilityButton, BorderLayout.EAST);
        return box;
    }

    private JComponent demoNotice() {
        RoundedPanel notice = new RoundedPanel(8);
        notice.setBackground(DiscordTheme.TRANSCRIPT);
        notice.setLayout(new BorderLayout(10, 0));
        notice.setBorder(BorderFactory.createEmptyBorder(11, 12, 11, 12));

        JLabel infoIcon = new JLabel(IconFactory.phosphor("info.svg", 16, DiscordTheme.SUCCESS));
        infoIcon.setVerticalAlignment(SwingConstants.TOP);
        notice.add(infoIcon, BorderLayout.WEST);

        WrappedTextArea text = new WrappedTextArea(
                "Đăng nhập bằng tài khoản máy chủ. Nếu chưa có, bấm 'Tạo tài khoản'.", CONTENT_WIDTH - 52);
        text.setFont(DiscordTheme.font(12f));
        text.setForeground(DiscordTheme.TEXT_SECONDARY);
        notice.add(text, BorderLayout.CENTER);
        notice.getAccessibleContext().setAccessibleName(
                "Hệ thống yêu cầu tài khoản đã đăng ký trên máy chủ.");
        return notice;
    }

    private JComponent statusRow() {
        statusLabel.setFont(DiscordTheme.font(12f));
        statusLabel.setForeground(DiscordTheme.DANGER);
        collapseStatus();
        return statusLabel;
    }

    private void collapseStatus() {
        statusLabel.setText("");
        statusLabel.setPreferredSize(new Dimension(CONTENT_WIDTH, 0));
        statusLabel.setMinimumSize(new Dimension(CONTENT_WIDTH, 0));
        statusLabel.revalidate();
    }

    private void showStatus(String message, Color color) {
        statusLabel.setForeground(color);
        statusLabel.setText(message);
        statusLabel.setPreferredSize(new Dimension(CONTENT_WIDTH, 18));
        statusLabel.setMinimumSize(new Dimension(CONTENT_WIDTH, 18));
        statusLabel.revalidate();
    }

    private JButton loginButton() {
        JButton button = new JButton("Đăng nhập");
        configureButton(button, FIELD_HEIGHT);
        button.getAccessibleContext().setAccessibleName("Đăng nhập vào chế độ demo");
        DiscordTheme.stylePrimaryButton(button);
        button.addActionListener(event -> submit());
        return button;
    }

    private JButton createAccountButton() {
        JButton button = new JButton("Tạo tài khoản");
        configureButton(button, FIELD_HEIGHT);
        button.getAccessibleContext().setAccessibleName("Tạo tài khoản (chưa kết nối máy chủ)");
        DiscordTheme.styleSecondaryButton(button);
        button.addActionListener(event -> onUnavailableAction.accept("Tạo tài khoản"));
        return button;
    }

    private void configureButton(JButton button, int height) {
        button.setPreferredSize(new Dimension(CONTENT_WIDTH, height));
        button.setMinimumSize(new Dimension(120, height));
        button.setFocusPainted(true);
    }

    private JComponent separator() {
        JPanel line = new JPanel();
        line.setBackground(new Color(0x34, 0x36, 0x3C));
        line.setPreferredSize(new Dimension(CONTENT_WIDTH, 1));
        return line;
    }

    private JLabel centeredLabel(String text, int size, Color color) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setAlignmentX(CENTER_ALIGNMENT);
        label.setFont(DiscordTheme.font((float) size));
        label.setForeground(color);
        return label;
    }

    private String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void togglePasswordVisibility(ActionEvent event) {
        boolean currentlyVisible = passwordField.getEchoChar() == 0;
        passwordField.setEchoChar(currentlyVisible ? defaultEchoChar : (char) 0);
        passwordVisibilityButton.setIcon(IconFactory.phosphor(
                currentlyVisible ? "eye.svg" : "eye-slash.svg", 18, DiscordTheme.TEXT_SECONDARY));
        passwordVisibilityButton.setToolTipText(currentlyVisible ? "Hiện mật khẩu" : "Ẩn mật khẩu");
        passwordVisibilityButton.getAccessibleContext()
                .setAccessibleName(currentlyVisible ? "Hiện mật khẩu" : "Ẩn mật khẩu");
        passwordField.requestFocusInWindow();
    }

    private void submit() {
        collapseStatus();
        onLogin.accept(emailField.getText(), passwordField.getPassword());
    }

    public void showError(String message) {
        showStatus(message == null ? "Không thể đăng nhập demo." : message, DiscordTheme.DANGER);
        emailField.requestFocusInWindow();
    }

    public void showNotice(String message) {
        showStatus(message, DiscordTheme.WARNING);
    }

    public void clearPassword() {
        passwordField.setText("");
        collapseStatus();
    }

    public void requestEmailFocus() {
        SwingUtilities.invokeLater(emailField::requestFocusInWindow);
    }

    /** Lets the scroll pane center a short card and still scroll it when the window is small. */
    private static final class CenteredBody extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(24, visibleRect.height - 48);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport viewport
                    && viewport.getHeight() > getPreferredSize().height;
        }
    }
}

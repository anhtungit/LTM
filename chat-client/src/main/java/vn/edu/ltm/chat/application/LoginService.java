package vn.edu.ltm.chat.application;

import vn.edu.ltm.chat.domain.UserProfile;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Local-only demo authentication. This class never persists credentials or contacts a server. */
public final class LoginService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final ChatGateway gateway;

    public LoginService() {
        this(null);
    }

    public LoginService(ChatGateway gateway) {
        this.gateway = gateway;
    }

    public UserProfile login(String email, char[] password) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new IllegalArgumentException("Nhập địa chỉ email hợp lệ.");
        }
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Nhập mật khẩu để vào chế độ demo.");
        }

        if (gateway != null) {
            try {
                return gateway.login(normalizedEmail, new String(password));
            } catch (UnsupportedOperationException e) {
                throw new IllegalArgumentException("Không thể kết nối đến máy chủ chat. Vui lòng kiểm tra kết nối mạng hoặc đảm bảo máy chủ đang hoạt động.");
            }
        }

        String handle = normalizedEmail.substring(0, normalizedEmail.indexOf('@'));
        String displayName = toDisplayName(handle);
        return new UserProfile(UUID.randomUUID().toString(), displayName, normalizedEmail);
    }

    public UserProfile register(String email, char[] password, String displayName) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new IllegalArgumentException("Nhập địa chỉ email hợp lệ.");
        }
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Nhập mật khẩu để đăng ký tài khoản.");
        }

        if (gateway != null) {
            try {
                return gateway.register(normalizedEmail, new String(password), displayName);
            } catch (UnsupportedOperationException e) {
                throw new IllegalArgumentException("Không thể kết nối đến máy chủ chat. Vui lòng kiểm tra kết nối mạng hoặc đảm bảo máy chủ đang hoạt động.");
            }
        }

        String handle = normalizedEmail.substring(0, normalizedEmail.indexOf('@'));
        String resolvedName = (displayName != null && !displayName.isBlank()) ? displayName.trim() : toDisplayName(handle);
        return new UserProfile(UUID.randomUUID().toString(), resolvedName, normalizedEmail);
    }

    private String toDisplayName(String handle) {
        String[] parts = handle.replaceAll("[._-]+", " ").trim().split("\\s+");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank()) {
                if (!name.isEmpty()) {
                    name.append(' ');
                }
                name.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
                if (part.length() > 1) {
                    name.append(part.substring(1));
                }
            }
        }
        return name.isEmpty() ? "Bạn" : name.toString();
    }
}

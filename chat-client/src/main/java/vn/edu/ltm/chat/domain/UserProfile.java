package vn.edu.ltm.chat.domain;

import java.awt.Color;

public record UserProfile(
        String id,
        String displayName,
        String email,
        String username,
        String bio,
        String status,
        String avatarPath
) {
    public UserProfile {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName is required");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        if (username == null || username.isBlank()) {
            username = "@" + id.toLowerCase().replace(" ", "");
        }
        if (bio == null) {
            bio = "Thích trò chuyện, nghe nhạc và chơi game cùng bạn bè. ☁️";
        }
        if (status == null || status.isBlank()) {
            status = "online";
        }
    }

    public UserProfile(String id, String displayName, String email) {
        this(id, displayName, email, "@" + id.toLowerCase().replace(" ", ""),
                "Thích trò chuyện, nghe nhạc và chơi game cùng bạn bè. ☁️", "online", null);
    }

    public String initials() {
        String[] parts = displayName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    public boolean online() {
        return !"offline".equalsIgnoreCase(status);
    }

    public Color statusColor() {
        return switch (status) {
            case "idle" -> new Color(0xF0, 0xBB, 0x3B);
            case "dnd" -> new Color(0xF5, 0x47, 0x48);
            case "offline" -> new Color(0x74, 0x7F, 0x8D);
            default -> new Color(0x43, 0xC0, 0x7A);
        };
    }

    public String statusLabel() {
        return switch (status) {
            case "idle" -> "Chờ";
            case "dnd" -> "Không làm phiền";
            case "offline" -> "Ẩn danh";
            default -> "Đang hoạt động";
        };
    }

    public UserProfile withDisplayName(String newName) {
        return new UserProfile(id, newName, email, username, bio, status, avatarPath);
    }

    public UserProfile withDetails(String newName, String newUsername, String newBio, String newStatus, String newAvatar) {
        return new UserProfile(id, newName, email, newUsername, newBio, newStatus, newAvatar);
    }
}

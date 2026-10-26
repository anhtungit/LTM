package vn.edu.ltm.chat.server.dal;

import com.google.gson.Gson;
import vn.edu.ltm.chat.server.protocol.ProtocolDtos.ConversationDto;
import vn.edu.ltm.chat.server.protocol.ProtocolDtos.MessageDto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles database DDL schema creation, demo data seeding, and backward-compatible data migrations.
 */
final class DatabaseSchemaMigrator {
    private static final Gson GSON = new Gson();

    static void initialize(DatabaseManager db, Connection conn) throws SQLException {
        createTables(conn);
        if (countConversations(conn) == 0) {
            seedDefaultData(db, conn);
        } else {
            migrateLegacyDmMessages(db, conn);
        }
        migrateGroupMembersToUserIds(conn);
    }

    private static void createTables(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id TEXT PRIMARY KEY,
                    email TEXT UNIQUE NOT NULL,
                    password TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    username TEXT NOT NULL,
                    bio TEXT NOT NULL,
                    status TEXT NOT NULL,
                    avatar_path TEXT
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS conversations (
                    id TEXT PRIMARY KEY,
                    display_name TEXT NOT NULL,
                    initials TEXT NOT NULL,
                    about TEXT NOT NULL,
                    online INTEGER NOT NULL DEFAULT 1,
                    is_group INTEGER NOT NULL DEFAULT 0,
                    members_json TEXT NOT NULL,
                    channels_json TEXT NOT NULL,
                    voice_channels_json TEXT NOT NULL
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS user_dms (
                    user_id TEXT NOT NULL,
                    peer_id TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    PRIMARY KEY (user_id, peer_id)
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS messages (
                    id TEXT PRIMARY KEY,
                    conversation_id TEXT NOT NULL,
                    channel TEXT NOT NULL DEFAULT '',
                    sender_id TEXT NOT NULL,
                    sender_name TEXT NOT NULL,
                    sender_initials TEXT NOT NULL,
                    content TEXT NOT NULL,
                    sent_at TEXT NOT NULL,
                    edited INTEGER NOT NULL DEFAULT 0,
                    recalled INTEGER NOT NULL DEFAULT 0,
                    attachment_name TEXT,
                    attachment_size INTEGER NOT NULL DEFAULT 0,
                    attachment_failed INTEGER NOT NULL DEFAULT 0
                )
            """);
        }
    }

    private static int countConversations(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM conversations")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static void migrateGroupMembersToUserIds(Connection conn) {
        try {
            Map<String, String> displayNameToId = new HashMap<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id, display_name FROM users ORDER BY rowid ASC")) {
                while (rs.next()) {
                    String uid = rs.getString("id");
                    String dname = rs.getString("display_name");
                    if (dname != null && !dname.isBlank()) {
                        displayNameToId.putIfAbsent(dname.trim().toLowerCase(Locale.ROOT), uid);
                    }
                    if (uid != null && !uid.isBlank()) {
                        displayNameToId.putIfAbsent(uid.trim().toLowerCase(Locale.ROOT), uid);
                    }
                }
            }

            List<String[]> groupsToUpdate = new ArrayList<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id, members_json FROM conversations WHERE is_group = 1")) {
                while (rs.next()) {
                    String gid = rs.getString("id");
                    String mJson = rs.getString("members_json");
                    if ("nhom-ban-than".equals(gid) || "ltm-studio".equals(gid) || "phong-chill".equals(gid)) {
                        continue;
                    }
                    if (mJson != null && !mJson.isBlank()) {
                        String[] members = GSON.fromJson(mJson, String[].class);
                        if (members != null) {
                            List<String> updated = new ArrayList<>();
                            boolean changed = false;
                            for (String m : members) {
                                if (m == null) continue;
                                String trimmed = m.trim();
                                String resolvedId = displayNameToId.get(trimmed.toLowerCase(Locale.ROOT));
                                if (resolvedId != null) {
                                    if (!resolvedId.equals(trimmed)) {
                                        changed = true;
                                    }
                                    if (!updated.contains(resolvedId)) {
                                        updated.add(resolvedId);
                                    }
                                } else {
                                    if (!updated.contains(trimmed)) {
                                        updated.add(trimmed);
                                    }
                                }
                            }
                            if (changed) {
                                groupsToUpdate.add(new String[]{gid, GSON.toJson(updated)});
                            }
                        }
                    }
                }
            }

            for (String[] g : groupsToUpdate) {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE conversations SET members_json = ? WHERE id = ?")) {
                    ps.setString(1, g[1]);
                    ps.setString(2, g[0]);
                    ps.executeUpdate();
                }
            }

            try (Statement stClean = conn.createStatement()) {
                stClean.executeUpdate("UPDATE conversations SET online = 0 WHERE is_group = 1");
                stClean.executeUpdate("UPDATE conversations SET online = 0");
                stClean.executeUpdate("UPDATE users SET status = 'offline'");
            }
        } catch (SQLException ignored) {
        }
    }

    private static void migrateLegacyDmMessages(DatabaseManager db, Connection conn) throws SQLException {
        insertUserIfAbsent(conn, "mai-phuong", "mai.phuong@example.com", "123456", "Mai Phương",
                "@maiphuong", "Thiết kế sản phẩm · thích vẽ và chơi game cùng bạn bè.", "online");
        insertUserIfAbsent(conn, "an-nguyen", "an.nguyen@example.com", "123456", "An Nguyễn",
                "@annguyen", "Lập trình backend · đang thử luồng chat cục bộ.", "offline");
        insertUserIfAbsent(conn, "duc-khanh", "duc.khanh@example.com", "123456", "Đức Khánh",
                "@duckhanh", "Bạn học chung · kiểm tra phần giao diện.", "online");
        insertUserIfAbsent(conn, "ha-linh", "ha.linh@example.com", "123456", "Hà Linh",
                "@halinh", "Bạn học chung · trò chuyện demo.", "offline");

        Set<String> groupIds = new HashSet<>();
        for (ConversationDto c : db.listConversations()) {
            if (c.isGroup()) {
                groupIds.add(c.id());
            }
        }

        List<String[]> toMigrate = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, conversation_id, sender_id, sent_at FROM messages")) {
            while (rs.next()) {
                String msgId = rs.getString("id");
                String convId = rs.getString("conversation_id");
                String senderId = rs.getString("sender_id");
                String sentAt = rs.getString("sent_at");
                if (convId != null && !convId.startsWith("dm:") && !groupIds.contains(convId)) {
                    toMigrate.add(new String[]{msgId, convId, senderId, sentAt});
                }
            }
        }

        for (String[] row : toMigrate) {
            String msgId = row[0];
            String targetPeer = row[1];
            String senderId = row[2];
            String sentAt = row[3];
            if (senderId.equals(targetPeer)) {
                String peer = "mai-phuong".equals(targetPeer) ? "an-nguyen" : targetPeer;
                String threadId = DatabaseManager.dmThreadId("mai-phuong", peer);
                try (PreparedStatement upd = conn.prepareStatement("UPDATE messages SET conversation_id = ? WHERE id = ?")) {
                    upd.setString(1, threadId);
                    upd.setString(2, msgId);
                    upd.executeUpdate();
                }
                db.linkUserDm(conn, "mai-phuong", peer, sentAt);
                db.linkUserDm(conn, peer, "mai-phuong", sentAt);
            } else {
                String threadId = DatabaseManager.dmThreadId(senderId, targetPeer);
                try (PreparedStatement upd = conn.prepareStatement("UPDATE messages SET conversation_id = ? WHERE id = ?")) {
                    upd.setString(1, threadId);
                    upd.setString(2, msgId);
                    upd.executeUpdate();
                }
                db.linkUserDm(conn, senderId, targetPeer, sentAt);
                db.linkUserDm(conn, targetPeer, senderId, sentAt);
            }
        }
    }

    private static void seedDefaultData(DatabaseManager db, Connection conn) throws SQLException {
        insertUserIfAbsent(conn, "mai-phuong", "mai.phuong@example.com", "123456", "Mai Phương",
                "@maiphuong", "Thiết kế sản phẩm · thích vẽ và chơi game cùng bạn bè.", "online");
        insertUserIfAbsent(conn, "an-nguyen", "an.nguyen@example.com", "123456", "An Nguyễn",
                "@annguyen", "Lập trình backend · đang thử luồng chat cục bộ.", "offline");
        insertUserIfAbsent(conn, "duc-khanh", "duc.khanh@example.com", "123456", "Đức Khánh",
                "@duckhanh", "Bạn học chung · kiểm tra phần giao diện.", "online");
        insertUserIfAbsent(conn, "ha-linh", "ha.linh@example.com", "123456", "Hà Linh",
                "@halinh", "Bạn học chung · trò chuyện demo.", "offline");

        db.insertConversation(new ConversationDto("mai-phuong", "Mai Phương", "MP",
                "Thiết kế sản phẩm · thích vẽ và chơi game cùng bạn bè.", true, false,
                List.of(), List.of(), List.of()));
        db.insertConversation(new ConversationDto("an-nguyen", "An Nguyễn", "AN",
                "Lập trình backend · đang thử luồng chat cục bộ.", false, false,
                List.of(), List.of(), List.of()));
        db.insertConversation(new ConversationDto("duc-khanh", "Đức Khánh", "ĐK",
                "Bạn học chung · kiểm tra phần giao diện.", true, false,
                List.of(), List.of(), List.of()));
        db.insertConversation(new ConversationDto("ha-linh", "Hà Linh", "HL",
                "Bạn học chung · trò chuyện demo.", false, false,
                List.of(), List.of(), List.of()));

        db.insertConversation(new ConversationDto("nhom-ban-than", "Nhóm bạn thân", "HB",
                "Cùng nhau học, cùng nhau tiến bộ.", false, true,
                List.of("Mai Phương", "An Nguyễn", "Thảo Nguyễn", "Mai Linh", "Bảo Long", "Đức Minh"),
                List.of("tổng-quan", "học-tập", "ảnh-chia-sẻ"),
                List.of("Phòng chill", "Góc học chung")));

        db.insertConversation(new ConversationDto("ltm-studio", "LTM Studio", "LT",
                "Nhóm phát triển hệ thống mạng máy tính.", false, true,
                List.of("Mai Phương", "An Nguyễn", "Minh Anh", "Khánh Linh", "Hữu Phúc", "Thành Đạt"),
                List.of("chung", "chia-sẻ-tài-liệu"),
                List.of("phòng-họp", "Thảo luận nhanh")));

        db.insertConversation(new ConversationDto("phong-chill", "Hội Bạn Chill", "CL",
                "Góc giải trí và trò chuyện cuối tuần.", false, true,
                List.of("Mai Phương", "Hà My", "Quốc Thái", "Thanh Duy"),
                List.of("trò-chuyện", "âm-nhạc"),
                List.of("Phòng nghe nhạc")));

        LocalDateTime now = LocalDateTime.now();

        db.linkUserDm(conn, "mai-phuong", "an-nguyen", now.minusHours(2).toString());
        db.linkUserDm(conn, "an-nguyen", "mai-phuong", now.minusHours(2).toString());
        db.linkUserDm(conn, "mai-phuong", "duc-khanh", now.minusHours(5).toString());
        db.linkUserDm(conn, "duc-khanh", "mai-phuong", now.minusHours(5).toString());
        db.linkUserDm(conn, "mai-phuong", "ha-linh", now.minusDays(1).toString());
        db.linkUserDm(conn, "ha-linh", "mai-phuong", now.minusDays(1).toString());

        db.insertRawMessage(new MessageDto(UUID.randomUUID().toString(), DatabaseManager.dmThreadId("mai-phuong", "an-nguyen"), "",
                "an-nguyen", "An Nguyễn", "AN",
                "Mình có thể chọn cuộc trò chuyện ở danh sách bên trái.", now.minusHours(2).toString(), false, false, null, 0, false));
        db.insertRawMessage(new MessageDto(UUID.randomUUID().toString(), DatabaseManager.dmThreadId("mai-phuong", "duc-khanh"), "",
                "duc-khanh", "Đức Khánh", "ĐK",
                "Hệ thống hiện đã kết nối trực tiếp với SQLite database trên Server.", now.minusHours(5).toString(), false, false, null, 0, false));
        db.insertRawMessage(new MessageDto(UUID.randomUUID().toString(), DatabaseManager.dmThreadId("mai-phuong", "ha-linh"), "",
                "ha-linh", "Hà Linh", "HL",
                "Chào bạn! Đây là hội thoại được đồng bộ từ máy chủ.", now.minusDays(1).toString(), false, false, null, 0, false));

        db.insertRawMessage(new MessageDto(UUID.randomUUID().toString(), "nhom-ban-than", "học-tập", "mai-linh", "Mai Linh", "ML",
                "Mình gom tài liệu ôn tập vào đây nhé. Mọi người xem thử kế hoạch tuần này có cần thêm gì không?",
                now.minusMinutes(25).toString(), false, false, null, 0, false));
        db.insertRawMessage(new MessageDto(UUID.randomUUID().toString(), "nhom-ban-than", "học-tập", "mai-linh", "Mai Linh", "ML",
                "Đây là góc học tập mình vừa sắp xếp lại, gửi mọi người xem bố cục nha.",
                now.minusMinutes(23).toString(), false, false, "setup-moi.jpg", 1_800_000L, false));
        db.insertRawMessage(new MessageDto(UUID.randomUUID().toString(), "nhom-ban-than", "học-tập", "bao-long", "Bảo Long", "BL",
                "Mình gửi bản tổng hợp buổi học hôm qua ở đây. Mọi người tải về xem nhé.",
                now.minusMinutes(15).toString(), false, false, "Tổng_hợp_bài_giảng_tuần_04.pdf", 2_400_000L, false));
        db.insertRawMessage(new MessageDto(UUID.randomUUID().toString(), "nhom-ban-than", "học-tập", "duc-minh", "Đức Minh", "ĐM",
                "Đẹp đó Linh, mình sẽ thêm lịch ôn vào tối thứ năm.",
                now.minusMinutes(10).toString(), false, false, null, 0, false));

        db.insertRawMessage(new MessageDto(UUID.randomUUID().toString(), "nhom-ban-than", "tổng-quan", "thao-nguyen", "Thảo Nguyễn", "TN",
                "Chào mừng mọi người đến với kênh tổng quan của nhóm bạn thân!",
                now.minusHours(3).toString(), false, false, null, 0, false));
        db.insertRawMessage(new MessageDto(UUID.randomUUID().toString(), "nhom-ban-than", "tổng-quan", "duc-minh", "Đức Minh", "ĐM",
                "Tuần này chúng ta sẽ tập trung hoàn thiện bài tập lớn LTM nhé.",
                now.minusHours(2).toString(), false, false, null, 0, false));

        db.insertRawMessage(new MessageDto(UUID.randomUUID().toString(), "nhom-ban-than", "ảnh-chia-sẻ", "bao-long", "Bảo Long", "BL",
                "Kênh lưu trữ hình ảnh kỷ niệm của nhóm.",
                now.minusDays(2).toString(), false, false, null, 0, false));

        db.insertRawMessage(new MessageDto(UUID.randomUUID().toString(), "ltm-studio", "chung", "minh-anh", "Minh Anh", "MA",
                "Chào mừng team LTM Studio! Báo cáo tiến độ các module tại đây.",
                now.minusHours(4).toString(), false, false, null, 0, false));
    }

    private static void insertUserIfAbsent(Connection conn, String id, String email, String password, String displayName,
                                            String username, String bio, String status) throws SQLException {
        String sql = "INSERT INTO users(id, email, password, display_name, username, bio, status) VALUES(?,?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, email.toLowerCase(Locale.ROOT));
            ps.setString(3, password);
            ps.setString(4, displayName);
            ps.setString(5, username);
            ps.setString(6, bio);
            ps.setString(7, status);
            ps.executeUpdate();
        }
    }
}


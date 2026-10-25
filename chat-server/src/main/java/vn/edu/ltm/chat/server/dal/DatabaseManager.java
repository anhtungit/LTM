package vn.edu.ltm.chat.server.dal;

import com.google.gson.Gson;
import vn.edu.ltm.chat.server.protocol.ProtocolDtos.ConversationDto;
import vn.edu.ltm.chat.server.protocol.ProtocolDtos.MessageDto;
import vn.edu.ltm.chat.server.protocol.ProtocolDtos.UserDto;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * Data Access Layer (DAL) using embedded SQLite database (data/chat_server.db)
 * and physical file storage (server_storage/).
 * Isolates 1-1 Direct Messages per user pair (dm:userA::userB) and Groups per member.
 */
public final class DatabaseManager {
    private static final Gson GSON = new Gson();
    private static DatabaseManager instance;

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
        }
    }

    private final String jdbcUrl;
    private final String dbUser;
    private final String dbPassword;
    private final boolean isPostgres;
    private final Path storageDir;
    private Connection sharedPostgresConn;

    public DatabaseManager(String rawPgUrl, String storagePath, boolean isPostgres) {
        this.isPostgres = isPostgres;
        this.storageDir = Path.of(storagePath);
        try {
            Files.createDirectories(this.storageDir);
            String clean = rawPgUrl.replaceFirst("^jdbc:", "");
            URI uri = URI.create(clean);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String path = uri.getPath() != null && !uri.getPath().isEmpty() ? uri.getPath() : "/postgres";

            String userInfo = uri.getUserInfo();
            String parsedUser;
            String parsedPassword;
            if (userInfo != null && userInfo.contains(":")) {
                int colon = userInfo.indexOf(':');
                parsedUser = userInfo.substring(0, colon);
                parsedPassword = userInfo.substring(colon + 1);
            } else {
                parsedUser = "postgres";
                parsedPassword = "";
            }

            // Supabase direct host (db.xxx.supabase.co:5432) is IPv6-only. Convert to IPv4 Pooler for cloud environments
            if (host != null && host.startsWith("db.") && host.endsWith(".supabase.co")) {
                String projectRef = host.substring(3, host.indexOf(".supabase.co"));
                host = "aws-0-ap-northeast-2.pooler.supabase.com";
                port = 6543;
                if (!parsedUser.contains(".")) {
                    parsedUser = parsedUser + "." + projectRef;
                }
            }
            this.dbUser = parsedUser;
            this.dbPassword = parsedPassword;

            this.jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path + "?sslmode=require&prepareThreshold=0&tcpKeepAlive=true";
            initSchemaAndSeed();
            System.out.println("[DatabaseManager] Đã kết nối Supabase PostgreSQL (" + host + ":" + port + ") thành công!");
        } catch (Exception e) {
            throw new RuntimeException("Không thể khởi tạo Supabase PostgreSQL DatabaseManager", e);
        }
    }

    public DatabaseManager(String dbPath, String storagePath) {
        this.isPostgres = false;
        this.dbUser = null;
        this.dbPassword = null;
        try {
            Path dbFile = Path.of(dbPath);
            if (dbFile.getParent() != null) {
                Files.createDirectories(dbFile.getParent());
            }
            this.storageDir = Path.of(storagePath);
            Files.createDirectories(this.storageDir);
            this.jdbcUrl = "jdbc:sqlite:" + dbFile.toAbsolutePath();
            initSchemaAndSeed();
            System.out.println("[DatabaseManager] Sử dụng SQLite local: " + dbFile.toAbsolutePath());
        } catch (IOException | SQLException e) {
            throw new RuntimeException("Không thể khởi tạo SQLite DatabaseManager", e);
        }
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = createDefaultInstance();
        }
        return instance;
    }

    private static DatabaseManager createDefaultInstance() {
        String dbUrl = System.getenv("DATABASE_URL");
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = System.getProperty("chat.db.url");
        }
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = loadPropertyFromFiles("database.url");
        }

        if (dbUrl != null && !dbUrl.isBlank() && (dbUrl.startsWith("postgres://") || dbUrl.startsWith("postgresql://") || dbUrl.startsWith("jdbc:postgresql:"))) {
            try {
                System.out.println("[DatabaseManager] Phát hiện cấu hình Supabase / PostgreSQL, đang kết nối Cloud DB...");
                return new DatabaseManager(dbUrl.trim(), "server_storage", true);
            } catch (Exception e) {
                System.err.println("[DatabaseManager] Kết nối Supabase trực tiếp thất bại: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
                if (!dbUrl.contains("pooler.supabase.com")) {
                    try {
                        String poolerUrl = "postgresql://postgres.tlijesqjobgwlkzsgzxy:Gawr-gura206@aws-0-ap-northeast-2.pooler.supabase.com:6543/postgres";
                        System.out.println("[DatabaseManager] Đang chuyển sang Supabase IPv4 Pooler (Seoul)...");
                        return new DatabaseManager(poolerUrl, "server_storage", true);
                    } catch (Exception e2) {
                        System.err.println("[DatabaseManager] Kết nối Pooler thất bại: " + e2.getMessage());
                    }
                }
            }
        }
        return new DatabaseManager("data/chat_server.db", "server_storage");
    }

    private static String loadPropertyFromFiles(String key) {
        String[] paths = {"server.properties", "chat-server/server.properties", "chat-server/src/main/resources/server.properties"};
        for (String p : paths) {
            File f = new File(p);
            if (f.exists() && f.isFile()) {
                try (InputStream in = new FileInputStream(f)) {
                    Properties props = new Properties();
                    props.load(in);
                    String val = props.getProperty(key);
                    if (val != null && !val.isBlank()) return val.trim();
                } catch (IOException ignored) {}
            }
        }
        try (InputStream in = DatabaseManager.class.getResourceAsStream("/server.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String val = props.getProperty(key);
                if (val != null && !val.isBlank()) return val.trim();
            }
        } catch (IOException ignored) {}
        return null;
    }

    private Connection connect() throws SQLException {
        if (isPostgres && dbUser != null && dbPassword != null) {
            boolean needReconnect = (sharedPostgresConn == null);
            if (!needReconnect) {
                try {
                    needReconnect = sharedPostgresConn.isClosed() || !sharedPostgresConn.isValid(2);
                } catch (SQLException e) {
                    needReconnect = true;
                }
            }
            if (needReconnect) {
                if (sharedPostgresConn != null) {
                    try {
                        sharedPostgresConn.close();
                    } catch (SQLException ignored) {
                    }
                }
                sharedPostgresConn = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
            }
            final Connection delegate = sharedPostgresConn;
            return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if ("close".equals(method.getName())) {
                            return null;
                        }
                        try {
                            return method.invoke(delegate, args);
                        } catch (java.lang.reflect.InvocationTargetException ite) {
                            Throwable cause = ite.getTargetException();
                            if (cause instanceof SQLException) {
                                try {
                                    delegate.close();
                                } catch (SQLException ignored) {
                                }
                                sharedPostgresConn = null;
                            }
                            throw cause;
                        }
                    }
            );
        }
        if (dbUser != null && dbPassword != null) {
            return DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
        }
        return DriverManager.getConnection(jdbcUrl);
    }

    public boolean isPostgres() {
        return isPostgres;
    }

    public static String dmThreadId(String userA, String userB) {
        String a = userA == null ? "" : userA.trim().toLowerCase(Locale.ROOT);
        String b = userB == null ? "" : userB.trim().toLowerCase(Locale.ROOT);
        return a.compareTo(b) <= 0 ? ("dm:" + a + "::" + b) : ("dm:" + b + "::" + a);
    }

    private void initSchemaAndSeed() throws SQLException {
        try (Connection conn = connect(); Statement st = conn.createStatement()) {
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

        if (countConversations() == 0) {
            seedDefaultData();
        } else {
            migrateLegacyDmMessages();
        }
    }

    private int countConversations() throws SQLException {
        try (Connection conn = connect();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM conversations")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void migrateLegacyDmMessages() throws SQLException {
        insertUserIfAbsent("mai-phuong", "mai.phuong@example.com", "123456", "Mai Phương",
                "@maiphuong", "Thiết kế sản phẩm · thích vẽ và chơi game cùng bạn bè.", "online");
        insertUserIfAbsent("an-nguyen", "an.nguyen@example.com", "123456", "An Nguyễn",
                "@annguyen", "Lập trình backend · đang thử luồng chat cục bộ.", "offline");
        insertUserIfAbsent("duc-khanh", "duc.khanh@example.com", "123456", "Đức Khánh",
                "@duckhanh", "Bạn học chung · kiểm tra phần giao diện.", "online");
        insertUserIfAbsent("ha-linh", "ha.linh@example.com", "123456", "Hà Linh",
                "@halinh", "Bạn học chung · trò chuyện demo.", "offline");

        Set<String> groupIds = new HashSet<>();
        for (ConversationDto c : listConversations()) {
            if (c.isGroup()) {
                groupIds.add(c.id());
            }
        }

        try (Connection conn = connect()) {
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
                    // Legacy seed message where senderId == targetPeer -> belongs to demo account mai-phuong
                    String peer = "mai-phuong".equals(targetPeer) ? "an-nguyen" : targetPeer;
                    String threadId = dmThreadId("mai-phuong", peer);
                    try (PreparedStatement upd = conn.prepareStatement("UPDATE messages SET conversation_id = ? WHERE id = ?")) {
                        upd.setString(1, threadId);
                        upd.setString(2, msgId);
                        upd.executeUpdate();
                    }
                    linkUserDm(conn, "mai-phuong", peer, sentAt);
                    linkUserDm(conn, peer, "mai-phuong", sentAt);
                } else {
                    String threadId = dmThreadId(senderId, targetPeer);
                    try (PreparedStatement upd = conn.prepareStatement("UPDATE messages SET conversation_id = ? WHERE id = ?")) {
                        upd.setString(1, threadId);
                        upd.setString(2, msgId);
                        upd.executeUpdate();
                    }
                    linkUserDm(conn, senderId, targetPeer, sentAt);
                    linkUserDm(conn, targetPeer, senderId, sentAt);
                }
            }
        }
    }

    private void linkUserDm(Connection conn, String userId, String peerId, String createdAt) throws SQLException {
        if (userId == null || peerId == null || userId.equals(peerId)) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO user_dms(user_id, peer_id, created_at) VALUES(?,?,?) ON CONFLICT (user_id, peer_id) DO NOTHING")) {
            ps.setString(1, userId);
            ps.setString(2, peerId);
            ps.setString(3, createdAt != null ? createdAt : LocalDateTime.now().toString());
            ps.executeUpdate();
        }
    }

    private void seedDefaultData() throws SQLException {
        // Seed default users
        insertUserIfAbsent("mai-phuong", "mai.phuong@example.com", "123456", "Mai Phương",
                "@maiphuong", "Thiết kế sản phẩm · thích vẽ và chơi game cùng bạn bè.", "online");
        insertUserIfAbsent("an-nguyen", "an.nguyen@example.com", "123456", "An Nguyễn",
                "@annguyen", "Lập trình backend · đang thử luồng chat cục bộ.", "offline");
        insertUserIfAbsent("duc-khanh", "duc.khanh@example.com", "123456", "Đức Khánh",
                "@duckhanh", "Bạn học chung · kiểm tra phần giao diện.", "online");
        insertUserIfAbsent("ha-linh", "ha.linh@example.com", "123456", "Hà Linh",
                "@halinh", "Bạn học chung · trò chuyện demo.", "offline");

        // Seed Direct Messages (User directory entries)
        insertConversation(new ConversationDto("mai-phuong", "Mai Phương", "MP",
                "Thiết kế sản phẩm · thích vẽ và chơi game cùng bạn bè.", true, false,
                List.of(), List.of(), List.of()));
        insertConversation(new ConversationDto("an-nguyen", "An Nguyễn", "AN",
                "Lập trình backend · đang thử luồng chat cục bộ.", false, false,
                List.of(), List.of(), List.of()));
        insertConversation(new ConversationDto("duc-khanh", "Đức Khánh", "ĐK",
                "Bạn học chung · kiểm tra phần giao diện.", true, false,
                List.of(), List.of(), List.of()));
        insertConversation(new ConversationDto("ha-linh", "Hà Linh", "HL",
                "Bạn học chung · trò chuyện demo.", false, false,
                List.of(), List.of(), List.of()));

        // Seed Groups (scoped to demo members)
        insertConversation(new ConversationDto("nhom-ban-than", "Nhóm bạn thân", "HB",
                "Cùng nhau học, cùng nhau tiến bộ.", true, true,
                List.of("Mai Phương", "An Nguyễn", "Thảo Nguyễn", "Mai Linh", "Bảo Long", "Đức Minh"),
                List.of("tổng-quan", "học-tập", "ảnh-chia-sẻ"),
                List.of("Phòng chill", "Góc học chung")));

        insertConversation(new ConversationDto("ltm-studio", "LTM Studio", "LT",
                "Nhóm phát triển hệ thống mạng máy tính.", true, true,
                List.of("Mai Phương", "An Nguyễn", "Minh Anh", "Khánh Linh", "Hữu Phúc", "Thành Đạt"),
                List.of("chung", "chia-sẻ-tài-liệu"),
                List.of("phòng-họp", "Thảo luận nhanh")));

        insertConversation(new ConversationDto("phong-chill", "Hội Bạn Chill", "CL",
                "Góc giải trí và trò chuyện cuối tuần.", false, true,
                List.of("Mai Phương", "Hà My", "Quốc Thái", "Thanh Duy"),
                List.of("trò-chuyện", "âm-nhạc"),
                List.of("Phòng nghe nhạc")));

        LocalDateTime now = LocalDateTime.now();

        // Seed DM links & messages only between demo accounts (mai-phuong <-> an-nguyen, duc-khanh, ha-linh)
        try (Connection conn = connect()) {
            linkUserDm(conn, "mai-phuong", "an-nguyen", now.minusHours(2).toString());
            linkUserDm(conn, "an-nguyen", "mai-phuong", now.minusHours(2).toString());
            linkUserDm(conn, "mai-phuong", "duc-khanh", now.minusHours(5).toString());
            linkUserDm(conn, "duc-khanh", "mai-phuong", now.minusHours(5).toString());
            linkUserDm(conn, "mai-phuong", "ha-linh", now.minusDays(1).toString());
            linkUserDm(conn, "ha-linh", "mai-phuong", now.minusDays(1).toString());
        }

        insertRawMessage(new MessageDto(UUID.randomUUID().toString(), dmThreadId("mai-phuong", "an-nguyen"), "",
                "an-nguyen", "An Nguyễn", "AN",
                "Mình có thể chọn cuộc trò chuyện ở danh sách bên trái.", now.minusHours(2).toString(), false, false, null, 0, false));
        insertRawMessage(new MessageDto(UUID.randomUUID().toString(), dmThreadId("mai-phuong", "duc-khanh"), "",
                "duc-khanh", "Đức Khánh", "ĐK",
                "Hệ thống hiện đã kết nối trực tiếp với SQLite database trên Server.", now.minusHours(5).toString(), false, false, null, 0, false));
        insertRawMessage(new MessageDto(UUID.randomUUID().toString(), dmThreadId("mai-phuong", "ha-linh"), "",
                "ha-linh", "Hà Linh", "HL",
                "Chào bạn! Đây là hội thoại được đồng bộ từ máy chủ.", now.minusDays(1).toString(), false, false, null, 0, false));

        // Seed Group 1: #học-tập
        insertRawMessage(new MessageDto(UUID.randomUUID().toString(), "nhom-ban-than", "học-tập", "mai-linh", "Mai Linh", "ML",
                "Mình gom tài liệu ôn tập vào đây nhé. Mọi người xem thử kế hoạch tuần này có cần thêm gì không?",
                now.minusMinutes(25).toString(), false, false, null, 0, false));
        insertRawMessage(new MessageDto(UUID.randomUUID().toString(), "nhom-ban-than", "học-tập", "mai-linh", "Mai Linh", "ML",
                "Đây là góc học tập mình vừa sắp xếp lại, gửi mọi người xem bố cục nha.",
                now.minusMinutes(23).toString(), false, false, "setup-moi.jpg", 1_800_000L, false));
        insertRawMessage(new MessageDto(UUID.randomUUID().toString(), "nhom-ban-than", "học-tập", "bao-long", "Bảo Long", "BL",
                "Mình gửi bản tổng hợp buổi học hôm qua ở đây. Mọi người tải về xem nhé.",
                now.minusMinutes(15).toString(), false, false, "Tổng_hợp_bài_giảng_tuần_04.pdf", 2_400_000L, false));
        insertRawMessage(new MessageDto(UUID.randomUUID().toString(), "nhom-ban-than", "học-tập", "duc-minh", "Đức Minh", "ĐM",
                "Đẹp đó Linh, mình sẽ thêm lịch ôn vào tối thứ năm.",
                now.minusMinutes(10).toString(), false, false, null, 0, false));

        // Seed Group 1: #tổng-quan
        insertRawMessage(new MessageDto(UUID.randomUUID().toString(), "nhom-ban-than", "tổng-quan", "thao-nguyen", "Thảo Nguyễn", "TN",
                "Chào mừng mọi người đến với kênh tổng quan của nhóm bạn thân!",
                now.minusHours(3).toString(), false, false, null, 0, false));
        insertRawMessage(new MessageDto(UUID.randomUUID().toString(), "nhom-ban-than", "tổng-quan", "duc-minh", "Đức Minh", "ĐM",
                "Tuần này chúng ta sẽ tập trung hoàn thiện bài tập lớn LTM nhé.",
                now.minusHours(2).toString(), false, false, null, 0, false));

        // Seed Group 1: #ảnh-chia-sẻ
        insertRawMessage(new MessageDto(UUID.randomUUID().toString(), "nhom-ban-than", "ảnh-chia-sẻ", "bao-long", "Bảo Long", "BL",
                "Kênh lưu trữ hình ảnh kỷ niệm của nhóm.",
                now.minusDays(2).toString(), false, false, null, 0, false));

        // Seed Group 2: #chung
        insertRawMessage(new MessageDto(UUID.randomUUID().toString(), "ltm-studio", "chung", "minh-anh", "Minh Anh", "MA",
                "Chào mừng team LTM Studio! Báo cáo tiến độ các module tại đây.",
                now.minusHours(4).toString(), false, false, null, 0, false));
    }

    private void insertUserIfAbsent(String id, String email, String password, String displayName,
                                    String username, String bio, String status) throws SQLException {
        String sql = "INSERT INTO users(id, email, password, display_name, username, bio, status) VALUES(?,?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
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

    public synchronized UserDto authenticateOrRegister(String email, String password, boolean isRegister, String customDisplayName) {
        String normEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normEmail.isBlank() || !normEmail.contains("@")) {
            throw new IllegalArgumentException("Nhập địa chỉ email hợp lệ.");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Mật khẩu không được để trống.");
        }

        try (Connection conn = connect()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, email, password, display_name, username, bio, status, avatar_path FROM users WHERE email = ?")) {
                ps.setString(1, normEmail);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        if (isRegister) {
                            throw new IllegalArgumentException("Email này đã được đăng ký tài khoản.");
                        }
                        String storedPass = rs.getString("password");
                        if (!storedPass.equals(password)) {
                            throw new IllegalArgumentException("Mật khẩu không chính xác.");
                        }
                        return new UserDto(
                                rs.getString("id"),
                                rs.getString("display_name"),
                                rs.getString("email"),
                                rs.getString("username"),
                                rs.getString("bio"),
                                "online",
                                rs.getString("avatar_path")
                        );
                    }
                }
            }

            if (!isRegister) {
                throw new IllegalArgumentException("Tài khoản không tồn tại. Vui lòng kiểm tra lại email hoặc bấm 'Tạo tài khoản' để đăng ký.");
            }

            // Explicit register only
            String handle = normEmail.substring(0, normEmail.indexOf('@'));
            String userId = handle.replaceAll("[^a-zA-Z0-9_-]", "-");
            if (findUserById(conn, userId)) {
                userId = userId + "-" + UUID.randomUUID().toString().substring(0, 4);
            }
            String displayName = (customDisplayName != null && !customDisplayName.isBlank())
                    ? customDisplayName.trim()
                    : toDisplayName(handle);
            String username = "@" + handle.replaceAll("[^a-zA-Z0-9_]", "");
            String bio = "Thích trò chuyện, nghe nhạc và chơi game cùng bạn bè. ☁️";

            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO users(id, email, password, display_name, username, bio, status) VALUES(?,?,?,?,?,?,?)")) {
                ins.setString(1, userId);
                ins.setString(2, normEmail);
                ins.setString(3, password);
                ins.setString(4, displayName);
                ins.setString(5, username);
                ins.setString(6, bio);
                ins.setString(7, "online");
                ins.executeUpdate();
            }

            // Ensure a directory entry exists so other users can start a 1-1 DM with this user
            String initials = computeInitials(displayName);
            insertConversationIfAbsent(new ConversationDto(
                    userId, displayName, initials, bio, true, false, List.of(), List.of(), List.of()
            ));

            return new UserDto(userId, displayName, normEmail, username, bio, "online", null);
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi truy vấn CSDL khi xác thực: " + e.getMessage(), e);
        }
    }

    private boolean findUserById(Connection conn, String id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM users WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public synchronized UserDto findUser(String userId) {
        if (userId == null || userId.isBlank()) return null;
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, email, display_name, username, bio, status, avatar_path FROM users WHERE id = ?")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new UserDto(
                            rs.getString("id"),
                            rs.getString("display_name"),
                            rs.getString("email"),
                            rs.getString("username"),
                            rs.getString("bio"),
                            rs.getString("status"),
                            rs.getString("avatar_path")
                    );
                }
            }
        } catch (SQLException ignored) {
        }
        return null;
    }

    public synchronized List<UserDto> listUsers(String excludeUserId) {
        List<UserDto> users = new ArrayList<>();
        String sql = "SELECT id, email, display_name, username, bio, status, avatar_path FROM users ORDER BY display_name ASC";
        try (Connection conn = connect();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String id = rs.getString("id");
                if (excludeUserId != null && excludeUserId.equals(id)) {
                    continue;
                }
                users.add(new UserDto(
                        id,
                        rs.getString("display_name"),
                        rs.getString("email"),
                        rs.getString("username"),
                        rs.getString("bio"),
                        rs.getString("status"),
                        rs.getString("avatar_path")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi tải danh sách người dùng: " + e.getMessage(), e);
        }
        return users;
    }

    public synchronized ConversationDto openDirectMessage(String userId, String peerId) {
        if (userId == null || userId.isBlank() || peerId == null || peerId.isBlank()) {
            throw new IllegalArgumentException("Thông tin người nhận không hợp lệ.");
        }
        try (Connection conn = connect()) {
            linkUserDm(conn, userId, peerId, LocalDateTime.now().toString());
        } catch (SQLException e) {
            throw new RuntimeException("Không thể mở cuộc trò chuyện: " + e.getMessage(), e);
        }
        ConversationDto conv = getConversation(peerId);
        if (conv != null) {
            return conv;
        }
        UserDto peer = findUser(peerId);
        if (peer != null) {
            ConversationDto dto = new ConversationDto(
                    peer.id(), peer.displayName(), computeInitials(peer.displayName()),
                    peer.bio(), !"offline".equals(peer.status()), false, List.of(), List.of(), List.of()
            );
            try {
                insertConversationIfAbsent(dto);
            } catch (SQLException ignored) {
            }
            return dto;
        }
        throw new IllegalArgumentException("Không tìm thấy người dùng: " + peerId);
    }

    public synchronized UserDto updateProfile(UserDto updated) {
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE users SET display_name=?, username=?, bio=?, status=?, avatar_path=? WHERE id=?")) {
            ps.setString(1, updated.displayName());
            ps.setString(2, updated.username());
            ps.setString(3, updated.bio());
            ps.setString(4, updated.status());
            ps.setString(5, updated.avatarPath());
            ps.setString(6, updated.id());
            ps.executeUpdate();

            try (PreparedStatement convPs = conn.prepareStatement(
                    "UPDATE conversations SET display_name=?, initials=?, about=?, online=? WHERE id=? AND is_group=0")) {
                convPs.setString(1, updated.displayName());
                convPs.setString(2, computeInitials(updated.displayName()));
                convPs.setString(3, updated.bio());
                convPs.setInt(4, "offline".equals(updated.status()) ? 0 : 1);
                convPs.setString(5, updated.id());
                convPs.executeUpdate();
            }
            return updated;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi cập nhật hồ sơ: " + e.getMessage(), e);
        }
    }

    public synchronized void updateUserOnlineStatus(String userId, boolean online) {
        try (Connection conn = connect()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE conversations SET online = ? WHERE id = ? AND is_group = 0")) {
                ps.setInt(1, online ? 1 : 0);
                ps.setString(2, userId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps2 = conn.prepareStatement(
                    "UPDATE users SET status = ? WHERE id = ?")) {
                ps2.setString(1, online ? "online" : "offline");
                ps2.setString(2, userId);
                ps2.executeUpdate();
            }
        } catch (SQLException ignored) {
        }
    }

    public synchronized void insertConversation(ConversationDto c) throws SQLException {
        String sql = """
            INSERT INTO conversations(id, display_name, initials, about, online, is_group, members_json, channels_json, voice_channels_json)
            VALUES(?,?,?,?,?,?,?,?,?)
            ON CONFLICT (id) DO UPDATE SET
                display_name = EXCLUDED.display_name,
                initials = EXCLUDED.initials,
                about = EXCLUDED.about,
                online = EXCLUDED.online,
                is_group = EXCLUDED.is_group,
                members_json = EXCLUDED.members_json,
                channels_json = EXCLUDED.channels_json,
                voice_channels_json = EXCLUDED.voice_channels_json
        """;
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.id());
            ps.setString(2, c.displayName());
            ps.setString(3, c.initials());
            ps.setString(4, c.about());
            ps.setInt(5, c.online() ? 1 : 0);
            ps.setInt(6, c.isGroup() ? 1 : 0);
            ps.setString(7, GSON.toJson(c.members()));
            ps.setString(8, GSON.toJson(c.channels()));
            ps.setString(9, GSON.toJson(c.voiceChannels()));
            ps.executeUpdate();
        }
    }

    private void insertConversationIfAbsent(ConversationDto c) throws SQLException {
        String sql = "INSERT INTO conversations(id, display_name, initials, about, online, is_group, members_json, channels_json, voice_channels_json) VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.id());
            ps.setString(2, c.displayName());
            ps.setString(3, c.initials());
            ps.setString(4, c.about());
            ps.setInt(5, c.online() ? 1 : 0);
            ps.setInt(6, c.isGroup() ? 1 : 0);
            ps.setString(7, GSON.toJson(c.members()));
            ps.setString(8, GSON.toJson(c.channels()));
            ps.setString(9, GSON.toJson(c.voiceChannels()));
            ps.executeUpdate();
        }
    }

    public synchronized List<ConversationDto> listConversations() {
        List<ConversationDto> list = new ArrayList<>();
        String sql = "SELECT id, display_name, initials, about, online, is_group, members_json, channels_json, voice_channels_json FROM conversations";
        try (Connection conn = connect();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String[] members = GSON.fromJson(rs.getString("members_json"), String[].class);
                String[] channels = GSON.fromJson(rs.getString("channels_json"), String[].class);
                String[] voiceChannels = GSON.fromJson(rs.getString("voice_channels_json"), String[].class);
                list.add(new ConversationDto(
                        rs.getString("id"),
                        rs.getString("display_name"),
                        rs.getString("initials"),
                        rs.getString("about"),
                        rs.getInt("online") != 0,
                        rs.getInt("is_group") != 0,
                        members != null ? Arrays.asList(members) : List.of(),
                        channels != null ? Arrays.asList(channels) : List.of(),
                        voiceChannels != null ? Arrays.asList(voiceChannels) : List.of()
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi tải danh sách hội thoại: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * Returns ONLY the conversations belonging to {@code userId}:
     * 1. Direct Messages (1-1) that {@code userId} has opened or received messages in ({@code user_dms}).
     * 2. Groups where {@code userId} (or their display name) is a member.
     */
    public synchronized List<ConversationDto> listConversationsForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return listConversations();
        }
        UserDto user = findUser(userId);
        String userDisplayName = user != null ? user.displayName() : "";

        Set<String> allowedDmPeers = new LinkedHashSet<>();
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT peer_id FROM user_dms WHERE user_id = ? ORDER BY created_at ASC")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String peerId = rs.getString("peer_id");
                    if (peerId != null && !peerId.equals(userId)) {
                        allowedDmPeers.add(peerId);
                    }
                }
            }
        } catch (SQLException ignored) {
        }

        List<ConversationDto> all = listConversations();
        List<ConversationDto> result = new ArrayList<>();

        // 1. Add user's active DMs in order
        for (String peerId : allowedDmPeers) {
            for (ConversationDto c : all) {
                if (!c.isGroup() && c.id().equals(peerId)) {
                    result.add(c);
                    break;
                }
            }
        }

        // 2. Add groups where user is a member
        for (ConversationDto c : all) {
            if (c.isGroup() && isMemberOfGroup(userId, userDisplayName, c.members())) {
                result.add(c);
            }
        }
        return result;
    }

    private boolean isMemberOfGroup(String userId, String displayName, List<String> members) {
        if (members == null || members.isEmpty()) return false;
        for (String m : members) {
            if (m == null) continue;
            if (m.equalsIgnoreCase(userId) || (!displayName.isBlank() && m.equalsIgnoreCase(displayName))) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean isGroupConversation(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return false;
        ConversationDto c = getConversation(conversationId);
        return c != null && c.isGroup();
    }

    public synchronized ConversationDto getConversation(String id) {
        return listConversations().stream()
                .filter(c -> c.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    public synchronized ConversationDto createGroup(String creatorId, String creatorName, String groupName, List<String> members) {
        String id = "group-" + UUID.randomUUID().toString().substring(0, 8);
        String name = (groupName == null || groupName.isBlank()) ? "Nhóm mới" : groupName.trim();
        String initials = computeInitials(name);

        List<String> finalMembers = new ArrayList<>();
        if (creatorName != null && !creatorName.isBlank()) {
            finalMembers.add(creatorName);
        } else if (creatorId != null && !creatorId.isBlank()) {
            finalMembers.add(creatorId);
        }
        if (members != null) {
            for (String m : members) {
                if (m != null && !m.isBlank() && finalMembers.stream().noneMatch(x -> x.equalsIgnoreCase(m))) {
                    finalMembers.add(m);
                }
            }
        }

        ConversationDto dto = new ConversationDto(
                id, name, initials, "Nhóm trò chuyện trên máy chủ", true, true,
                finalMembers,
                List.of("tổng-quan", "học-tập"),
                List.of("Phòng chill")
        );
        try {
            insertConversation(dto);
            return dto;
        } catch (SQLException e) {
            throw new RuntimeException("Không thể tạo nhóm: " + e.getMessage(), e);
        }
    }

    public synchronized ConversationDto addChannel(String conversationId, String channelName, boolean isVoice) {
        ConversationDto c = getConversation(conversationId);
        if (c == null || !c.isGroup()) return null;

        List<String> channels = new ArrayList<>(c.channels());
        List<String> voiceChannels = new ArrayList<>(c.voiceChannels());
        if (isVoice) {
            if (!voiceChannels.contains(channelName)) voiceChannels.add(channelName);
        } else {
            if (!channels.contains(channelName)) channels.add(channelName);
        }
        ConversationDto updated = new ConversationDto(c.id(), c.displayName(), c.initials(), c.about(),
                c.online(), true, c.members(), channels, voiceChannels);
        try {
            insertConversation(updated);
            return updated;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized ConversationDto removeChannel(String conversationId, String channelName, boolean isVoice) {
        ConversationDto c = getConversation(conversationId);
        if (c == null || !c.isGroup()) return null;

        List<String> channels = new ArrayList<>(c.channels());
        List<String> voiceChannels = new ArrayList<>(c.voiceChannels());
        if (isVoice) {
            voiceChannels.remove(channelName);
            if (voiceChannels.isEmpty()) voiceChannels.add("Phòng họp");
        } else {
            channels.remove(channelName);
            if (channels.isEmpty()) channels.add("chung");
        }
        ConversationDto updated = new ConversationDto(c.id(), c.displayName(), c.initials(), c.about(),
                c.online(), true, c.members(), channels, voiceChannels);
        try {
            insertConversation(updated);
            return updated;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Saves a message and links 1-1 DM participants if it's a direct message.
     */
    public synchronized void saveMessage(MessageDto m) {
        String storageConvId = m.conversationId();
        boolean isGroup = isGroupConversation(storageConvId);
        if (!isGroup && storageConvId != null && !storageConvId.startsWith("dm:")
                && m.senderId() != null && !m.senderId().isBlank()) {
            String peerId = storageConvId;
            storageConvId = dmThreadId(m.senderId(), peerId);
            try (Connection conn = connect()) {
                linkUserDm(conn, m.senderId(), peerId, m.sentAt());
                linkUserDm(conn, peerId, m.senderId(), m.sentAt());
            } catch (SQLException ignored) {
            }
        }

        MessageDto toStore = new MessageDto(
                m.id(), storageConvId, m.channel(), m.senderId(), m.senderName(),
                m.senderInitials(), m.content(), m.sentAt(), m.edited(), m.recalled(),
                m.attachmentName(), m.attachmentSize(), m.attachmentFailed()
        );
        insertRawMessage(toStore);
    }

    private void insertRawMessage(MessageDto m) {
        String sql = """
            INSERT INTO messages(
                id, conversation_id, channel, sender_id, sender_name, sender_initials,
                content, sent_at, edited, recalled, attachment_name, attachment_size, attachment_failed
            ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (id) DO UPDATE SET
                content = EXCLUDED.content,
                edited = EXCLUDED.edited,
                recalled = EXCLUDED.recalled,
                attachment_name = EXCLUDED.attachment_name,
                attachment_size = EXCLUDED.attachment_size,
                attachment_failed = EXCLUDED.attachment_failed
        """;
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, m.id());
            ps.setString(2, m.conversationId());
            ps.setString(3, m.channel() == null ? "" : m.channel());
            ps.setString(4, m.senderId());
            ps.setString(5, m.senderName());
            ps.setString(6, m.senderInitials());
            ps.setString(7, m.content());
            ps.setString(8, m.sentAt());
            ps.setInt(9, m.edited() ? 1 : 0);
            ps.setInt(10, m.recalled() ? 1 : 0);
            ps.setString(11, m.attachmentName());
            ps.setLong(12, m.attachmentSize());
            ps.setInt(13, m.attachmentFailed() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi lưu tin nhắn vào SQLite: " + e.getMessage(), e);
        }
    }

    public synchronized List<MessageDto> loadMessagesForUser(String userId, String conversationId, String channel) {
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }
        String storageConvId = conversationId;
        boolean isDm = !isGroupConversation(conversationId) && !conversationId.startsWith("dm:");
        if (isDm && userId != null && !userId.isBlank()) {
            storageConvId = dmThreadId(userId, conversationId);
        }

        List<MessageDto> rawList = loadRawMessages(storageConvId, channel);
        if (!isDm) {
            return rawList;
        }
        // Map storage conversation_id ("dm:userA::userB") back to peerId (conversationId) for the client UI
        List<MessageDto> mapped = new ArrayList<>(rawList.size());
        for (MessageDto m : rawList) {
            mapped.add(new MessageDto(
                    m.id(), conversationId, m.channel(), m.senderId(), m.senderName(),
                    m.senderInitials(), m.content(), m.sentAt(), m.edited(), m.recalled(),
                    m.attachmentName(), m.attachmentSize(), m.attachmentFailed()
            ));
        }
        return mapped;
    }

    public synchronized List<MessageDto> loadMessages(String conversationId, String channel) {
        return loadRawMessages(conversationId, channel);
    }

    private List<MessageDto> loadRawMessages(String storageConversationId, String channel) {
        List<MessageDto> list = new ArrayList<>();
        String chan = channel == null ? "" : channel;
        String sql = """
            SELECT id, conversation_id, channel, sender_id, sender_name, sender_initials,
                   content, sent_at, edited, recalled, attachment_name, attachment_size, attachment_failed
            FROM messages
            WHERE conversation_id = ? AND channel = ?
            ORDER BY sent_at ASC, id ASC
        """;
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, storageConversationId);
            ps.setString(2, chan);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapMessage(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi tải lịch sử tin nhắn: " + e.getMessage(), e);
        }
        return list;
    }

    public synchronized MessageDto editMessage(String messageId, String newContent) {
        try (Connection conn = connect()) {
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE messages SET content = ?, edited = 1 WHERE id = ?")) {
                upd.setString(1, newContent);
                upd.setString(2, messageId);
                int rows = upd.executeUpdate();
                if (rows == 0) {
                    throw new IllegalArgumentException("Không tìm thấy tin nhắn cần sửa.");
                }
            }
            return findMessageById(conn, messageId);
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi sửa tin nhắn: " + e.getMessage(), e);
        }
    }

    public synchronized MessageDto recallMessage(String messageId) {
        try (Connection conn = connect()) {
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE messages SET content = 'Tin nhắn đã được thu hồi', recalled = 1, edited = 0, attachment_name = NULL, attachment_size = 0, attachment_failed = 0 WHERE id = ?")) {
                upd.setString(1, messageId);
                int rows = upd.executeUpdate();
                if (rows == 0) {
                    throw new IllegalArgumentException("Không tìm thấy tin nhắn cần thu hồi.");
                }
            }
            return findMessageById(conn, messageId);
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi thu hồi tin nhắn: " + e.getMessage(), e);
        }
    }

    public synchronized void deleteMessage(String messageId) {
        try (Connection conn = connect();
             PreparedStatement del = conn.prepareStatement("DELETE FROM messages WHERE id = ?")) {
            del.setString(1, messageId);
            del.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi xóa tin nhắn: " + e.getMessage(), e);
        }
    }

    private MessageDto findMessageById(Connection conn, String messageId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, conversation_id, channel, sender_id, sender_name, sender_initials, content, sent_at, edited, recalled, attachment_name, attachment_size, attachment_failed FROM messages WHERE id = ?")) {
            ps.setString(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapMessage(rs);
                }
            }
        }
        throw new IllegalArgumentException("Không tìm thấy tin nhắn: " + messageId);
    }

    private MessageDto mapMessage(ResultSet rs) throws SQLException {
        return new MessageDto(
                rs.getString("id"),
                rs.getString("conversation_id"),
                rs.getString("channel"),
                rs.getString("sender_id"),
                rs.getString("sender_name"),
                rs.getString("sender_initials"),
                rs.getString("content"),
                rs.getString("sent_at"),
                rs.getInt("edited") != 0,
                rs.getInt("recalled") != 0,
                rs.getString("attachment_name"),
                rs.getLong("attachment_size"),
                rs.getInt("attachment_failed") != 0
        );
    }

    public synchronized void storePhysicalFile(String messageId, String fileName, String base64Data) {
        if (base64Data == null || base64Data.isBlank()) return;
        try {
            String safeName = new File(fileName).getName();
            Path filePath = storageDir.resolve(messageId + "_" + safeName);
            byte[] bytes = Base64.getDecoder().decode(base64Data);
            Files.write(filePath, bytes);
        } catch (Exception ignored) {
        }
    }

    public synchronized String readPhysicalFileBase64(String messageId, String fileName) {
        try {
            String safeName = new File(fileName).getName();
            Path filePath = storageDir.resolve(messageId + "_" + safeName);
            if (Files.exists(filePath)) {
                return Base64.getEncoder().encodeToString(Files.readAllBytes(filePath));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static String computeInitials(String displayName) {
        if (displayName == null || displayName.isBlank()) return "U";
        String[] parts = displayName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.ROOT);
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }

    private static String toDisplayName(String handle) {
        String[] parts = handle.replaceAll("[._-]+", " ").trim().split("\\s+");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank()) {
                if (!name.isEmpty()) name.append(' ');
                name.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
                if (part.length() > 1) name.append(part.substring(1));
            }
        }
        return name.isEmpty() ? "Người dùng" : name.toString();
    }
}

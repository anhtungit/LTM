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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
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
public final class DatabaseManager implements AutoCloseable {
    private static final Gson GSON = new Gson();
    private static DatabaseManager instance;

    static {
        try {
            DriverManager.registerDriver(new org.postgresql.Driver());
        } catch (Throwable ignored) {
            try {
                Class.forName("org.postgresql.Driver");
            } catch (ClassNotFoundException ignored2) {
            }
        }
        try {
            DriverManager.registerDriver(new org.sqlite.JDBC());
        } catch (Throwable ignored) {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException ignored2) {
            }
        }
    }

    private final String jdbcUrl;
    private final String dbUser;
    private final String dbPassword;
    private final boolean isPostgres;
    private final FileStorageRepository fileStorage;
    private final Map<String, ConversationDto> conversationCache = new java.util.concurrent.ConcurrentHashMap<>();
    private Connection sharedPostgresConn;
    private long lastValidCheckMs = 0L;

    public DatabaseManager(String rawPgUrl, String storagePath, boolean isPostgres) {
        this.isPostgres = isPostgres;
        this.fileStorage = new FileStorageRepository(Path.of(storagePath));
        try {
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
            this.fileStorage = new FileStorageRepository(Path.of(storagePath));
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
                String poolerUrl = System.getenv("DATABASE_POOLER_URL");
                if (poolerUrl == null || poolerUrl.isBlank()) {
                    poolerUrl = "postgresql://postgres.tlijesqjobgwlkzsgzxy:Gawr-gura206@aws-0-ap-northeast-2.pooler.supabase.com:6543/postgres";
                }
                if (!dbUrl.contains("pooler.supabase.com")) {
                    try {
                        System.out.println("[DatabaseManager] Đang chuyển sang Supabase IPv4 Pooler (Seoul)...");
                        return new DatabaseManager(poolerUrl.trim(), "server_storage", true);
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

    private synchronized Connection connect() throws SQLException {
        long now = System.currentTimeMillis();
        boolean needReconnect = (sharedPostgresConn == null);
        if (!needReconnect) {
            try {
                boolean checkValid = isPostgres && (now - lastValidCheckMs > 10_000L);
                needReconnect = sharedPostgresConn.isClosed() || (checkValid && !sharedPostgresConn.isValid(2));
                if (checkValid && !needReconnect) {
                    lastValidCheckMs = now;
                }
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
            sharedPostgresConn = (dbUser != null && dbPassword != null)
                    ? DriverManager.getConnection(jdbcUrl, dbUser, dbPassword)
                    : DriverManager.getConnection(jdbcUrl);
            lastValidCheckMs = now;
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

    public boolean isPostgres() {
        return isPostgres;
    }

    @Override
    public synchronized void close() {
        if (sharedPostgresConn != null) {
            try {
                sharedPostgresConn.close();
            } catch (SQLException ignored) {
            }
            sharedPostgresConn = null;
        }
    }

    public static String dmThreadId(String userA, String userB) {
        String a = userA == null ? "" : userA.trim().toLowerCase(Locale.ROOT);
        String b = userB == null ? "" : userB.trim().toLowerCase(Locale.ROOT);
        return a.compareTo(b) <= 0 ? ("dm:" + a + "::" + b) : ("dm:" + b + "::" + a);
    }

    private void initSchemaAndSeed() throws SQLException {
        try (Connection conn = connect()) {
            DatabaseSchemaMigrator.initialize(this, conn);
        }
        try {
            listConversations();
        } catch (Exception ignored) {
        }
    }

    void linkUserDm(Connection conn, String userId, String peerId, String createdAt) throws SQLException {
        if (userId == null || peerId == null || userId.equals(peerId)) return;
        String time = createdAt != null ? createdAt : LocalDateTime.now().toString();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO user_dms(user_id, peer_id, created_at) VALUES(?,?,?) " +
                "ON CONFLICT (user_id, peer_id) DO UPDATE SET created_at = EXCLUDED.created_at")) {
            ps.setString(1, userId);
            ps.setString(2, peerId);
            ps.setString(3, time);
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

    public synchronized void closeDirectMessage(String userId, String peerId) {
        if (userId == null || userId.isBlank() || peerId == null || peerId.isBlank()) {
            return;
        }
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM user_dms WHERE user_id = ? AND peer_id = ?")) {
            ps.setString(1, userId);
            ps.setString(2, peerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Không thể đóng cuộc trò chuyện: " + e.getMessage());
        }
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
        if (c.id() != null) {
            conversationCache.put(c.id(), c);
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
        if (c.id() != null) {
            conversationCache.putIfAbsent(c.id(), c);
        }
    }

    private ConversationDto mapConversation(ResultSet rs) throws SQLException {
        String[] members = GSON.fromJson(rs.getString("members_json"), String[].class);
        String[] channels = GSON.fromJson(rs.getString("channels_json"), String[].class);
        String[] voiceChannels = GSON.fromJson(rs.getString("voice_channels_json"), String[].class);
        ConversationDto dto = new ConversationDto(
                rs.getString("id"),
                rs.getString("display_name"),
                rs.getString("initials"),
                rs.getString("about"),
                rs.getInt("online") != 0,
                rs.getInt("is_group") != 0,
                members != null ? Arrays.asList(members) : List.of(),
                channels != null ? Arrays.asList(channels) : List.of(),
                voiceChannels != null ? Arrays.asList(voiceChannels) : List.of()
        );
        if (dto.id() != null) {
            conversationCache.put(dto.id(), dto);
        }
        return dto;
    }

    public synchronized List<ConversationDto> listConversations() {
        List<ConversationDto> list = new ArrayList<>();
        String sql = "SELECT id, display_name, initials, about, online, is_group, members_json, channels_json, voice_channels_json FROM conversations";
        try (Connection conn = connect();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapConversation(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi tải danh sách hội thoại: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * Returns ONLY the conversations belonging to {@code userId}:
     * 1. Direct Messages (1-1) that {@code userId} has opened or received messages in ({@code user_dms}).
     * 2. Groups where {@code userId} is a member.
     */
    public synchronized List<ConversationDto> listConversationsForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        Set<String> allowedDmPeers = new LinkedHashSet<>();
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT peer_id FROM user_dms WHERE user_id = ? ORDER BY created_at DESC")) {
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

        // 1. Add user's active DMs in descending order of activity
        for (String peerId : allowedDmPeers) {
            for (ConversationDto c : all) {
                if (!c.isGroup() && c.id().equals(peerId)) {
                    result.add(c);
                    break;
                }
            }
        }

        // Query max sent_at for groups
        Map<String, String> lastMessageTime = new HashMap<>();
        try (Connection conn = connect();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT conversation_id, MAX(sent_at) AS last_sent FROM messages GROUP BY conversation_id")) {
            while (rs.next()) {
                String cId = rs.getString("conversation_id");
                String lSent = rs.getString("last_sent");
                if (cId != null && lSent != null) {
                    lastMessageTime.put(cId, lSent);
                }
            }
        } catch (SQLException ignored) {
        }

        // 2. Add groups where user is a member, sorted by latest activity descending
        List<ConversationDto> userGroups = new ArrayList<>();
        for (ConversationDto c : all) {
            if (c.isGroup() && isUserMemberOfGroup(userId, c)) {
                userGroups.add(c);
            }
        }
        userGroups.sort((a, b) -> lastMessageTime.getOrDefault(b.id(), "").compareTo(lastMessageTime.getOrDefault(a.id(), "")));
        result.addAll(userGroups);

        return result;
    }

    public boolean isUserMemberOfGroup(String userId, String groupConvId) {
        if (userId == null || userId.isBlank() || groupConvId == null || groupConvId.isBlank()) {
            return false;
        }
        return isUserMemberOfGroup(userId, getConversation(groupConvId));
    }

    private boolean isUserMemberOfGroup(String userId, ConversationDto conv) {
        if (userId == null || userId.isBlank() || conv == null || !conv.isGroup()) {
            return false;
        }
        String groupConvId = conv.id();
        // Demo seed groups only belong to demo accounts (mai-phuong, an-nguyen)
        if ("nhom-ban-than".equals(groupConvId) || "ltm-studio".equals(groupConvId) || "phong-chill".equals(groupConvId)) {
            return "mai-phuong".equalsIgnoreCase(userId) || "an-nguyen".equalsIgnoreCase(userId);
        }
        List<String> members = conv.members();
        if (members == null || members.isEmpty()) {
            return false;
        }
        for (String m : members) {
            if (m != null && m.equalsIgnoreCase(userId)) {
                return true;
            }
        }
        return false;
    }

    public boolean isGroupConversation(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return false;
        ConversationDto c = getConversation(conversationId);
        return c != null && c.isGroup();
    }

    public ConversationDto getConversation(String id) {
        if (id == null || id.isBlank()) return null;
        ConversationDto cached = conversationCache.get(id);
        if (cached != null) {
            return cached;
        }
        return queryConversationFromDb(id);
    }

    private synchronized ConversationDto queryConversationFromDb(String id) {
        ConversationDto cached = conversationCache.get(id);
        if (cached != null) {
            return cached;
        }
        String sql = "SELECT id, display_name, initials, about, online, is_group, members_json, channels_json, voice_channels_json FROM conversations WHERE id = ?";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapConversation(rs);
                }
            }
        } catch (SQLException ignored) {
        }
        return null;
    }

    public synchronized ConversationDto createGroup(String creatorId, String creatorName, String groupName, List<String> members) {
        String id = "group-" + UUID.randomUUID().toString().substring(0, 8);
        String name = (groupName == null || groupName.isBlank()) ? "Nhóm mới" : groupName.trim();
        String initials = computeInitials(name);

        List<String> finalMembers = new ArrayList<>();
        if (creatorId != null && !creatorId.isBlank()) {
            finalMembers.add(creatorId.trim());
        } else if (creatorName != null && !creatorName.isBlank()) {
            finalMembers.add(creatorName.trim());
        }
        if (members != null) {
            for (String m : members) {
                if (m != null && !m.isBlank() && finalMembers.stream().noneMatch(x -> x.equalsIgnoreCase(m.trim()))) {
                    finalMembers.add(m.trim());
                }
            }
        }

        ConversationDto dto = new ConversationDto(
                id, name, initials, "Nhóm trò chuyện trên máy chủ", false, true,
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

    void insertRawMessage(MessageDto m) {
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
        ConversationDto conv = getConversation(conversationId);
        boolean isGroup = conv != null && conv.isGroup();
        if (isGroup && !isUserMemberOfGroup(userId, conv)) {
            return List.of();
        }
        String storageConvId = conversationId;
        boolean isDm = !isGroup && !conversationId.startsWith("dm:");
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
                    m.attachmentName(), m.attachmentSize(), m.attachmentFailed(), m.reactions()
            ));
        }
        return mapped;
    }

    public synchronized List<MessageDto> loadMessages(String conversationId, String channel) {
        return loadRawMessages(conversationId, channel);
    }

    private List<MessageDto> loadRawMessages(String storageConversationId, String channel) {
        List<MessageDto> list = new ArrayList<>();
        List<String> ids = new ArrayList<>();
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
                    ids.add(rs.getString("id"));
                }
            }
            if (!ids.isEmpty()) {
                Map<String, Map<String, List<String>>> reactionsMap = loadReactionsForMessages(conn, ids);
                for (int i = 0; i < list.size(); i++) {
                    MessageDto m = list.get(i);
                    Map<String, List<String>> r = reactionsMap.get(m.id());
                    if (r != null && !r.isEmpty()) {
                        list.set(i, new MessageDto(
                                m.id(), m.conversationId(), m.channel(), m.senderId(), m.senderName(),
                                m.senderInitials(), m.content(), m.sentAt(), m.edited(), m.recalled(),
                                m.attachmentName(), m.attachmentSize(), m.attachmentFailed(), r
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi tải lịch sử tin nhắn: " + e.getMessage(), e);
        }
        return list;
    }

    private Map<String, Map<String, List<String>>> loadReactionsForMessages(Connection conn, List<String> messageIds) {
        Map<String, Map<String, List<String>>> result = new HashMap<>();
        if (messageIds == null || messageIds.isEmpty()) return result;
        for (int i = 0; i < messageIds.size(); i += 50) {
            List<String> chunk = messageIds.subList(i, Math.min(i + 50, messageIds.size()));
            String placeholders = String.join(",", Collections.nCopies(chunk.size(), "?"));
            String sql = "SELECT message_id, emoji, user_id FROM message_reactions WHERE message_id IN (" + placeholders + ") ";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int j = 0; j < chunk.size(); j++) {
                    ps.setString(j + 1, chunk.get(j));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String mid = rs.getString("message_id");
                        String emoji = rs.getString("emoji");
                        String uid = rs.getString("user_id");
                        result.computeIfAbsent(mid, k -> new LinkedHashMap<>())
                                .computeIfAbsent(emoji, k -> new ArrayList<>())
                                .add(uid);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public synchronized boolean toggleMessageReaction(String messageId, String userId, String emoji) {
        if (messageId == null || userId == null || emoji == null) return false;
        try (Connection conn = connect()) {
            try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM message_reactions WHERE message_id = ? AND user_id = ? AND emoji = ?")) {
                del.setString(1, messageId);
                del.setString(2, userId);
                del.setString(3, emoji);
                if (del.executeUpdate() > 0) {
                    return false;
                }
            }
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO message_reactions (message_id, user_id, emoji) VALUES (?, ?, ?)")) {
                ins.setString(1, messageId);
                ins.setString(2, userId);
                ins.setString(3, emoji);
                ins.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
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

    public synchronized MessageDto findMessageById(String messageId) {
        if (messageId == null || messageId.isBlank()) return null;
        try (Connection conn = connect()) {
            return findMessageById(conn, messageId);
        } catch (Exception e) {
            return null;
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
        fileStorage.storeFile(messageId, fileName, base64Data);
    }

    public synchronized String readPhysicalFileBase64(String messageId, String fileName) {
        return fileStorage.readFileBase64(messageId, fileName);
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

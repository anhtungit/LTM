package vn.edu.ltm.chat.server.network;

import vn.edu.ltm.chat.server.dal.DatabaseManager;
import vn.edu.ltm.chat.server.protocol.Packet;
import vn.edu.ltm.chat.server.protocol.ProtocolDtos.ConversationDto;
import vn.edu.ltm.chat.server.protocol.ProtocolDtos.MessageDto;
import vn.edu.ltm.chat.server.protocol.ProtocolDtos.UserDto;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Worker thread handling a single TCP client socket connection.
 */
public final class ClientHandler implements Runnable {
    private final Socket socket;
    private final ChatServer server;
    private final DatabaseManager db;
    private PrintWriter out;
    private UserDto currentUser;

    public ClientHandler(Socket socket, ChatServer server, DatabaseManager db) {
        this.socket = socket;
        this.server = server;
        this.db = db;
    }

    public UserDto getCurrentUser() {
        return currentUser;
    }

    public synchronized void sendPacket(Packet packet) {
        if (out != null) {
            out.println(packet.toJsonLine());
            out.flush();
        }
    }

    public void closeSocket() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    @Override
    public void run() {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            this.out = writer;
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    Packet req = Packet.fromJsonLine(line);
                    handleRequest(req);
                } catch (Exception e) {
                    System.err.println("[ClientHandler] Error processing packet: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (IOException ignored) {
            // Client disconnected
        } finally {
            server.unregisterHandler(this);
            if (currentUser != null && server.isRunning()) {
                db.updateUserOnlineStatus(currentUser.id(), false);
                server.broadcastExcept(this, Packet.broadcast("USER_STATUS_BROADCAST")
                        .put("userId", currentUser.id())
                        .put("online", false)
                        .put("status", "offline"));
            }
        }
    }

    private void handleRequest(Packet req) {
        String type = req.getType();
        String reqId = req.getRequestId();
        if (type == null) return;

        try {
            switch (type) {
                case "LOGIN_REQ" -> handleAuth(req, reqId, false);
                case "REGISTER_REQ" -> handleAuth(req, reqId, true);
                case "UPDATE_PROFILE_REQ" -> handleUpdateProfile(req, reqId);
                case "LIST_CONVERSATIONS_REQ" -> handleListConversations(req, reqId);
                case "LIST_USERS_REQ" -> handleListUsers(req, reqId);
                case "OPEN_DM_REQ" -> handleOpenDirectMessage(req, reqId);
                case "CLOSE_DM_REQ" -> handleCloseDirectMessage(req, reqId);
                case "GET_CHAT_HISTORY_REQ" -> handleGetHistory(req, reqId);
                case "SEND_MSG_REQ" -> handleSendMessage(req, reqId);
                case "EDIT_MSG_REQ" -> handleEditMessage(req, reqId);
                case "RECALL_MSG_REQ" -> handleRecallMessage(req, reqId);
                case "DELETE_MSG_REQ" -> handleDeleteMessage(req, reqId);
                case "REACT_MSG_REQ" -> handleReactMessage(req, reqId);
                case "FILE_UPLOAD_REQ" -> handleFileUpload(req, reqId);
                case "FILE_DOWNLOAD_REQ" -> handleFileDownload(req, reqId);
                case "CREATE_GROUP_REQ" -> handleCreateGroup(req, reqId);
                case "ADD_CHANNEL_REQ" -> handleModifyChannel(req, reqId, true, false);
                case "REMOVE_CHANNEL_REQ" -> handleModifyChannel(req, reqId, false, false);
                case "ADD_VOICE_CHANNEL_REQ" -> handleModifyChannel(req, reqId, true, true);
                case "REMOVE_VOICE_CHANNEL_REQ" -> handleModifyChannel(req, reqId, false, true);
                case "TYPING_REQ" -> handleTyping(req);
                default -> sendPacket(Packet.error(type + "_RES", reqId, "Loại gói tin không hỗ trợ: " + type));
            }
        } catch (IllegalArgumentException ex) {
            String resType = type.endsWith("_REQ") ? type.substring(0, type.length() - 4) + "_RES" : type + "_RES";
            sendPacket(Packet.error(resType, reqId, ex.getMessage()));
        } catch (Exception ex) {
            String resType = type.endsWith("_REQ") ? type.substring(0, type.length() - 4) + "_RES" : type + "_RES";
            sendPacket(Packet.error(resType, reqId, "Lỗi máy chủ: " + ex.getMessage()));
        }
    }

    private void handleAuth(Packet req, String reqId, boolean isRegister) {
        String email = req.getString("email", "");
        String password = req.getString("password", "");
        String displayName = req.getString("displayName", null);

        UserDto user = db.authenticateOrRegister(email, password, isRegister, displayName);
        this.currentUser = user;
        server.registerOnlineUser(user.id(), this);

        String resType = isRegister ? "REGISTER_RES" : "LOGIN_RES";
        sendPacket(Packet.response(resType, reqId).putObj("user", user));

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            db.updateUserOnlineStatus(user.id(), true);
            // Broadcast user online presence to all other clients
            server.broadcastExcept(this, Packet.broadcast("USER_STATUS_BROADCAST")
                    .put("userId", user.id())
                    .put("displayName", user.displayName())
                    .put("online", true)
                    .put("status", user.status()));
        });
    }

    private void handleUpdateProfile(Packet req, String reqId) {
        UserDto user = req.getObj("user", UserDto.class);
        if (user == null) {
            throw new IllegalArgumentException("Dữ liệu hồ sơ không hợp lệ.");
        }
        UserDto updated = db.updateProfile(user);
        this.currentUser = updated;
        sendPacket(Packet.response("UPDATE_PROFILE_RES", reqId).putObj("user", updated));

        server.broadcastExcept(this, Packet.broadcast("USER_STATUS_BROADCAST")
                .put("userId", updated.id())
                .put("displayName", updated.displayName())
                .put("online", !"offline".equals(updated.status()))
                .put("status", updated.status()));
    }

    private ConversationDto withLivePresence(ConversationDto c) {
        if (c == null) return null;
        boolean liveOnline = !c.isGroup() && server.isUserOnline(c.id());
        if (c.online() == liveOnline) return c;
        return new ConversationDto(c.id(), c.displayName(), c.initials(), c.about(),
                liveOnline, c.isGroup(), c.members(), c.channels(), c.voiceChannels());
    }

    private boolean denyGroupAccess(String userId, String convId, String resType, String reqId, String errorMsg) {
        if (db.isGroupConversation(convId) && (userId == null || !db.isUserMemberOfGroup(userId, convId))) {
            sendPacket(Packet.error(resType, reqId, errorMsg));
            return true;
        }
        return false;
    }

    private void broadcastToGroupMembers(String groupConvId, Packet packet) {
        for (ClientHandler h : server.getActiveHandlers()) {
            if (h == this) continue;
            UserDto u = h.getCurrentUser();
            if (u != null && db.isUserMemberOfGroup(u.id(), groupConvId)) {
                h.sendPacket(packet);
            }
        }
    }

    private void handleListConversations(Packet req, String reqId) {
        String userId = req.getString("userId", currentUser != null ? currentUser.id() : null);
        List<ConversationDto> raw = db.listConversationsForUser(userId);
        List<ConversationDto> conversations = new ArrayList<>(raw.size());
        for (ConversationDto c : raw) {
            conversations.add(withLivePresence(c));
        }
        sendPacket(Packet.response("LIST_CONVERSATIONS_RES", reqId)
                .putObj("conversations", conversations));
    }

    private void handleListUsers(Packet req, String reqId) {
        String excludeUserId = req.getString("excludeUserId", currentUser != null ? currentUser.id() : null);
        List<UserDto> raw = db.listUsers(excludeUserId);
        List<UserDto> users = new ArrayList<>(raw.size());
        for (UserDto u : raw) {
            boolean liveOnline = server.isUserOnline(u.id());
            users.add(new UserDto(
                    u.id(),
                    u.displayName(),
                    u.email(),
                    u.username(),
                    u.bio(),
                    liveOnline ? (u.status() == null || "offline".equalsIgnoreCase(u.status()) ? "online" : u.status()) : "offline",
                    u.avatarPath()
            ));
        }
        sendPacket(Packet.response("LIST_USERS_RES", reqId)
                .putObj("users", users));
    }

    private void handleOpenDirectMessage(Packet req, String reqId) {
        String userId = req.getString("userId", currentUser != null ? currentUser.id() : null);
        String peerId = req.getString("peerId", "");
        ConversationDto conv = db.openDirectMessage(userId, peerId);
        sendPacket(Packet.response("OPEN_DM_RES", reqId)
                .putObj("conversation", withLivePresence(conv)));
    }

    private void handleCloseDirectMessage(Packet req, String reqId) {
        String userId = req.getString("userId", currentUser != null ? currentUser.id() : null);
        String peerId = req.getString("peerId", "");
        if (userId != null && !userId.isBlank() && !peerId.isBlank()) {
            db.closeDirectMessage(userId, peerId);
        }
        sendPacket(Packet.response("CLOSE_DM_RES", reqId)
                .put("peerId", peerId));
    }

    private void handleGetHistory(Packet req, String reqId) {
        String userId = currentUser != null ? currentUser.id() : "";
        String convId = req.getString("conversationId", "");
        String channel = req.getString("channel", "");
        List<MessageDto> messages = db.loadMessagesForUser(userId, convId, channel);
        sendPacket(Packet.response("CHAT_HISTORY_RES", reqId)
                .put("conversationId", convId)
                .put("channel", channel)
                .putObj("messages", messages));
    }

    private void handleSendMessage(Packet req, String reqId) {
        String convId = req.getString("conversationId", "");
        String channel = req.getString("channel", "");
        String senderId = currentUser != null ? currentUser.id() : "user";
        String senderName = currentUser != null ? currentUser.displayName() : "Người dùng";
        String senderInitials = DatabaseManager.computeInitials(senderName);
        String content = req.getString("content", "");

        if (denyGroupAccess(senderId, convId, "SEND_MSG_RES", reqId, "Bạn không phải thành viên của nhóm này.")) {
            return;
        }

        MessageDto msg = new MessageDto(
                UUID.randomUUID().toString(),
                convId,
                channel,
                senderId,
                senderName,
                senderInitials,
                content,
                LocalDateTime.now().toString(),
                false,
                false,
                null,
                0L,
                false
        );
        db.saveMessage(msg);

        sendPacket(Packet.response("SEND_MSG_RES", reqId).putObj("message", msg));
        broadcastNewMessage(convId, senderId, msg);
    }

    private void broadcastNewMessage(String convId, String senderId, MessageDto msg) {
        if (db.isGroupConversation(convId)) {
            broadcastToGroupMembers(convId, Packet.broadcast("NEW_MSG_BROADCAST").putObj("message", msg));
            return;
        }
        // 1-1 Direct Message between senderId and peerId (convId)
        String peerId = convId;
        ConversationDto senderConv = withLivePresence(db.getConversation(senderId));
        for (ClientHandler h : server.getActiveHandlers()) {
            if (h == this) continue;
            UserDto targetUser = h.getCurrentUser();
            if (targetUser == null) continue;
            if (targetUser.id().equals(peerId)) {
                if (senderConv != null) {
                    h.sendPacket(Packet.broadcast("CONVERSATION_UPDATED_BROADCAST")
                            .putObj("conversation", senderConv));
                }
                MessageDto forPeer = withConversationId(msg, senderId);
                h.sendPacket(Packet.broadcast("NEW_MSG_BROADCAST").putObj("message", forPeer));
            } else if (targetUser.id().equals(senderId)) {
                h.sendPacket(Packet.broadcast("NEW_MSG_BROADCAST").putObj("message", msg));
            }
        }
    }

    private void handleEditMessage(Packet req, String reqId) {
        String msgId = req.getString("messageId", "");
        String newContent = req.getString("newContent", "");
        MessageDto existing = db.findMessageById(msgId);
        if (existing == null) {
            sendPacket(Packet.error("EDIT_MSG_RES", reqId, "Tin nhắn không tồn tại."));
            return;
        }
        if (currentUser == null || !currentUser.id().equals(existing.senderId())) {
            sendPacket(Packet.error("EDIT_MSG_RES", reqId, "Bạn không có quyền chỉnh sửa tin nhắn này."));
            return;
        }

        MessageDto updated = db.editMessage(msgId, newContent);
        String callerId = currentUser.id();
        MessageDto callerView = withConversationId(updated, resolveClientConversationId(updated.conversationId(), callerId));

        sendPacket(Packet.response("EDIT_MSG_RES", reqId).putObj("message", callerView));
        broadcastMessageUpdate("EDIT", updated);
    }

    private void handleRecallMessage(Packet req, String reqId) {
        String msgId = req.getString("messageId", "");
        MessageDto existing = db.findMessageById(msgId);
        if (existing == null) {
            sendPacket(Packet.error("RECALL_MSG_RES", reqId, "Tin nhắn không tồn tại."));
            return;
        }
        if (currentUser == null || !currentUser.id().equals(existing.senderId())) {
            sendPacket(Packet.error("RECALL_MSG_RES", reqId, "Bạn không có quyền thu hồi tin nhắn này."));
            return;
        }

        MessageDto recalled = db.recallMessage(msgId);
        String callerId = currentUser.id();
        MessageDto callerView = withConversationId(recalled, resolveClientConversationId(recalled.conversationId(), callerId));

        sendPacket(Packet.response("RECALL_MSG_RES", reqId).putObj("message", callerView));
        broadcastMessageUpdate("RECALL", recalled);
    }

    private void broadcastMessageUpdate(String action, MessageDto rawUpdated) {
        String rawConvId = rawUpdated.conversationId();
        if (rawConvId != null && rawConvId.startsWith("dm:")) {
            String[] pair = rawConvId.substring(3).split("::", 2);
            if (pair.length == 2) {
                for (ClientHandler h : server.getActiveHandlers()) {
                    if (h == this) continue;
                    UserDto u = h.getCurrentUser();
                    if (u == null) continue;
                    if (u.id().equals(pair[0]) || u.id().equals(pair[1])) {
                        String peerId = u.id().equals(pair[0]) ? pair[1] : pair[0];
                        MessageDto view = withConversationId(rawUpdated, peerId);
                        h.sendPacket(Packet.broadcast("MSG_UPDATED_BROADCAST")
                                .put("action", action)
                                .putObj("message", view));
                    }
                }
                return;
            }
        }
        if (db.isGroupConversation(rawConvId)) {
            broadcastToGroupMembers(rawConvId, Packet.broadcast("MSG_UPDATED_BROADCAST")
                    .put("action", action)
                    .putObj("message", rawUpdated));
            return;
        }
        server.broadcastExcept(this, Packet.broadcast("MSG_UPDATED_BROADCAST")
                .put("action", action)
                .putObj("message", rawUpdated));
    }

    private static String resolveClientConversationId(String rawConvId, String viewerUserId) {
        if (rawConvId != null && rawConvId.startsWith("dm:")) {
            String[] parts = rawConvId.substring(3).split("::", 2);
            if (parts.length == 2) {
                if (viewerUserId != null && viewerUserId.equals(parts[0])) {
                    return parts[1];
                }
                return parts[0];
            }
        }
        return rawConvId;
    }

    private static MessageDto withConversationId(MessageDto m, String newConvId) {
        if (m == null || (m.conversationId() != null && m.conversationId().equals(newConvId))) {
            return m;
        }
        return new MessageDto(
                m.id(), newConvId, m.channel(), m.senderId(), m.senderName(),
                m.senderInitials(), m.content(), m.sentAt(), m.edited(), m.recalled(),
                m.attachmentName(), m.attachmentSize(), m.attachmentFailed(), m.reactions()
        );
    }

    private void handleReactMessage(Packet req, String reqId) {
        String convId = req.getString("conversationId", "");
        String msgId = req.getString("messageId", "");
        String emoji = req.getString("emoji", "");
        if (msgId.isBlank() || emoji.isBlank()) {
            sendPacket(Packet.error("REACT_MSG_RES", reqId, "messageId and emoji are required"));
            return;
        }
        String uid = currentUser != null ? currentUser.id() : "";
        if (uid.isBlank()) {
            sendPacket(Packet.error("REACT_MSG_RES", reqId, "Bạn chưa đăng nhập"));
            return;
        }
        if (denyGroupAccess(uid, convId, "REACT_MSG_RES", reqId, "Bạn không phải thành viên của nhóm này.")) {
            return;
        }
        db.toggleMessageReaction(msgId, uid, emoji);
        sendPacket(Packet.response("REACT_MSG_RES", reqId)
                .put("conversationId", convId)
                .put("messageId", msgId)
                .put("emoji", emoji));

        if (db.isGroupConversation(convId)) {
            broadcastToGroupMembers(convId, Packet.broadcast("MSG_UPDATED_BROADCAST")
                    .put("action", "REACT")
                    .put("conversationId", convId)
                    .put("messageId", msgId)
                    .put("emoji", emoji)
                    .put("userId", uid));
        } else {
            for (ClientHandler h : server.getActiveHandlers()) {
                if (h == this) continue;
                UserDto u = h.getCurrentUser();
                if (u == null) continue;
                if (u.id().equals(convId) || u.id().equals(uid)) {
                    String viewConvId = u.id().equals(convId) ? uid : convId;
                    h.sendPacket(Packet.broadcast("MSG_UPDATED_BROADCAST")
                            .put("action", "REACT")
                            .put("conversationId", viewConvId)
                            .put("messageId", msgId)
                            .put("emoji", emoji)
                            .put("userId", uid));
                }
            }
        }
    }

    private void handleDeleteMessage(Packet req, String reqId) {
        String convId = req.getString("conversationId", "");
        String msgId = req.getString("messageId", "");
        MessageDto existing = db.findMessageById(msgId);
        if (existing == null) {
            sendPacket(Packet.error("DELETE_MSG_RES", reqId, "Tin nhắn không tồn tại."));
            return;
        }
        if (currentUser == null || !currentUser.id().equals(existing.senderId())) {
            sendPacket(Packet.error("DELETE_MSG_RES", reqId, "Bạn không có quyền xoá tin nhắn này."));
            return;
        }

        db.deleteMessage(msgId);

        sendPacket(Packet.response("DELETE_MSG_RES", reqId)
                .put("conversationId", convId)
                .put("messageId", msgId));
        if (db.isGroupConversation(convId)) {
            broadcastToGroupMembers(convId, Packet.broadcast("MSG_UPDATED_BROADCAST")
                    .put("action", "DELETE")
                    .put("conversationId", convId)
                    .put("messageId", msgId));
        } else {
            String senderId = currentUser != null ? currentUser.id() : "";
            for (ClientHandler h : server.getActiveHandlers()) {
                if (h == this) continue;
                UserDto u = h.getCurrentUser();
                if (u == null) continue;
                if (u.id().equals(convId) || u.id().equals(senderId)) {
                    String viewConvId = u.id().equals(convId) ? senderId : convId;
                    h.sendPacket(Packet.broadcast("MSG_UPDATED_BROADCAST")
                            .put("action", "DELETE")
                            .put("conversationId", viewConvId)
                            .put("messageId", msgId));
                }
            }
        }
    }

    private void handleFileUpload(Packet req, String reqId) {
        String convId = req.getString("conversationId", "");
        String channel = req.getString("channel", "");
        String senderId = currentUser != null ? currentUser.id() : "user";
        String senderName = currentUser != null ? currentUser.displayName() : "Người dùng";
        String senderInitials = DatabaseManager.computeInitials(senderName);
        String fileName = req.getString("fileName", "attachment.dat");
        long fileSize = req.getLong("fileSize", 0L);
        boolean failed = req.getBoolean("failed", false);
        String base64Data = req.getString("base64Data", null);

        if (denyGroupAccess(senderId, convId, "FILE_UPLOAD_RES", reqId, "Bạn không phải thành viên của nhóm này.")) {
            return;
        }

        String msgId = UUID.randomUUID().toString();
        if (!failed && base64Data != null && !base64Data.isBlank()) {
            db.storePhysicalFile(msgId, fileName, base64Data);
        }

        MessageDto msg = new MessageDto(
                msgId,
                convId,
                channel,
                senderId,
                senderName,
                senderInitials,
                "Đã gửi tệp: " + fileName,
                LocalDateTime.now().toString(),
                false,
                false,
                fileName,
                fileSize,
                failed
        );
        db.saveMessage(msg);

        sendPacket(Packet.response("FILE_UPLOAD_RES", reqId).putObj("message", msg));
        broadcastNewMessage(convId, senderId, msg);
    }

    private void handleFileDownload(Packet req, String reqId) {
        String msgId = req.getString("messageId", "");
        String fileName = req.getString("fileName", "");
        String base64Data = db.readPhysicalFileBase64(msgId, fileName);

        sendPacket(Packet.response("FILE_DOWNLOAD_RES", reqId)
                .put("messageId", msgId)
                .put("fileName", fileName)
                .put("base64Data", base64Data != null ? base64Data : ""));
    }

    private void handleCreateGroup(Packet req, String reqId) {
        String creatorId = req.getString("creatorId", currentUser != null ? currentUser.id() : null);
        String creatorName = req.getString("creatorName", currentUser != null ? currentUser.displayName() : null);
        String groupName = req.getString("groupName", "Nhóm mới");
        String[] membersArr = req.getObj("members", String[].class);
        List<String> members = membersArr != null ? Arrays.asList(membersArr) : List.of();

        ConversationDto created = db.createGroup(creatorId, creatorName, groupName, members);
        sendPacket(Packet.response("CREATE_GROUP_RES", reqId).putObj("conversation", created));
        broadcastToGroupMembers(created.id(), Packet.broadcast("CONVERSATION_UPDATED_BROADCAST")
                .putObj("conversation", created));
    }

    private void handleModifyChannel(Packet req, String reqId, boolean isAdd, boolean isVoice) {
        String convId = req.getString("conversationId", "");
        String chanName = req.getString("channelName", "");
        String userId = currentUser != null ? currentUser.id() : null;
        String resType = req.getType().replace("_REQ", "_RES");
        if (denyGroupAccess(userId, convId, resType, reqId, "Bạn không có quyền quản lý kênh trong nhóm này.")) {
            return;
        }
        ConversationDto updated = isAdd
                ? db.addChannel(convId, chanName, isVoice)
                : db.removeChannel(convId, chanName, isVoice);

        sendPacket(Packet.response(resType, reqId).putObj("conversation", updated));
        if (updated != null) {
            broadcastToGroupMembers(convId, Packet.broadcast("CONVERSATION_UPDATED_BROADCAST")
                    .putObj("conversation", updated));
        }
    }

    private void handleTyping(Packet req) {
        String convId = req.getString("conversationId", "");
        String channel = req.getString("channel", "");
        String senderName = req.getString("senderName", currentUser != null ? currentUser.displayName() : "Ai đó");
        boolean typing = req.getBoolean("typing", true);

        server.broadcastExcept(this, Packet.broadcast("TYPING_BROADCAST")
                .put("conversationId", convId)
                .put("channel", channel)
                .put("senderName", senderName)
                .put("typing", typing));
    }
}


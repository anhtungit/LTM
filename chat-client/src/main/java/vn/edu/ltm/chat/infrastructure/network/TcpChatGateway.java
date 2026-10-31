package vn.edu.ltm.chat.infrastructure.network;

import vn.edu.ltm.chat.application.ChatGateway;
import vn.edu.ltm.chat.domain.ChatMessage;
import vn.edu.ltm.chat.domain.Conversation;
import vn.edu.ltm.chat.domain.UserProfile;
import vn.edu.ltm.chat.infrastructure.demo.InMemoryChatGateway;
import vn.edu.ltm.chat.infrastructure.network.TcpPacket.ConversationDto;
import vn.edu.ltm.chat.infrastructure.network.TcpPacket.MessageDto;
import vn.edu.ltm.chat.infrastructure.network.TcpPacket.UserDto;

import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * TCP Socket Adapter implementing {@link ChatGateway}.
 * Connects to the Centralized Server (default localhost:8888) using JSON newline framing.
 * If the server is not reachable, gracefully falls back to {@link InMemoryChatGateway}.
 */
public final class TcpChatGateway implements ChatGateway {
    private final String explicitHost;
    private final int explicitPort;
    private final InMemoryChatGateway fallback = new InMemoryChatGateway();
    private final Map<String, Boolean> userPresenceMap = new ConcurrentHashMap<>();

    private Socket socket;
    private DatagramSocket udpVoiceSocket;
    private volatile boolean udpVoiceVerified = false;
    private PrintWriter out;
    private volatile boolean connected = false;
    private volatile boolean closed = false;
    private String currentUserId;
    private String currentUserName;
    private volatile Runnable onRealtimeUpdate;
    private volatile Runnable onPresenceUpdate;
    private volatile java.util.function.Consumer<TypingEvent> onTypingUpdate;
    private volatile java.util.function.Consumer<CallSignalEvent> onCallSignal;

    private final Map<String, CompletableFuture<TcpPacket>> pendingRequests = new ConcurrentHashMap<>();

    public TcpChatGateway() {
        this(null, 0);
    }

    public TcpChatGateway(String explicitHost, int explicitPort) {
        this.explicitHost = explicitHost;
        this.explicitPort = explicitPort;
        startBackgroundAutoConnect();
    }

    private void startBackgroundAutoConnect() {
        Thread connector = new Thread(() -> {
            while (!closed) {
                if (!connected) {
                    boolean nowConnected = tryConnect();
                    if (nowConnected) {
                        Runnable cb = this.onRealtimeUpdate;
                        if (cb != null) {
                            SwingUtilities.invokeLater(cb);
                        }
                    }
                }
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "tcp-zero-config-autoconnect");
        connector.setDaemon(true);
        connector.start();
    }

    public synchronized boolean tryConnect() {
        if (connected && socket != null && !socket.isClosed()) {
            return true;
        }
        if (SwingUtilities.isEventDispatchThread()) {
            return false;
        }
        try {
            Socket s = ServerEndpointResolver.connectAuto(explicitHost, explicitPort);
            s.setTcpNoDelay(true);
            this.socket = s;
            this.out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
            this.connected = true;

            initUdpVoiceSocket();

            Thread readerThread = new Thread(this::listenLoop, "tcp-client-listener");
            readerThread.setDaemon(true);
            readerThread.start();
            return true;
        } catch (IOException e) {
            this.connected = false;
            return false;
        }
    }

    private void initUdpVoiceSocket() {
        try {
            if (udpVoiceSocket != null && !udpVoiceSocket.isClosed()) {
                udpVoiceSocket.close();
            }
            this.udpVoiceVerified = false;
            DatagramSocket ds = new DatagramSocket();
            this.udpVoiceSocket = ds;
            Thread udpListener = new Thread(() -> udpVoiceListenLoop(ds), "udp-voice-listener");
            udpListener.setDaemon(true);
            udpListener.setPriority(Thread.MAX_PRIORITY);
            udpListener.start();
            sendUdpVoiceReg();
        } catch (Exception ignored) {
        }
    }

    private void udpVoiceListenLoop(DatagramSocket ds) {
        byte[] buf = new byte[2048];
        while (!closed && !ds.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                ds.receive(packet);
                byte[] data = packet.getData();
                int offset = packet.getOffset();
                int len = packet.getLength();
                int nl = -1;
                for (int i = offset; i < offset + len; i++) {
                    if (data[i] == '\n') {
                        nl = i;
                        break;
                    }
                }
                if (nl <= offset) continue;
                String header = new String(data, offset, nl - offset, StandardCharsets.UTF_8);
                if (header.startsWith("LTM_VOICE_ACK")) {
                    this.udpVoiceVerified = true;
                    continue;
                }
                if (!header.startsWith("LTM_VOICE|")) continue;
                this.udpVoiceVerified = true;
                String[] parts = header.split("\\|", 5);
                if (parts.length < 5) continue;

                String senderId = parts[1];
                String senderName = parts[2];
                String convId = parts[3];
                String chan = parts[4];
                int pcmOffset = nl + 1;
                int pcmLen = (offset + len) - pcmOffset;
                if (pcmLen <= 0) continue;

                byte[] pcm = new byte[pcmLen];
                System.arraycopy(data, pcmOffset, pcm, 0, pcmLen);
                var cb = this.onCallSignal;
                if (cb != null) {
                    String b64 = Base64.getEncoder().encodeToString(pcm);
                    cb.accept(new CallSignalEvent(convId, chan, senderId, senderName, "AUDIO", b64));
                }
            } catch (IOException e) {
                if (ds.isClosed()) break;
            } catch (Exception ignored) {
            }
        }
    }

    private void sendUdpVoiceReg() {
        DatagramSocket ds = this.udpVoiceSocket;
        Socket s = this.socket;
        String uid = this.currentUserId;
        if (ds == null || ds.isClosed() || s == null || s.isClosed() || uid == null || uid.isBlank()) {
            return;
        }
        try {
            byte[] payload = ("LTM_VOICE_REG|" + uid + "\n").getBytes(StandardCharsets.UTF_8);
            ds.send(new DatagramPacket(payload, payload.length, s.getInetAddress(), s.getPort()));
        } catch (Exception ignored) {
        }
    }

    private void listenLoop() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    TcpPacket packet = TcpPacket.fromJsonLine(line);
                    String reqId = packet.getRequestId();
                    if (reqId != null && pendingRequests.containsKey(reqId)) {
                        CompletableFuture<TcpPacket> future = pendingRequests.remove(reqId);
                        if (future != null) {
                            future.complete(packet);
                        }
                    } else {
                        handleBroadcast(packet);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (IOException ignored) {
        } finally {
            connected = false;
            for (CompletableFuture<TcpPacket> f : pendingRequests.values()) {
                f.completeExceptionally(new IOException("Mất kết nối tới máy chủ chat."));
            }
            pendingRequests.clear();
        }
    }

    private void handleBroadcast(TcpPacket packet) {
        String type = packet.getType();
        if ("USER_STATUS_BROADCAST".equals(type)) {
            String uid = packet.getString("userId", "");
            boolean online = packet.getBoolean("online", false);
            if (!uid.isBlank()) {
                userPresenceMap.put(uid, online);
            }
            Runnable presenceCb = this.onPresenceUpdate != null ? this.onPresenceUpdate : this.onRealtimeUpdate;
            if (presenceCb != null) {
                SwingUtilities.invokeLater(presenceCb);
            }
            return;
        }
        if ("TYPING_BROADCAST".equals(type)) {
            String convId = packet.getString("conversationId", "");
            String chan = packet.getString("channel", "");
            String sId = packet.getString("senderId", "");
            String sName = packet.getString("senderName", "Ai đó");
            boolean typing = packet.getBoolean("typing", true);
            var cb = this.onTypingUpdate;
            if (cb != null) {
                SwingUtilities.invokeLater(() -> cb.accept(new TypingEvent(convId, chan, sId, sName, typing)));
            }
            return;
        }
        if ("CALL_SIGNAL_BROADCAST".equals(type)) {
            String convId = packet.getString("conversationId", "");
            String chan = packet.getString("channel", "");
            String sId = packet.getString("senderId", "");
            String sName = packet.getString("senderName", "Ai đó");
            String action = packet.getString("action", "");
            String data = packet.getString("data", "");
            var cb = this.onCallSignal;
            if (cb != null) {
                CallSignalEvent ev = new CallSignalEvent(convId, chan, sId, sName, action, data);
                if ("AUDIO".equals(action)) {
                    cb.accept(ev);
                } else {
                    SwingUtilities.invokeLater(() -> cb.accept(ev));
                }
            }
            return;
        }
        Runnable cb = this.onRealtimeUpdate;
        if (cb != null) {
            SwingUtilities.invokeLater(cb);
        }
    }

    @Override
    public boolean isUserOnline(String userId) {
        if (userId == null || userId.isBlank()) return false;
        if (currentUserId != null && userId.equalsIgnoreCase(currentUserId)) {
            return true;
        }
        return userPresenceMap.getOrDefault(userId, false);
    }

    private TcpPacket request(TcpPacket req) {
        if (!tryConnect()) {
            throw new IllegalStateException("Chưa kết nối tới máy chủ TCP.");
        }
        CompletableFuture<TcpPacket> future = new CompletableFuture<>();
        pendingRequests.put(req.getRequestId(), future);
        synchronized (this) {
            out.println(req.toJsonLine());
            out.flush();
        }
        try {
            TcpPacket res = future.get(15, TimeUnit.SECONDS);
            if (!res.isOk()) {
                throw new IllegalArgumentException(res.getError() != null ? res.getError() : "Yêu cầu thất bại.");
            }
            return res;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (java.util.concurrent.TimeoutException e) {
            pendingRequests.remove(req.getRequestId());
            throw new IllegalArgumentException("Máy chủ phản hồi quá thời gian chờ (Timeout). Vui lòng thử lại.", e);
        } catch (java.util.concurrent.ExecutionException e) {
            pendingRequests.remove(req.getRequestId());
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String msg = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
            throw new IllegalArgumentException("Lỗi giao tiếp máy chủ: " + msg, cause);
        } catch (Exception e) {
            pendingRequests.remove(req.getRequestId());
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new IllegalArgumentException("Lỗi giao tiếp máy chủ: " + msg, e);
        }
    }

    @Override
    public boolean isServerConnected() {
        return connected;
    }

    @Override
    public void setOnRealtimeUpdate(Runnable callback) {
        this.onRealtimeUpdate = callback;
    }

    @Override
    public void setOnPresenceUpdate(Runnable callback) {
        this.onPresenceUpdate = callback;
    }

    @Override
    public UserProfile login(String email, String password) {
        if (!tryConnect()) {
            throw new UnsupportedOperationException("Server offline, use local fallback");
        }
        TcpPacket req = new TcpPacket("LOGIN_REQ", UUID.randomUUID().toString())
                .put("email", email)
                .put("password", password);
        TcpPacket res = request(req);
        UserDto u = res.getObj("user", UserDto.class);
        this.currentUserId = u.id();
        this.currentUserName = u.displayName();
        userPresenceMap.put(u.id(), true);
        sendUdpVoiceReg();
        return toDomainUser(u);
    }

    @Override
    public UserProfile register(String email, String password, String displayName) {
        if (!tryConnect()) {
            throw new UnsupportedOperationException("Server offline, use local fallback");
        }
        TcpPacket req = new TcpPacket("REGISTER_REQ", UUID.randomUUID().toString())
                .put("email", email)
                .put("password", password)
                .put("displayName", displayName);
        TcpPacket res = request(req);
        UserDto u = res.getObj("user", UserDto.class);
        this.currentUserId = u.id();
        this.currentUserName = u.displayName();
        userPresenceMap.put(u.id(), true);
        sendUdpVoiceReg();
        return toDomainUser(u);
    }

    @Override
    public UserProfile updateProfile(UserProfile user) {
        if (!tryConnect()) {
            return fallback.updateProfile(user);
        }
        UserDto dto = new UserDto(user.id(), user.displayName(), user.email(),
                user.username(), user.bio(), user.status(), user.avatarPath());
        TcpPacket req = new TcpPacket("UPDATE_PROFILE_REQ", UUID.randomUUID().toString())
                .putObj("user", dto);
        TcpPacket res = request(req);
        UserDto updated = res.getObj("user", UserDto.class);
        if (updated != null) {
            this.currentUserId = updated.id();
            this.currentUserName = updated.displayName();
            return toDomainUser(updated);
        }
        return user;
    }

    @Override
    public List<UserProfile> listUsers() {
        if (!tryConnect()) {
            return fallback.listUsers();
        }
        try {
            TcpPacket req = new TcpPacket("LIST_USERS_REQ", UUID.randomUUID().toString())
                    .put("excludeUserId", currentUserId != null ? currentUserId : "");
            TcpPacket res = request(req);
            UserDto[] arr = res.getObj("users", UserDto[].class);
            List<UserProfile> list = new ArrayList<>();
            if (arr != null) {
                for (UserDto u : arr) {
                    list.add(toDomainUser(u));
                    userPresenceMap.put(u.id(), !"offline".equalsIgnoreCase(u.status()));
                }
            }
            return list;
        } catch (Exception e) {
            return fallback.listUsers();
        }
    }

    @Override
    public Conversation openDirectMessage(String peerId) {
        if (!tryConnect()) {
            return fallback.openDirectMessage(peerId);
        }
        TcpPacket req = new TcpPacket("OPEN_DM_REQ", UUID.randomUUID().toString())
                .put("userId", currentUserId != null ? currentUserId : "")
                .put("peerId", peerId);
        TcpPacket res = request(req);
        ConversationDto c = res.getObj("conversation", ConversationDto.class);
        return c != null ? toDomainConversation(c) : null;
    }

    @Override
    public void setOnTypingUpdate(java.util.function.Consumer<TypingEvent> callback) {
        this.onTypingUpdate = callback;
    }

    @Override
    public void sendTyping(String conversationId, String channel, boolean typing) {
        if (!tryConnect() || conversationId == null || conversationId.isBlank()) {
            return;
        }
        try {
            TcpPacket req = new TcpPacket("TYPING_REQ", UUID.randomUUID().toString())
                    .put("conversationId", conversationId)
                    .put("channel", channel != null ? channel : "")
                    .put("senderName", currentUserName != null ? currentUserName : "Người dùng")
                    .put("typing", typing);
            synchronized (this) {
                if (out != null) {
                    out.println(req.toJsonLine());
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void setOnCallSignal(java.util.function.Consumer<CallSignalEvent> callback) {
        this.onCallSignal = callback;
    }

    @Override
    public void sendCallSignal(String conversationId, String channel, String action, String data) {
        if (!connected || out == null || conversationId == null || conversationId.isBlank()) {
            return;
        }
        if ("AUDIO".equals(action)) {
            DatagramSocket ds = this.udpVoiceSocket;
            Socket s = this.socket;
            String uid = this.currentUserId;
            if (ds != null && !ds.isClosed() && s != null && !s.isClosed() && uid != null && data != null && !data.isEmpty()) {
                try {
                    byte[] pcm = Base64.getDecoder().decode(data);
                    String safeName = (currentUserName != null ? currentUserName : "Người dùng").replace('|', ' ');
                    String safeChan = (channel != null ? channel : "").replace('|', ' ');
                    byte[] hdr = ("LTM_VOICE|" + uid + "|" + safeName + "|" + conversationId + "|" + safeChan + "\n")
                            .getBytes(StandardCharsets.UTF_8);
                    byte[] packetBytes = new byte[hdr.length + pcm.length];
                    System.arraycopy(hdr, 0, packetBytes, 0, hdr.length);
                    System.arraycopy(pcm, 0, packetBytes, hdr.length, pcm.length);
                    ds.send(new DatagramPacket(packetBytes, packetBytes.length, s.getInetAddress(), s.getPort()));
                    if (udpVoiceVerified) {
                        return;
                    }
                    sendUdpVoiceReg();
                } catch (Exception ignored) {
                }
            }
        } else {
            sendUdpVoiceReg();
        }
        try {
            TcpPacket req = new TcpPacket("CALL_SIGNAL_REQ", UUID.randomUUID().toString())
                    .put("conversationId", conversationId)
                    .put("channel", channel != null ? channel : "")
                    .put("senderName", currentUserName != null ? currentUserName : "Người dùng")
                    .put("action", action != null ? action : "")
                    .put("data", data != null ? data : "");
            synchronized (this) {
                if (out != null) {
                    out.println(req.toJsonLine());
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void closeDirectMessage(String peerId) {
        if (peerId == null || peerId.isBlank()) return;
        fallback.closeDirectMessage(peerId);
        if (!tryConnect()) {
            return;
        }
        try {
            TcpPacket req = new TcpPacket("CLOSE_DM_REQ", UUID.randomUUID().toString())
                    .put("userId", currentUserId != null ? currentUserId : "")
                    .put("peerId", peerId);
            request(req);
        } catch (Exception ignored) {
        }
    }

    @Override
    public List<Conversation> listConversations() {
        if (!tryConnect()) {
            return fallback.listConversations();
        }
        try {
            TcpPacket req = new TcpPacket("LIST_CONVERSATIONS_REQ", UUID.randomUUID().toString())
                    .put("userId", currentUserId != null ? currentUserId : "");
            TcpPacket res = request(req);
            ConversationDto[] arr = res.getObj("conversations", ConversationDto[].class);
            List<Conversation> result = new ArrayList<>();
            if (arr != null) {
                for (ConversationDto c : arr) {
                    // Hide own self-DM row if another user logged in
                    if (!c.isGroup() && c.id().equals(currentUserId)) {
                        continue;
                    }
                    if (!c.isGroup()) {
                        userPresenceMap.put(c.id(), c.online());
                    }
                    result.add(toDomainConversation(c));
                }
            }
            return result;
        } catch (Exception e) {
            if (currentUserId != null && !currentUserId.isBlank()) {
                return Collections.emptyList();
            }
            return fallback.listConversations();
        }
    }

    @Override
    public List<ChatMessage> loadMessages(String conversationId, String channel) {
        if (!tryConnect()) {
            return fallback.loadMessages(conversationId, channel);
        }
        try {
            TcpPacket req = new TcpPacket("GET_CHAT_HISTORY_REQ", UUID.randomUUID().toString())
                    .put("userId", currentUserId != null ? currentUserId : "")
                    .put("conversationId", conversationId)
                    .put("channel", channel != null ? channel : "");
            TcpPacket res = request(req);
            MessageDto[] arr = res.getObj("messages", MessageDto[].class);
            List<ChatMessage> list = new ArrayList<>();
            if (arr != null) {
                for (MessageDto m : arr) {
                    list.add(toDomainMessage(m));
                }
            }
            return list;
        } catch (Exception e) {
            return fallback.loadMessages(conversationId, channel);
        }
    }

    @Override
    public ChatMessage sendMessage(String conversationId, String channel, UserProfile sender, String content) {
        if (!tryConnect()) {
            return fallback.sendMessage(conversationId, channel, sender, content);
        }
        this.currentUserId = sender.id();
        TcpPacket req = new TcpPacket("SEND_MSG_REQ", UUID.randomUUID().toString())
                .put("conversationId", conversationId)
                .put("channel", channel != null ? channel : "")
                .put("senderId", sender.id())
                .put("senderName", sender.displayName())
                .put("senderInitials", sender.initials())
                .put("content", content);
        TcpPacket res = request(req);
        MessageDto m = res.getObj("message", MessageDto.class);
        return toDomainMessage(m);
    }

    @Override
    public ChatMessage editMessage(String conversationId, String messageId, String newContent) {
        if (!tryConnect()) {
            return fallback.editMessage(conversationId, messageId, newContent);
        }
        TcpPacket req = new TcpPacket("EDIT_MSG_REQ", UUID.randomUUID().toString())
                .put("conversationId", conversationId)
                .put("messageId", messageId)
                .put("newContent", newContent);
        TcpPacket res = request(req);
        MessageDto m = res.getObj("message", MessageDto.class);
        return toDomainMessage(m);
    }

    @Override
    public void deleteMessage(String conversationId, String messageId) {
        if (!tryConnect()) {
            fallback.deleteMessage(conversationId, messageId);
            return;
        }
        TcpPacket req = new TcpPacket("DELETE_MSG_REQ", UUID.randomUUID().toString())
                .put("conversationId", conversationId)
                .put("messageId", messageId);
        request(req);
    }

    @Override
    public ChatMessage recallMessage(String conversationId, String messageId) {
        if (!tryConnect()) {
            return fallback.recallMessage(conversationId, messageId);
        }
        TcpPacket req = new TcpPacket("RECALL_MSG_REQ", UUID.randomUUID().toString())
                .put("conversationId", conversationId)
                .put("messageId", messageId);
        TcpPacket res = request(req);
        MessageDto m = res.getObj("message", MessageDto.class);
        return toDomainMessage(m);
    }

    @Override
    public ChatMessage sendAttachment(String conversationId, String channel, UserProfile sender,
                                      String fileName, long fileSize, boolean failed) {
        if (!tryConnect()) {
            return fallback.sendAttachment(conversationId, channel, sender, fileName, fileSize, failed);
        }
        this.currentUserId = sender.id();
        String base64 = null;
        File candidate = new File(fileName);
        if (!failed && candidate.exists() && candidate.isFile() && candidate.length() <= 10 * 1024 * 1024) {
            try {
                base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(candidate.toPath()));
                fileName = candidate.getName();
            } catch (IOException ignored) {
            }
        }

        TcpPacket req = new TcpPacket("FILE_UPLOAD_REQ", UUID.randomUUID().toString())
                .put("conversationId", conversationId)
                .put("channel", channel != null ? channel : "")
                .put("senderId", sender.id())
                .put("senderName", sender.displayName())
                .put("senderInitials", sender.initials())
                .put("fileName", new File(fileName).getName())
                .put("fileSize", fileSize)
                .put("failed", failed);
        if (base64 != null) {
            req.put("base64Data", base64);
        }
        TcpPacket res = request(req);
        MessageDto m = res.getObj("message", MessageDto.class);
        return toDomainMessage(m);
    }

    @Override
    public byte[] downloadAttachment(String messageId, String fileName) {
        if (!tryConnect()) {
            return null;
        }
        try {
            TcpPacket req = new TcpPacket("FILE_DOWNLOAD_REQ", UUID.randomUUID().toString())
                    .put("messageId", messageId)
                    .put("fileName", fileName);
            TcpPacket res = request(req);
            String b64 = res.getString("base64Data", "");
            if (b64 != null && !b64.isBlank()) {
                return Base64.getDecoder().decode(b64);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public Conversation createGroup(String groupName, List<String> memberNames) {
        if (!tryConnect()) {
            return fallback.createGroup(groupName, memberNames);
        }
        TcpPacket req = new TcpPacket("CREATE_GROUP_REQ", UUID.randomUUID().toString())
                .put("creatorId", currentUserId != null ? currentUserId : "")
                .put("creatorName", currentUserName != null ? currentUserName : "")
                .put("groupName", groupName)
                .putObj("members", memberNames != null ? memberNames : List.of());
        TcpPacket res = request(req);
        ConversationDto c = res.getObj("conversation", ConversationDto.class);
        return toDomainConversation(c);
    }

    @Override
    public void addChannel(String conversationId, String channelName) {
        if (!tryConnect()) {
            fallback.addChannel(conversationId, channelName);
            return;
        }
        TcpPacket req = new TcpPacket("ADD_CHANNEL_REQ", UUID.randomUUID().toString())
                .put("conversationId", conversationId)
                .put("channelName", channelName);
        request(req);
    }

    @Override
    public void removeChannel(String conversationId, String channelName) {
        if (!tryConnect()) {
            fallback.removeChannel(conversationId, channelName);
            return;
        }
        TcpPacket req = new TcpPacket("REMOVE_CHANNEL_REQ", UUID.randomUUID().toString())
                .put("conversationId", conversationId)
                .put("channelName", channelName);
        request(req);
    }

    @Override
    public void addVoiceChannel(String conversationId, String voiceChannelName) {
        if (!tryConnect()) {
            fallback.addVoiceChannel(conversationId, voiceChannelName);
            return;
        }
        TcpPacket req = new TcpPacket("ADD_VOICE_CHANNEL_REQ", UUID.randomUUID().toString())
                .put("conversationId", conversationId)
                .put("channelName", voiceChannelName);
        request(req);
    }

    @Override
    public void removeVoiceChannel(String conversationId, String voiceChannelName) {
        if (!tryConnect()) {
            fallback.removeVoiceChannel(conversationId, voiceChannelName);
            return;
        }
        TcpPacket req = new TcpPacket("REMOVE_VOICE_CHANNEL_REQ", UUID.randomUUID().toString())
                .put("conversationId", conversationId)
                .put("channelName", voiceChannelName);
        request(req);
    }

    public void close() {
        closed = true;
        connected = false;
        try {
            if (udpVoiceSocket != null) udpVoiceSocket.close();
        } catch (Exception ignored) {
        }
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }

    private UserProfile toDomainUser(UserDto u) {
        return new UserProfile(u.id(), u.displayName(), u.email(),
                u.username(), u.bio(), u.status(), u.avatarPath());
    }

    private Conversation toDomainConversation(ConversationDto c) {
        return new Conversation(
                c.id(),
                c.displayName(),
                c.initials(),
                c.about(),
                c.online(),
                c.isGroup(),
                c.members() != null ? c.members() : List.of(),
                c.channels() != null ? c.channels() : List.of(),
                c.voiceChannels() != null ? c.voiceChannels() : List.of()
        );
    }

    private ChatMessage toDomainMessage(MessageDto m) {
        LocalDateTime sentAt;
        try {
            sentAt = LocalDateTime.parse(m.sentAt());
        } catch (Exception e) {
            sentAt = LocalDateTime.now();
        }
        boolean fromMe = currentUserId != null
                ? currentUserId.equals(m.senderId())
                : "user".equals(m.senderId());
        return new ChatMessage(
                m.id(),
                m.conversationId(),
                m.channel() != null ? m.channel() : "",
                m.senderId(),
                m.senderName(),
                m.senderInitials(),
                m.content(),
                sentAt,
                fromMe,
                m.edited(),
                m.recalled(),
                m.attachmentName(),
                m.attachmentSize(),
                m.attachmentFailed(),
                m.reactions() != null ? m.reactions() : java.util.Collections.emptyMap()
        );
    }

    @Override
    public void reactMessage(String conversationId, String messageId, String emoji) {
        if (!tryConnect()) {
            fallback.reactMessage(conversationId, messageId, emoji);
            return;
        }
        if (messageId == null || emoji == null || emoji.isBlank()) return;
        TcpPacket p = new TcpPacket("REACT_MSG_REQ", java.util.UUID.randomUUID().toString())
                .put("conversationId", conversationId != null ? conversationId : "")
                .put("userId", currentUserId != null ? currentUserId : "")
                .put("messageId", messageId)
                .put("emoji", emoji);
        try {
            request(p);
        } catch (Exception e) {
            System.err.println("[TcpChatGateway] Error sending reaction: " + e.getMessage());
        }
    }
}


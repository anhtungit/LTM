package vn.edu.ltm.chat.infrastructure.demo;

import vn.edu.ltm.chat.application.ChatGateway;
import vn.edu.ltm.chat.domain.ChatMessage;
import vn.edu.ltm.chat.domain.Conversation;
import vn.edu.ltm.chat.domain.UserProfile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A disposable in-memory adapter implementing local mock data with per-channel storage. */
public final class InMemoryChatGateway implements ChatGateway {
    private final Map<String, Conversation> conversations = new LinkedHashMap<>();
    private final Map<String, List<ChatMessage>> messages = new LinkedHashMap<>();
    private Runnable onRealtimeUpdate;

    public InMemoryChatGateway() {
        // Direct messages
        addConversation(new Conversation("mai-phuong", "Mai Phương", "MP",
                "Thiết kế sản phẩm · thích vẽ và chơi game cùng bạn bè.", true));
        addConversation(new Conversation("an-nguyen", "An Nguyễn", "AN",
                "Lập trình backend · đang thử luồng chat cục bộ.", false));
        addConversation(new Conversation("duc-khanh", "Đức Khánh", "ĐK",
                "Bạn học chung · kiểm tra phần giao diện.", true));
        addConversation(new Conversation("ha-linh", "Hà Linh", "HL",
                "Bạn học chung · trò chuyện demo.", false));

        // Group 1: Nhóm bạn thân (HB)
        Conversation groupStudy = new Conversation("nhom-ban-than", "Nhóm bạn thân", "HB",
                "Cùng nhau học, cùng nhau tiến bộ.", false, true,
                List.of("Thảo Nguyễn", "Mai Linh", "Bảo Long", "Đức Minh"),
                List.of("tổng-quan", "học-tập", "ảnh-chia-sẻ"),
                List.of("Phòng chill", "Góc học chung"));
        addConversation(groupStudy);

        // Group 2: LTM Studio (LT)
        Conversation groupStudio = new Conversation("ltm-studio", "LTM Studio", "LT",
                "Nhóm phát triển hệ thống mạng máy tính.", false, true,
                List.of("Minh Anh", "Khánh Linh", "Hữu Phúc", "Thành Đạt"),
                List.of("chung", "chia-sẻ-tài-liệu"),
                List.of("phòng-họp", "Thảo luận nhanh"));
        addConversation(groupStudio);

        // Group 3: Hội Chill (CL)
        Conversation groupChill = new Conversation("phong-chill", "Hội Bạn Chill", "CL",
                "Góc giải trí và trò chuyện cuối tuần.", false, true,
                List.of("Hà My", "Quốc Thái", "Thanh Duy"),
                List.of("trò-chuyện", "âm-nhạc"),
                List.of("Phòng nghe nhạc"));
        addConversation(groupChill);

        LocalDateTime now = LocalDateTime.now();

        // Seed DM messages
        addSeedMessage("mai-phuong", "", "mai-phuong", "Mai Phương", "MP",
                "Chào bạn, bản thử chat đã mở được chưa?", now.minusMinutes(18), false, false, null, 0, false);
        addSeedMessage("mai-phuong", "", "mai-phuong", "Mai Phương", "MP",
                "Mình đang thử gửi tin nhắn trong chế độ cục bộ đây.", now.minusMinutes(17), false, false, null, 0, false);
        addSeedMessage("mai-phuong", "", "user", "Bạn", "BN",
                "Mình sẽ cập nhật tiêu đề và gửi bản xem trước cho cậu nhé.", now.minusMinutes(16), true, true, null, 0, false);
        addSeedMessage("mai-phuong", "", "user", "Bạn", "BN",
                "Gửi bạn sơ đồ kiến trúc luồng chat.", now.minusMinutes(5), true, false,
                "architecture-chat-flow-v2.png", 2_400_000L, true);

        addSeedMessage("an-nguyen", "", "an-nguyen", "An Nguyễn", "AN",
                "Mình có thể chọn cuộc trò chuyện ở danh sách bên trái.", now.minusHours(2), false, false, null, 0, false);
        addSeedMessage("duc-khanh", "", "duc-khanh", "Đức Khánh", "ĐK",
                "Khi kết nối server, phần này sẽ dùng dữ liệu từ backend.", now.minusHours(5), false, false, null, 0, false);
        addSeedMessage("ha-linh", "", "ha-linh", "Hà Linh", "HL",
                "Chào bạn! Đây là hội thoại minh họa trong bản đầu.", now.minusDays(1), false, false, null, 0, false);

        // Seed Group 1: #học-tập (Canvas 01)
        addSeedMessage("nhom-ban-than", "học-tập", "mai-linh", "Mai Linh", "ML",
                "Mình gom tài liệu ôn tập vào đây nhé. Mọi người xem thử kế hoạch tuần này có cần thêm gì không?",
                now.minusMinutes(25), false, false, null, 0, false);
        addSeedMessage("nhom-ban-than", "học-tập", "mai-linh", "Mai Linh", "ML",
                "Đây là góc học tập mình vừa sắp xếp lại, gửi mọi người xem bố cục nha.",
                now.minusMinutes(23), false, false, "setup-moi.jpg", 1_800_000L, false);
        addSeedMessage("nhom-ban-than", "học-tập", "bao-long", "Bảo Long", "BL",
                "Mình gửi bản tổng hợp buổi học hôm qua ở đây. Mọi người tải về xem nhé.",
                now.minusMinutes(15), false, false, "Tổng_hợp_bài_giảng_tuần_04.pdf", 2_400_000L, false);
        addSeedMessage("nhom-ban-than", "học-tập", "duc-minh", "Đức Minh", "ĐM",
                "Đẹp đó Linh, mình sẽ thêm lịch ôn vào tối thứ năm.",
                now.minusMinutes(10), false, false, null, 0, false);

        // Seed Group 1: #tổng-quan
        addSeedMessage("nhom-ban-than", "tổng-quan", "thao-nguyen", "Thảo Nguyễn", "TN",
                "Chào mừng mọi người đến với kênh tổng quan của nhóm bạn thân!",
                now.minusHours(3), false, false, null, 0, false);
        addSeedMessage("nhom-ban-than", "tổng-quan", "duc-minh", "Đức Minh", "ĐM",
                "Tuần này chúng ta sẽ tập trung hoàn thiện bài tập lớn LTM nhé.",
                now.minusHours(2), false, false, null, 0, false);

        // Seed Group 1: #ảnh-chia-sẻ
        addSeedMessage("nhom-ban-than", "ảnh-chia-sẻ", "bao-long", "Bảo Long", "BL",
                "Kênh lưu trữ hình ảnh kỷ niệm của nhóm.",
                now.minusDays(2), false, false, null, 0, false);

        // Seed Group 2: #chung
        addSeedMessage("ltm-studio", "chung", "minh-anh", "Minh Anh", "MA",
                "Chào mừng team LTM Studio! Báo cáo tiến độ các module tại đây.",
                now.minusHours(4), false, false, null, 0, false);
    }

    private String makeKey(String conversationId, String channel) {
        if (channel == null || channel.isBlank()) {
            return conversationId;
        }
        return conversationId + "#" + channel;
    }

    private void addConversation(Conversation conversation) {
        conversations.put(conversation.id(), conversation);
        messages.putIfAbsent(conversation.id(), new ArrayList<>());
        for (String chan : conversation.channels()) {
            messages.putIfAbsent(makeKey(conversation.id(), chan), new ArrayList<>());
        }
    }

    private void addSeedMessage(String conversationId, String channel, String senderId, String senderName,
                                String senderInitials, String text, LocalDateTime sentAt,
                                boolean fromCurrentUser, boolean edited, String attachmentName,
                                long attachmentSize, boolean attachmentFailed) {
        String key = makeKey(conversationId, channel);
        messages.computeIfAbsent(key, k -> new ArrayList<>()).add(new ChatMessage(
                UUID.randomUUID().toString(), conversationId, channel, senderId, senderName,
                senderInitials, text, sentAt, fromCurrentUser, edited, false,
                attachmentName, attachmentSize, attachmentFailed));
    }

    @Override
    public synchronized List<Conversation> listConversations() {
        return List.copyOf(conversations.values());
    }

    @Override
    public synchronized List<UserProfile> listUsers() {
        List<UserProfile> users = new ArrayList<>();
        for (Conversation c : conversations.values()) {
            if (!c.isGroup()) {
                users.add(new UserProfile(c.id(), c.displayName(), c.id() + "@example.com",
                        "@" + c.id().replace("-", ""), c.about(), c.online() ? "online" : "offline", null));
            }
        }
        return users;
    }

    @Override
    public synchronized Conversation openDirectMessage(String peerId) {
        if (peerId == null || peerId.isBlank()) return null;
        Conversation existing = conversations.get(peerId);
        if (existing != null) {
            return existing;
        }
        String initials = peerId.length() >= 2 ? peerId.substring(0, 2).toUpperCase() : peerId.toUpperCase();
        Conversation created = new Conversation(peerId, peerId, initials, "Trò chuyện trực tiếp", true);
        addConversation(created);
        return created;
    }

    @Override
    public synchronized void closeDirectMessage(String peerId) {
        if (peerId == null || peerId.isBlank()) return;
        conversations.remove(peerId);
    }

    @Override
    public synchronized List<ChatMessage> loadMessages(String conversationId, String channel) {
        String key = makeKey(conversationId, channel);
        List<ChatMessage> history = messages.get(key);
        if (history == null) {
            history = messages.computeIfAbsent(key, k -> new ArrayList<>());
        }
        return List.copyOf(history);
    }

    @Override
    public synchronized ChatMessage sendMessage(String conversationId, String channel, UserProfile sender, String content) {
        String key = makeKey(conversationId, channel);
        List<ChatMessage> history = messages.computeIfAbsent(key, k -> new ArrayList<>());
        ChatMessage message = new ChatMessage(
                UUID.randomUUID().toString(), conversationId, channel, sender.id(), sender.displayName(),
                sender.initials(), content, LocalDateTime.now(), true);
        history.add(message);
        return message;
    }

    @Override
    public synchronized ChatMessage editMessage(String conversationId, String messageId, String newContent) {
        for (List<ChatMessage> history : messages.values()) {
            for (int i = 0; i < history.size(); i++) {
                ChatMessage msg = history.get(i);
                if (msg.id().equals(messageId)) {
                    ChatMessage updated = msg.withContent(newContent);
                    history.set(i, updated);
                    return updated;
                }
            }
        }
        throw new IllegalArgumentException("Không tìm thấy tin nhắn cần sửa.");
    }

    @Override
    public synchronized void deleteMessage(String conversationId, String messageId) {
        for (List<ChatMessage> history : messages.values()) {
            history.removeIf(msg -> msg.id().equals(messageId));
        }
    }

    @Override
    public synchronized ChatMessage recallMessage(String conversationId, String messageId) {
        for (List<ChatMessage> history : messages.values()) {
            for (int i = 0; i < history.size(); i++) {
                ChatMessage msg = history.get(i);
                if (msg.id().equals(messageId)) {
                    ChatMessage updated = msg.asRecalled();
                    history.set(i, updated);
                    return updated;
                }
            }
        }
        throw new IllegalArgumentException("Không tìm thấy tin nhắn cần thu hồi.");
    }

    @Override
    public void setOnRealtimeUpdate(Runnable callback) {
        this.onRealtimeUpdate = callback;
    }

    @Override
    public synchronized void reactMessage(String conversationId, String messageId, String emoji) {
        if (messageId == null || emoji == null || emoji.isBlank()) return;
        for (List<ChatMessage> history : messages.values()) {
            for (int i = 0; i < history.size(); i++) {
                ChatMessage msg = history.get(i);
                if (msg.id().equals(messageId)) {
                    history.set(i, msg.toggleReaction(emoji, "me"));
                    if (onRealtimeUpdate != null) {
                        onRealtimeUpdate.run();
                    }
                    return;
                }
            }
        }
    }

    @Override
    public synchronized ChatMessage sendAttachment(String conversationId, String channel, UserProfile sender,
                                                   String fileName, long fileSize, boolean failed) {
        String key = makeKey(conversationId, channel);
        List<ChatMessage> history = messages.computeIfAbsent(key, k -> new ArrayList<>());
        ChatMessage message = new ChatMessage(
                UUID.randomUUID().toString(), conversationId, channel, sender.id(), sender.displayName(),
                sender.initials(), "Đã gửi tệp: " + fileName, LocalDateTime.now(), true,
                false, false, fileName, fileSize, failed);
        history.add(message);
        return message;
    }

    @Override
    public synchronized Conversation createGroup(String groupName, List<String> memberNames) {
        String id = "group-" + UUID.randomUUID().toString().substring(0, 8);
        String name = (groupName == null || groupName.isBlank()) ? "Nhóm mới" : groupName.trim();
        String initials = name.length() >= 2 ? name.substring(0, 2).toUpperCase() : name.toUpperCase();
        Conversation group = new Conversation(id, name, initials, "Nhóm mới tạo trong phiên demo",
                false, true, memberNames, List.of("tổng-quan", "học-tập"), List.of("Phòng chill"));
        addConversation(group);
        return group;
    }

    @Override
    public synchronized void addChannel(String conversationId, String channelName) {
        Conversation c = conversations.get(conversationId);
        if (c != null && c.isGroup()) {
            Conversation updated = c.withAddedChannel(channelName);
            conversations.put(conversationId, updated);
            messages.putIfAbsent(makeKey(conversationId, channelName), new ArrayList<>());
        }
    }

    @Override
    public synchronized void removeChannel(String conversationId, String channelName) {
        Conversation c = conversations.get(conversationId);
        if (c != null && c.isGroup()) {
            Conversation updated = c.withRemovedChannel(channelName);
            conversations.put(conversationId, updated);
        }
    }

    @Override
    public synchronized void addVoiceChannel(String conversationId, String voiceChannelName) {
        Conversation c = conversations.get(conversationId);
        if (c != null && c.isGroup()) {
            Conversation updated = c.withAddedVoiceChannel(voiceChannelName);
            conversations.put(conversationId, updated);
        }
    }

    @Override
    public synchronized void removeVoiceChannel(String conversationId, String voiceChannelName) {
        Conversation c = conversations.get(conversationId);
        if (c != null && c.isGroup()) {
            Conversation updated = c.withRemovedVoiceChannel(voiceChannelName);
            conversations.put(conversationId, updated);
        }
    }
}

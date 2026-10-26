package vn.edu.ltm.chat.application;

import vn.edu.ltm.chat.domain.ChatMessage;
import vn.edu.ltm.chat.domain.Conversation;
import vn.edu.ltm.chat.domain.UserProfile;

import java.util.List;

public final class ChatService {
    private final ChatGateway gateway;

    public ChatService(ChatGateway gateway) {
        this.gateway = gateway;
    }

    public ChatGateway gateway() {
        return gateway;
    }

    public boolean isServerConnected() {
        return gateway.isServerConnected();
    }

    public boolean isUserOnline(String userId) {
        return gateway.isUserOnline(userId);
    }

    public void setOnRealtimeUpdate(Runnable callback) {
        gateway.setOnRealtimeUpdate(callback);
    }

    public void setOnPresenceUpdate(Runnable callback) {
        gateway.setOnPresenceUpdate(callback);
    }

    public UserProfile updateProfile(UserProfile user) {
        return gateway.updateProfile(user);
    }

    public byte[] downloadAttachment(String messageId, String fileName) {
        return gateway.downloadAttachment(messageId, fileName);
    }

    public List<Conversation> conversations() {
        return gateway.listConversations();
    }

    public List<UserProfile> listUsers() {
        return gateway.listUsers();
    }

    public Conversation openDirectMessage(String peerId) {
        return gateway.openDirectMessage(peerId);
    }

    public List<ChatMessage> messages(String conversationId) {
        return gateway.loadMessages(conversationId, "");
    }

    public List<ChatMessage> messages(String conversationId, String channel) {
        return gateway.loadMessages(conversationId, channel != null ? channel : "");
    }

    public ChatMessage send(String conversationId, UserProfile sender, String content) {
        return send(conversationId, "", sender, content);
    }

    public ChatMessage send(String conversationId, String channel, UserProfile sender, String content) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("Chọn một cuộc trò chuyện trước khi gửi.");
        }
        if (sender == null) {
            throw new IllegalArgumentException("Phiên demo đã kết thúc. Vui lòng đăng nhập lại.");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Tin nhắn không được để trống.");
        }
        return gateway.sendMessage(conversationId, channel != null ? channel : "", sender, content.trim());
    }

    public ChatMessage edit(String conversationId, String messageId, String newContent) {
        if (newContent == null || newContent.isBlank()) {
            throw new IllegalArgumentException("Nội dung tin nhắn không được để trống.");
        }
        return gateway.editMessage(conversationId, messageId, newContent.trim());
    }

    public void delete(String conversationId, String messageId) {
        gateway.deleteMessage(conversationId, messageId);
    }

    public ChatMessage recall(String conversationId, String messageId) {
        return gateway.recallMessage(conversationId, messageId);
    }

    public ChatMessage sendAttachment(String conversationId, UserProfile sender, String fileName, long fileSize, boolean failed) {
        return sendAttachment(conversationId, "", sender, fileName, fileSize, failed);
    }

    public ChatMessage sendAttachment(String conversationId, String channel, UserProfile sender, String fileName, long fileSize, boolean failed) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("Chọn một cuộc trò chuyện trước khi gửi tệp.");
        }
        if (sender == null) {
            throw new IllegalArgumentException("Phiên demo đã kết thúc. Vui lòng đăng nhập lại.");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Tên tệp không hợp lệ.");
        }
        return gateway.sendAttachment(conversationId, channel != null ? channel : "", sender, fileName, fileSize, failed);
    }

    public Conversation createGroup(String groupName, List<String> memberNames) {
        return gateway.createGroup(groupName, memberNames);
    }

    public void addChannel(String conversationId, String channelName) {
        gateway.addChannel(conversationId, channelName);
    }

    public void removeChannel(String conversationId, String channelName) {
        gateway.removeChannel(conversationId, channelName);
    }

    public void addVoiceChannel(String conversationId, String voiceChannelName) {
        gateway.addVoiceChannel(conversationId, voiceChannelName);
    }

    public void removeVoiceChannel(String conversationId, String voiceChannelName) {
        gateway.removeVoiceChannel(conversationId, voiceChannelName);
    }
}

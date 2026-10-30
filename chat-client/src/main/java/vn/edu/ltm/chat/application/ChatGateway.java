package vn.edu.ltm.chat.application;

import vn.edu.ltm.chat.domain.ChatMessage;
import vn.edu.ltm.chat.domain.Conversation;
import vn.edu.ltm.chat.domain.UserProfile;

import java.util.List;

/** Boundary for chat data. */
public interface ChatGateway {
    default UserProfile login(String email, String password) {
        throw new UnsupportedOperationException("Gateway does not support network login");
    }

    default UserProfile register(String email, String password, String displayName) {
        throw new UnsupportedOperationException("Gateway does not support network register");
    }

    default UserProfile updateProfile(UserProfile user) {
        return user;
    }

    default boolean isServerConnected() {
        return false;
    }

    default boolean isUserOnline(String userId) {
        return false;
    }

    default void setOnRealtimeUpdate(Runnable callback) {
    }

    default void setOnPresenceUpdate(Runnable callback) {
    }

    default void setOnTypingUpdate(java.util.function.Consumer<TypingEvent> callback) {
    }

    default void sendTyping(String conversationId, String channel, boolean typing) {
    }

    record TypingEvent(String conversationId, String channel, String senderId, String senderName, boolean typing) {}

    default void setOnCallSignal(java.util.function.Consumer<CallSignalEvent> callback) {
    }

    default void sendCallSignal(String conversationId, String channel, String action, String data) {
    }

    record CallSignalEvent(String conversationId, String channel, String senderId, String senderName, String action, String data) {}

    default byte[] downloadAttachment(String messageId, String fileName) {
        return null;
    }

    List<Conversation> listConversations();

    default List<UserProfile> listUsers() {
        return List.of();
    }

    default Conversation openDirectMessage(String peerId) {
        return null;
    }

    default void closeDirectMessage(String peerId) {
    }

    default List<ChatMessage> loadMessages(String conversationId) {
        return loadMessages(conversationId, "");
    }

    List<ChatMessage> loadMessages(String conversationId, String channel);

    default ChatMessage sendMessage(String conversationId, UserProfile sender, String content) {
        return sendMessage(conversationId, "", sender, content);
    }

    ChatMessage sendMessage(String conversationId, String channel, UserProfile sender, String content);

    ChatMessage editMessage(String conversationId, String messageId, String newContent);

    void deleteMessage(String conversationId, String messageId);

    ChatMessage recallMessage(String conversationId, String messageId);

    default void reactMessage(String conversationId, String messageId, String emoji) {}

    default ChatMessage sendAttachment(String conversationId, UserProfile sender, String fileName, long fileSize, boolean failed) {
        return sendAttachment(conversationId, "", sender, fileName, fileSize, failed);
    }

    ChatMessage sendAttachment(String conversationId, String channel, UserProfile sender, String fileName, long fileSize, boolean failed);

    Conversation createGroup(String groupName, List<String> memberNames);

    void addChannel(String conversationId, String channelName);

    void removeChannel(String conversationId, String channelName);

    void addVoiceChannel(String conversationId, String voiceChannelName);

    void removeVoiceChannel(String conversationId, String voiceChannelName);
}

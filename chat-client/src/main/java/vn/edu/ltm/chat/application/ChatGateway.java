package vn.edu.ltm.chat.application;

import vn.edu.ltm.chat.domain.ChatMessage;
import vn.edu.ltm.chat.domain.Conversation;
import vn.edu.ltm.chat.domain.UserProfile;

import java.util.List;

/** Boundary for chat data. */
public interface ChatGateway {
    List<Conversation> listConversations();

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

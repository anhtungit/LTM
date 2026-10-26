package vn.edu.ltm.chat.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ChatMessage(
        String id,
        String conversationId,
        String channel,
        String senderId,
        String senderName,
        String senderInitials,
        String content,
        LocalDateTime sentAt,
        boolean fromCurrentUser,
        boolean edited,
        boolean recalled,
        String attachmentName,
        long attachmentSize,
        boolean attachmentFailed,
        Map<String, List<String>> reactions
) {
    public ChatMessage(
            String id,
            String conversationId,
            String senderId,
            String senderName,
            String senderInitials,
            String content,
            LocalDateTime sentAt,
            boolean fromCurrentUser
    ) {
        this(id, conversationId, "", senderId, senderName, senderInitials, content, sentAt, fromCurrentUser,
                false, false, null, 0L, false, Collections.emptyMap());
    }

    public ChatMessage(
            String id,
            String conversationId,
            String channel,
            String senderId,
            String senderName,
            String senderInitials,
            String content,
            LocalDateTime sentAt,
            boolean fromCurrentUser
    ) {
        this(id, conversationId, channel, senderId, senderName, senderInitials, content, sentAt, fromCurrentUser,
                false, false, null, 0L, false, Collections.emptyMap());
    }

    public ChatMessage(
            String id,
            String conversationId,
            String channel,
            String senderId,
            String senderName,
            String senderInitials,
            String content,
            LocalDateTime sentAt,
            boolean fromCurrentUser,
            boolean edited,
            boolean recalled,
            String attachmentName,
            long attachmentSize,
            boolean attachmentFailed
    ) {
        this(id, conversationId, channel, senderId, senderName, senderInitials, content, sentAt, fromCurrentUser,
                edited, recalled, attachmentName, attachmentSize, attachmentFailed, Collections.emptyMap());
    }

    public ChatMessage(
            String id,
            String conversationId,
            String senderId,
            String senderName,
            String senderInitials,
            String content,
            LocalDateTime sentAt,
            boolean fromCurrentUser,
            boolean edited,
            boolean recalled,
            String attachmentName,
            long attachmentSize,
            boolean attachmentFailed
    ) {
        this(id, conversationId, "", senderId, senderName, senderInitials, content, sentAt, fromCurrentUser,
                edited, recalled, attachmentName, attachmentSize, attachmentFailed, Collections.emptyMap());
    }

    public ChatMessage {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId is required");
        }
        channel = channel == null ? "" : channel;
        if (senderName == null || senderName.isBlank()) {
            throw new IllegalArgumentException("senderName is required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content is required");
        }
        if (sentAt == null) {
            throw new IllegalArgumentException("sentAt is required");
        }
        reactions = reactions != null ? Collections.unmodifiableMap(new LinkedHashMap<>(reactions)) : Collections.emptyMap();
    }

    public boolean hasAttachment() {
        return attachmentName != null && !attachmentName.isBlank();
    }

    public ChatMessage withContent(String newContent) {
        return new ChatMessage(id, conversationId, channel, senderId, senderName, senderInitials, newContent,
                sentAt, fromCurrentUser, true, recalled, attachmentName, attachmentSize, attachmentFailed, reactions);
    }

    public ChatMessage asRecalled() {
        return new ChatMessage(id, conversationId, channel, senderId, senderName, senderInitials,
                "Tin nhắn đã được thu hồi", sentAt, fromCurrentUser, false, true, null, 0L, false, Collections.emptyMap());
    }

    public ChatMessage withAttachmentStatus(boolean failed) {
        return new ChatMessage(id, conversationId, channel, senderId, senderName, senderInitials, content,
                sentAt, fromCurrentUser, edited, recalled, attachmentName, attachmentSize, failed, reactions);
    }

    public ChatMessage withReactions(Map<String, List<String>> newReactions) {
        return new ChatMessage(id, conversationId, channel, senderId, senderName, senderInitials, content,
                sentAt, fromCurrentUser, edited, recalled, attachmentName, attachmentSize, attachmentFailed, newReactions);
    }

    public ChatMessage toggleReaction(String emoji, String userId) {
        if (emoji == null || emoji.isBlank() || userId == null || userId.isBlank()) {
            return this;
        }
        Map<String, List<String>> newReactions = new LinkedHashMap<>(reactions);
        List<String> users = new ArrayList<>(newReactions.getOrDefault(emoji, List.of()));
        boolean alreadyReacted = users.contains(userId) || ("me".equals(userId) && users.contains("me"));
        if (alreadyReacted) {
            users.remove(userId);
            users.remove("me");
            if (users.isEmpty()) {
                newReactions.remove(emoji);
            } else {
                newReactions.put(emoji, users);
            }
        } else {
            users.add(userId);
            newReactions.put(emoji, users);
        }
        return withReactions(newReactions);
    }
}

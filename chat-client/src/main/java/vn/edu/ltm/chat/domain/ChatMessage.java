package vn.edu.ltm.chat.domain;

import java.time.LocalDateTime;

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
        boolean attachmentFailed
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
                false, false, null, 0L, false);
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
                false, false, null, 0L, false);
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
                edited, recalled, attachmentName, attachmentSize, attachmentFailed);
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
    }

    public boolean hasAttachment() {
        return attachmentName != null && !attachmentName.isBlank();
    }

    public ChatMessage withContent(String newContent) {
        return new ChatMessage(id, conversationId, channel, senderId, senderName, senderInitials, newContent,
                sentAt, fromCurrentUser, true, recalled, attachmentName, attachmentSize, attachmentFailed);
    }

    public ChatMessage asRecalled() {
        return new ChatMessage(id, conversationId, channel, senderId, senderName, senderInitials,
                "Tin nhắn đã được thu hồi", sentAt, fromCurrentUser, false, true, null, 0L, false);
    }

    public ChatMessage withAttachmentStatus(boolean failed) {
        return new ChatMessage(id, conversationId, channel, senderId, senderName, senderInitials, content,
                sentAt, fromCurrentUser, edited, recalled, attachmentName, attachmentSize, failed);
    }
}

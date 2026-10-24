package vn.edu.ltm.chat.server.protocol;

import java.util.List;

/**
 * Data Transfer Objects (DTOs) for JSON serialization over TCP.
 */
public final class ProtocolDtos {
    private ProtocolDtos() {
    }

    public record UserDto(
            String id,
            String displayName,
            String email,
            String username,
            String bio,
            String status,
            String avatarPath
    ) {}

    public record ConversationDto(
            String id,
            String displayName,
            String initials,
            String about,
            boolean online,
            boolean isGroup,
            List<String> members,
            List<String> channels,
            List<String> voiceChannels
    ) {}

    public record MessageDto(
            String id,
            String conversationId,
            String channel,
            String senderId,
            String senderName,
            String senderInitials,
            String content,
            String sentAt,
            boolean edited,
            boolean recalled,
            String attachmentName,
            long attachmentSize,
            boolean attachmentFailed
    ) {}
}


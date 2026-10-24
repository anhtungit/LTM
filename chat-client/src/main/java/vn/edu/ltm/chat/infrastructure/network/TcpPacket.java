package vn.edu.ltm.chat.infrastructure.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Packet and DTO structures matching the TCP Server JSON newline protocol.
 */
public final class TcpPacket {
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private String type;
    private String requestId;
    private boolean ok = true;
    private String error;
    private JsonObject payload;

    public TcpPacket() {
        this.payload = new JsonObject();
    }

    public TcpPacket(String type, String requestId) {
        this.type = type;
        this.requestId = requestId;
        this.payload = new JsonObject();
    }

    public TcpPacket put(String key, String value) {
        payload.addProperty(key, value);
        return this;
    }

    public TcpPacket put(String key, Number value) {
        payload.addProperty(key, value);
        return this;
    }

    public TcpPacket put(String key, Boolean value) {
        payload.addProperty(key, value);
        return this;
    }

    public TcpPacket putObj(String key, Object obj) {
        payload.add(key, GSON.toJsonTree(obj));
        return this;
    }

    public String getString(String key, String defaultValue) {
        if (payload == null || !payload.has(key) || payload.get(key).isJsonNull()) {
            return defaultValue;
        }
        return payload.get(key).getAsString();
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        if (payload == null || !payload.has(key) || payload.get(key).isJsonNull()) {
            return defaultValue;
        }
        return payload.get(key).getAsBoolean();
    }

    public <T> T getObj(String key, Class<T> clazz) {
        if (payload == null || !payload.has(key) || payload.get(key).isJsonNull()) {
            return null;
        }
        JsonElement el = payload.get(key);
        return GSON.fromJson(el, clazz);
    }

    public String getType() {
        return type;
    }

    public String getRequestId() {
        return requestId;
    }

    public boolean isOk() {
        return ok;
    }

    public String getError() {
        return error;
    }

    public String toJsonLine() {
        return GSON.toJson(this);
    }

    public static TcpPacket fromJsonLine(String line) {
        return GSON.fromJson(line, TcpPacket.class);
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


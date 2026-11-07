package vn.edu.ltm.chat.application;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Trạng thái thuần của cuộc gọi đang tham gia và danh sách người trong kênh thoại nhóm (không phụ thuộc Swing). */
public final class CallState {

    private volatile String conversationId;
    private volatile String channel = "";
    private volatile String textChannel = "";

    private final Set<String> mutedPeers = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> camOffTimestamps = new ConcurrentHashMap<>();
    /** Who is in each group voice channel, keyed by conversationId|channel, built from INVITE/ACCEPT/LEAVE signals. */
    private final Map<String, Map<String, String>> voiceRoster = new ConcurrentHashMap<>();

    public String conversationId() {
        return conversationId;
    }

    public String channel() {
        return channel;
    }

    public String textChannel() {
        return textChannel;
    }

    public boolean isActive() {
        return conversationId != null;
    }

    public Set<String> mutedPeers() {
        return mutedPeers;
    }

    public Map<String, Long> camOffTimestamps() {
        return camOffTimestamps;
    }

    public void begin(String conversationId, String channel, String textChannel) {
        this.conversationId = conversationId;
        this.channel = channel != null ? channel : "";
        this.textChannel = textChannel != null ? textChannel : "";
        mutedPeers.clear();
    }

    public void end() {
        this.conversationId = null;
    }

    /** True nếu tín hiệu thuộc cuộc gọi đang tham gia (kênh rỗng khớp mọi kênh). */
    public boolean matches(String eventConversationId, String eventChannel) {
        String cid = conversationId;
        if (cid == null || !cid.equals(eventConversationId)) return false;
        String a = channel == null ? "" : channel;
        String b = eventChannel == null ? "" : eventChannel;
        return a.isEmpty() || b.isEmpty() || a.equals(b);
    }

    public static String rosterKey(String conversationId, String channel) {
        return conversationId + "|" + (channel == null ? "" : channel);
    }

    public Map<String, String> roster(String conversationId, String channel) {
        return voiceRoster.get(rosterKey(conversationId, channel));
    }

    /** Cập nhật danh sách kênh thoại theo tín hiệu; trả về true nếu danh sách thay đổi. */
    public boolean applyRosterSignal(String conversationId, String channel, String senderId, String senderName, String action) {
        String key = rosterKey(conversationId, channel);
        if ("INVITE".equals(action) || "ACCEPT".equals(action)) {
            voiceRoster.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(senderId, senderName);
            return true;
        }
        if ("LEAVE".equals(action) || "REJECT".equals(action)) {
            Map<String, String> m = voiceRoster.get(key);
            return m != null && m.remove(senderId) != null;
        }
        return false;
    }
}

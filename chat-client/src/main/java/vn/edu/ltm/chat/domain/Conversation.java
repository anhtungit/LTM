package vn.edu.ltm.chat.domain;

import java.util.ArrayList;
import java.util.List;

public record Conversation(
        String id,
        String displayName,
        String initials,
        String about,
        boolean online,
        boolean isGroup,
        List<String> members,
        List<String> channels,
        List<String> voiceChannels
) {
    public Conversation(String id, String displayName, String initials, String about) {
        this(id, displayName, initials, about, false, false, List.of(), List.of(), List.of());
    }

    public Conversation(String id, String displayName, String initials, String about, boolean online) {
        this(id, displayName, initials, about, online, false, List.of(), List.of(), List.of());
    }

    public Conversation(String id, String displayName, String initials, String about, boolean online,
                        boolean isGroup, List<String> members, List<String> channels) {
        this(id, displayName, initials, about, online, isGroup, members, channels, List.of("Phòng chill"));
    }

    public Conversation {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName is required");
        }
        if (initials == null || initials.isBlank()) {
            throw new IllegalArgumentException("initials is required");
        }
        about = about == null ? "" : about;
        members = members == null ? List.of() : List.copyOf(members);
        channels = channels == null ? List.of() : List.copyOf(channels);
        voiceChannels = voiceChannels == null ? List.of() : List.copyOf(voiceChannels);
    }

    public Conversation withAddedChannel(String channelName) {
        List<String> list = new ArrayList<>(channels);
        if (!list.contains(channelName)) {
            list.add(channelName);
        }
        return new Conversation(id, displayName, initials, about, online, isGroup, members, list, voiceChannels);
    }

    public Conversation withRemovedChannel(String channelName) {
        List<String> list = new ArrayList<>(channels);
        list.remove(channelName);
        if (list.isEmpty()) {
            list.add("chung");
        }
        return new Conversation(id, displayName, initials, about, online, isGroup, members, list, voiceChannels);
    }

    public Conversation withAddedVoiceChannel(String voiceChannelName) {
        List<String> list = new ArrayList<>(voiceChannels);
        if (!list.contains(voiceChannelName)) {
            list.add(voiceChannelName);
        }
        return new Conversation(id, displayName, initials, about, online, isGroup, members, channels, list);
    }

    public Conversation withRemovedVoiceChannel(String voiceChannelName) {
        List<String> list = new ArrayList<>(voiceChannels);
        list.remove(voiceChannelName);
        if (list.isEmpty()) {
            list.add("Phòng họp");
        }
        return new Conversation(id, displayName, initials, about, online, isGroup, members, channels, list);
    }
}

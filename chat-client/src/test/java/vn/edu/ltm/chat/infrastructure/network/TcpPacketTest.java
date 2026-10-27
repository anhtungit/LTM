package vn.edu.ltm.chat.infrastructure.network;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import vn.edu.ltm.chat.infrastructure.network.TcpPacket.MessageDto;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TcpPacketTest {
    @Test
    void deserializesMessageDtoWithReactions() {
        String json = """
        {
            "id": "m-1",
            "conversationId": "c-1",
            "channel": "",
            "senderId": "u-1",
            "senderName": "User 1",
            "senderInitials": "U1",
            "content": "Hello",
            "sentAt": "2026-10-26T10:00:00",
            "edited": false,
            "recalled": false,
            "attachmentName": null,
            "attachmentSize": 0,
            "attachmentFailed": false,
            "reactions": {
                "👍": ["u-1", "u-2"],
                "❤️": ["u-1"]
            }
        }
        """;

        Gson gson = new Gson();
        MessageDto dto = gson.fromJson(json, MessageDto.class);
        assertNotNull(dto);
        assertNotNull(dto.reactions());
        assertEquals(2, dto.reactions().size());
        assertTrue(dto.reactions().containsKey("👍"));
        assertEquals(List.of("u-1", "u-2"), dto.reactions().get("👍"));
    }
}

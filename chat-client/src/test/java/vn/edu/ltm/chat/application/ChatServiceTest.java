package vn.edu.ltm.chat.application;

import org.junit.jupiter.api.Test;
import vn.edu.ltm.chat.domain.ChatMessage;
import vn.edu.ltm.chat.domain.UserProfile;
import vn.edu.ltm.chat.infrastructure.demo.InMemoryChatGateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatServiceTest {
    private final ChatService service = new ChatService(new InMemoryChatGateway());
    private final UserProfile user = new UserProfile("demo-user", "Minh Nguyễn", "minh@example.com");

    @Test
    void appendsMessageToSelectedLocalConversation() {
        ChatMessage sent = service.send("mai-phuong", user, "  Chào bạn  ");

        assertEquals("Chào bạn", sent.content());
        assertTrue(sent.fromCurrentUser());
        assertEquals("Minh Nguyễn", sent.senderName());
        assertEquals(sent, service.messages("mai-phuong").getLast());
    }

    @Test
    void rejectsBlankMessage() {
        assertThrows(IllegalArgumentException.class,
                () -> service.send("mai-phuong", user, "   "));
    }

    @Test
    void keepsConversationsSeparate() {
        int before = service.messages("an-nguyen").size();
        service.send("mai-phuong", user, "Chỉ ở cuộc trò chuyện này.");

        assertEquals(before, service.messages("an-nguyen").size());
    }
}

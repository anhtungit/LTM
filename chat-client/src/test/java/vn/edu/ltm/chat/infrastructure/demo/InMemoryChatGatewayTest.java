package vn.edu.ltm.chat.infrastructure.demo;

import org.junit.jupiter.api.Test;
import vn.edu.ltm.chat.domain.ChatMessage;
import vn.edu.ltm.chat.domain.Conversation;
import vn.edu.ltm.chat.domain.UserProfile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryChatGatewayTest {
    @Test
    void seededContentIsClearlyDemoAndPerConversation() {
        InMemoryChatGateway gateway = new InMemoryChatGateway();
        assertFalse(gateway.loadMessages("mai-phuong", "").isEmpty());
        assertEquals(7, gateway.listConversations().size());
        assertEquals(1, gateway.loadMessages("an-nguyen", "").size());
        assertFalse(gateway.loadMessages("nhom-ban-than", "học-tập").isEmpty());
    }

    @Test
    void separateChannelsHaveIndependentHistories() {
        InMemoryChatGateway gateway = new InMemoryChatGateway();
        UserProfile user = new UserProfile("demo", "Demo User", "demo@example.com");

        List<ChatMessage> hocTapMessages = gateway.loadMessages("nhom-ban-than", "học-tập");
        List<ChatMessage> tongQuanMessages = gateway.loadMessages("nhom-ban-than", "tổng-quan");

        // Verify channel messages are distinct
        assertNotEquals(hocTapMessages.size(), tongQuanMessages.size());

        // Send into tong-quan only
        gateway.sendMessage("nhom-ban-than", "tổng-quan", user, "Tin nhắn kênh tổng quan");
        assertEquals("Tin nhắn kênh tổng quan", gateway.loadMessages("nhom-ban-than", "tổng-quan").getLast().content());
        assertFalse(gateway.loadMessages("nhom-ban-than", "học-tập").stream()
                .anyMatch(m -> m.content().equals("Tin nhắn kênh tổng quan")));
    }

    @Test
    void sendsOnlyIntoSelectedInMemoryHistory() {
        InMemoryChatGateway gateway = new InMemoryChatGateway();
        UserProfile user = new UserProfile("demo", "Demo User", "demo@example.com");
        int otherConversationSize = gateway.loadMessages("an-nguyen", "").size();

        ChatMessage sent = gateway.sendMessage("mai-phuong", "", user, "Tin nhắn cục bộ");

        assertEquals("Tin nhắn cục bộ", gateway.loadMessages("mai-phuong", "").getLast().content());
        assertEquals(otherConversationSize, gateway.loadMessages("an-nguyen", "").size());
        assertEquals("demo", sent.senderId());
        assertEquals("mai-phuong", sent.conversationId());
        assertEquals(true, sent.fromCurrentUser());
    }

    @Test
    void editsAndRecallsMessages() {
        InMemoryChatGateway gateway = new InMemoryChatGateway();
        UserProfile user = new UserProfile("demo", "Demo User", "demo@example.com");
        ChatMessage sent = gateway.sendMessage("mai-phuong", "", user, "Nội dung ban đầu");

        ChatMessage edited = gateway.editMessage("mai-phuong", sent.id(), "Nội dung sau sửa");
        assertTrue(edited.edited());
        assertEquals("Nội dung sau sửa", edited.content());

        ChatMessage recalled = gateway.recallMessage("mai-phuong", sent.id());
        assertTrue(recalled.recalled());
        assertEquals("Tin nhắn đã được thu hồi", recalled.content());

        gateway.deleteMessage("mai-phuong", sent.id());
        assertFalse(gateway.loadMessages("mai-phuong", "").stream().anyMatch(m -> m.id().equals(sent.id())));
    }

    @Test
    void createsGroupAndManagesChannels() {
        InMemoryChatGateway gateway = new InMemoryChatGateway();
        Conversation group = gateway.createGroup("Đội dự án", List.of("Nguyễn A", "Trần B"));
        assertTrue(group.isGroup());
        assertEquals("Đội dự án", group.displayName());
        assertEquals(2, group.members().size());

        // Add channel
        gateway.addChannel(group.id(), "tai-lieu");
        Conversation updated = gateway.listConversations().stream()
                .filter(c -> c.id().equals(group.id())).findFirst().orElseThrow();
        assertTrue(updated.channels().contains("tai-lieu"));

        // Remove channel
        gateway.removeChannel(group.id(), "tai-lieu");
        Conversation afterRemoval = gateway.listConversations().stream()
                .filter(c -> c.id().equals(group.id())).findFirst().orElseThrow();
        assertFalse(afterRemoval.channels().contains("tai-lieu"));
    }
}

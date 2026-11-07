package vn.edu.ltm.chat.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CallStateTest {

    @Test
    void matchesConversationAndChannel() {
        CallState s = new CallState();
        assertFalse(s.matches("g1", "voice"));
        s.begin("g1", "voice", "text");
        assertTrue(s.matches("g1", "voice"));
        assertTrue(s.matches("g1", ""));
        assertFalse(s.matches("g1", "other"));
        assertFalse(s.matches("g2", "voice"));
        s.end();
        assertFalse(s.isActive());
    }

    @Test
    void rosterTracksJoinAndLeave() {
        CallState s = new CallState();
        assertTrue(s.applyRosterSignal("g1", "v", "u1", "An", "INVITE"));
        assertEquals("An", s.roster("g1", "v").get("u1"));
        assertFalse(s.applyRosterSignal("g1", "v", "u1", "An", "MUTE"));
        assertTrue(s.applyRosterSignal("g1", "v", "u1", "An", "LEAVE"));
        assertFalse(s.applyRosterSignal("g1", "v", "u1", "An", "LEAVE"));
    }
}

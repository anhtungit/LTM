package vn.edu.ltm.chat.presentation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WatchTogetherTest {

    @Test
    void parsesVideoIds() {
        assertEquals("dQw4w9WgXcQ", WatchTogether.parseVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=5"));
        assertEquals("dQw4w9WgXcQ", WatchTogether.parseVideoId("https://youtu.be/dQw4w9WgXcQ"));
        assertEquals("dQw4w9WgXcQ", WatchTogether.parseVideoId("dQw4w9WgXcQ"));
        assertNull(WatchTogether.parseVideoId("https://example.com"));
    }

    @Test
    void remoteStateRespectsHostPermission() {
        WatchTogether w = new WatchTogether("me", (a, d) -> { });
        assertTrue(w.onRemoteState("host", "q=aaaaaaaaaaa;i=0;p=1;t=3;e=0;h=host"));
        assertTrue(w.isActive());
        assertFalse(w.canControl());
        assertFalse(w.onRemoteState("other", "q=bbbbbbbbbbb;i=0;p=0;t=0;e=1;h=other"));
        assertTrue(w.onRemoteState("host", "q=aaaaaaaaaaa;i=0;p=0;t=9;e=1;h=host"));
        assertTrue(w.canControl());
    }
}

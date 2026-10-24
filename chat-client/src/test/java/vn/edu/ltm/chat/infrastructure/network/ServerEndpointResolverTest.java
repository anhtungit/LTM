package vn.edu.ltm.chat.infrastructure.network;

import org.junit.jupiter.api.Test;
import vn.edu.ltm.chat.infrastructure.network.ServerEndpointResolver.Endpoint;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ServerEndpointResolverTest {
    @Test
    void extractsGistIdFromVariousGistUrls() {
        String id = "55f47d25e0fe918c5d0fcb3df77f2ab2";
        assertEquals(id, ServerEndpointResolver.extractGistId(
                "https://gist.github.com/dunggawr/55f47d25e0fe918c5d0fcb3df77f2ab2"));
        assertEquals(id, ServerEndpointResolver.extractGistId(
                "https://gist.githubusercontent.com/dunggawr/55f47d25e0fe918c5d0fcb3df77f2ab2/raw/ltm-chat-endpoint.txt"));
        assertEquals(id, ServerEndpointResolver.extractGistId(
                "https://gist.githubusercontent.com/dunggawr/55f47d25e0fe918c5d0fcb3df77f2ab2/raw/0123456789abcdef0123456789abcdef01234567/ltm-chat-endpoint.txt"));
    }

    @Test
    void parsesNgrokAndLanEndpointsFromGistContent() {
        String sample = """
                # Địa chỉ máy chủ LTM Chat
                tcp://0.tcp.ap.ngrok.io:14567
                25.1.51.40:8888
                127.0.0.1:8888
                """;
        List<Endpoint> list = new ArrayList<>();
        ServerEndpointResolver.parseLinesIntoEndpoints(sample, list);

        assertEquals(3, list.size());
        assertEquals("0.tcp.ap.ngrok.io", list.get(0).host());
        assertEquals(14567, list.get(0).port());
        assertEquals("25.1.51.40", list.get(1).host());
        assertEquals(8888, list.get(1).port());
    }

    @Test
    void parsesSingleEndpointCleanly() {
        Endpoint ep = ServerEndpointResolver.parseEndpoint("0.tcp.ap.ngrok.io:19283", 8888, "gist");
        assertNotNull(ep);
        assertEquals("0.tcp.ap.ngrok.io", ep.host());
        assertEquals(19283, ep.port());
    }
}


package vn.edu.ltm.chat.infrastructure.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zero-Config Server Endpoint Resolver (Discord-style silent auto-connection).
 * Automatically locates and connects to the chat server without requiring user input:
 * <ol>
 *   <li>System property / Environment variable override (if present)</li>
 *   <li>Localhost fast-path ({@code 127.0.0.1:8888})</li>
 *   <li>GitHub Gist / Cloud Registry URL ({@code server.registry.url}) returning {@code host:port}</li>
 *   <li>Configured Cloud / Static Tunnel endpoint ({@code server.host:server.port})</li>
 *   <li>LAN / Wi-Fi UDP Broadcast Auto-Discovery (port {@code 8889})</li>
 * </ol>
 */
public final class ServerEndpointResolver {
    public static final String DISCOVERY_MAGIC_REQ = "LTM_CHAT_DISCOVER_REQ";
    public static final String DISCOVERY_MAGIC_RES_PREFIX = "LTM_CHAT_DISCOVER_RES:";
    public static final int DEFAULT_TCP_PORT = 8888;
    public static final int DEFAULT_UDP_PORT = 8889;

    private static final Pattern GIST_ID_PATTERN = Pattern.compile("([a-fA-F0-9]{20,40})");

    public record Endpoint(String host, int port, String source) {
        @Override
        public String toString() {
            return host + ":" + port;
        }
    }

    private ServerEndpointResolver() {
    }

    /**
     * Resolves and opens a connected TCP {@link Socket} to the first reachable server endpoint.
     */
    public static Socket connectAuto(String explicitHost, int explicitPort) throws IOException {
        if (explicitHost != null && !explicitHost.isBlank() && explicitPort > 0) {
            Socket s = new Socket();
            s.connect(new InetSocketAddress(explicitHost.trim(), explicitPort), 1500);
            return s;
        }

        // 1. Check JVM System Properties & Environment Variables
        String sysHost = System.getProperty("chat.server.host", System.getenv("CHAT_SERVER_HOST"));
        String sysPort = System.getProperty("chat.server.port", System.getenv("CHAT_SERVER_PORT"));
        if (sysHost != null && !sysHost.isBlank()) {
            Endpoint ep = parseEndpoint(sysHost, parsePort(sysPort, DEFAULT_TCP_PORT), "env");
            if (ep != null) {
                Socket s = tryOpenSocket(ep, 1500);
                if (s != null) return s;
            }
        }

        Properties props = loadProperties();
        int propPort = parsePort(props.getProperty("server.port"), DEFAULT_TCP_PORT);

        // 2. Fast-path check for Localhost (takes <5ms when server is on same machine)
        Socket localSocket = tryOpenSocket(new Endpoint("127.0.0.1", propPort, "localhost"), 150);
        if (localSocket != null) {
            return localSocket;
        }

        // 3. LAN / Wi-Fi UDP Broadcast Auto-Discovery
        int udpPort = parsePort(props.getProperty("server.discovery.udp.port"), DEFAULT_UDP_PORT);
        Endpoint discovered = discoverViaUdp(udpPort, 600);
        if (discovered != null) {
            Socket s = tryOpenSocket(discovered, 1200);
            if (s != null) return s;
        }

        // 4. Configured static host in server.properties
        String propHost = props.getProperty("server.host", "").trim();
        if (!propHost.isEmpty() && !"127.0.0.1".equals(propHost) && !"localhost".equalsIgnoreCase(propHost)) {
            Endpoint ep = parseEndpoint(propHost, propPort, "cloud");
            if (ep != null) {
                Socket s = tryOpenSocket(ep, 1500);
                if (s != null) return s;
            }
        }

        // 5. Optional Registry URL (only if explicitly configured)
        String regUrl = System.getProperty("chat.server.registry.url",
                System.getenv().getOrDefault("CHAT_SERVER_REGISTRY_URL",
                        props.getProperty("server.registry.url", "")));
        if (regUrl != null && !regUrl.isBlank()) {
            List<Endpoint> registryEndpoints = fetchEndpointsFromRegistryUrl(regUrl.trim());
            for (Endpoint ep : registryEndpoints) {
                if ("127.0.0.1".equals(ep.host()) || "localhost".equalsIgnoreCase(ep.host())) {
                    continue;
                }
                Socket s = tryOpenSocket(ep, 1800);
                if (s != null) return s;
            }
        }

        throw new IOException("Không tìm thấy máy chủ chat khả dụng.");
    }

    private static Socket tryOpenSocket(Endpoint endpoint, int timeoutMs) {
        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), timeoutMs);
            return s;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Fetches endpoint list from a GitHub Gist URL or any Raw HTTP URL.
     * Uses GitHub REST API first for Gist URLs to avoid CDN caching delays.
     */
    public static List<Endpoint> fetchEndpointsFromRegistryUrl(String urlStr) {
        String gistId = extractGistId(urlStr);
        if (gistId != null) {
            List<Endpoint> fromApi = fetchFromGitHubGistApi(gistId);
            if (!fromApi.isEmpty()) {
                return fromApi;
            }
        }
        return fetchFromRawHttpUrl(urlStr);
    }

    static String extractGistId(String urlOrId) {
        if (urlOrId == null || urlOrId.isBlank()) return null;
        String trimmed = urlOrId.trim();
        if (trimmed.matches("^[a-fA-F0-9]{20,40}$")) {
            return trimmed;
        }
        if (trimmed.contains("gist.github.com") || trimmed.contains("gist.githubusercontent.com")
                || trimmed.contains("api.github.com/gists/")) {
            Matcher m = GIST_ID_PATTERN.matcher(trimmed);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    private static List<Endpoint> fetchFromGitHubGistApi(String gistId) {
        List<Endpoint> endpoints = new ArrayList<>();
        try {
            URI uri = URI.create("https://api.github.com/gists/" + gistId);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(1500);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("Cache-Control", "no-cache");
            conn.setUseCaches(false);

            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    JsonObject files = root.getAsJsonObject("files");
                    if (files != null) {
                        for (Map.Entry<String, JsonElement> entry : files.entrySet()) {
                            JsonObject fileObj = entry.getValue().getAsJsonObject();
                            if (fileObj.has("content") && !fileObj.get("content").isJsonNull()) {
                                parseLinesIntoEndpoints(fileObj.get("content").getAsString(), endpoints);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return endpoints;
    }

    private static List<Endpoint> fetchFromRawHttpUrl(String urlStr) {
        List<Endpoint> endpoints = new ArrayList<>();
        try {
            String cleanUrl = urlStr.trim();
            // Strip commit SHA from gist.githubusercontent.com/<user>/<id>/raw/<sha>/<file> so it always fetches latest
            cleanUrl = cleanUrl.replaceAll("/raw/[a-fA-F0-9]{40}/", "/raw/");
            String sep = cleanUrl.contains("?") ? "&" : "?";
            URI uri = URI.create(cleanUrl + sep + "_t=" + System.currentTimeMillis());

            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(1500);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
            conn.setRequestProperty("Pragma", "no-cache");
            conn.setUseCaches(false);

            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                    parseLinesIntoEndpoints(sb.toString(), endpoints);
                }
            }
        } catch (Exception ignored) {
        }
        return endpoints;
    }

    static void parseLinesIntoEndpoints(String content, List<Endpoint> target) {
        if (content == null) return;
        for (String rawLine : content.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                Endpoint ep = parseEndpoint(line, DEFAULT_TCP_PORT, "gist-registry");
                if (ep != null && !ep.host().isEmpty()) {
                    target.add(ep);
                }
            }
        }
    }

    /**
     * Broadcasts a UDP discovery packet on LAN/Wi-Fi and waits for the server to reply with its IP & TCP port.
     */
    public static Endpoint discoverViaUdp(int udpPort, int timeoutMs) {
        if (udpPort <= 0) return null;
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(timeoutMs);

            byte[] reqBytes = DISCOVERY_MAGIC_REQ.getBytes(StandardCharsets.UTF_8);
            Set<InetAddress> targets = collectBroadcastAddresses();

            for (InetAddress target : targets) {
                try {
                    DatagramPacket packet = new DatagramPacket(reqBytes, reqBytes.length, target, udpPort);
                    socket.send(packet);
                } catch (IOException ignored) {
                }
            }

            byte[] buf = new byte[256];
            DatagramPacket response = new DatagramPacket(buf, buf.length);
            socket.receive(response);
            String reply = new String(response.getData(), response.getOffset(), response.getLength(), StandardCharsets.UTF_8).trim();
            if (reply.startsWith(DISCOVERY_MAGIC_RES_PREFIX)) {
                int tcpPort = parsePort(reply.substring(DISCOVERY_MAGIC_RES_PREFIX.length()).trim(), DEFAULT_TCP_PORT);
                String serverIp = response.getAddress().getHostAddress();
                return new Endpoint(serverIp, tcpPort, "lan-udp");
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static Set<InetAddress> collectBroadcastAddresses() {
        Set<InetAddress> list = new LinkedHashSet<>();
        try {
            list.add(InetAddress.getByName("255.255.255.255"));
            list.add(InetAddress.getByName("127.0.0.1"));
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) continue;
                for (InterfaceAddress ia : iface.getInterfaceAddresses()) {
                    InetAddress broadcast = ia.getBroadcast();
                    if (broadcast != null) {
                        list.add(broadcast);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    static Endpoint parseEndpoint(String rawHostOrHostPort, int defaultPort, String source) {
        String cleaned = rawHostOrHostPort.trim()
                .replaceFirst("^tcp://", "")
                .replaceFirst("^http://", "")
                .replaceFirst("^https://", "");
        int slashIdx = cleaned.indexOf('/');
        if (slashIdx > 0) {
            cleaned = cleaned.substring(0, slashIdx);
        }
        int colonIdx = cleaned.lastIndexOf(':');
        if (colonIdx > 0 && colonIdx < cleaned.length() - 1) {
            String h = cleaned.substring(0, colonIdx).trim();
            int p = parsePort(cleaned.substring(colonIdx + 1).trim(), defaultPort);
            return new Endpoint(h, p, source);
        }
        return new Endpoint(cleaned, defaultPort, source);
    }

    private static int parsePort(String str, int fallback) {
        if (str == null || str.isBlank()) return fallback;
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = ServerEndpointResolver.class.getResourceAsStream("/server.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
        }

        // Allow optional external override file next to the JAR or in ~/.ltm-chat/server.properties
        File[] externalFiles = new File[]{
                new File("server.properties"),
                new File(System.getProperty("user.home", "."), ".ltm-chat/server.properties")
        };
        for (File f : externalFiles) {
            if (f.exists() && f.isFile()) {
                try (InputStream fis = new FileInputStream(f)) {
                    props.load(fis);
                } catch (IOException ignored) {
                }
            }
        }
        return props;
    }
}

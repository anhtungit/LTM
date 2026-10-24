package vn.edu.ltm.chat.server.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import vn.edu.ltm.chat.server.protocol.Packet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Automatically detects active Ngrok TCP tunnels (via local API http://127.0.0.1:4040/api/tunnels)
 * and LAN IP, and publishes the latest {@code host:port} list to the configured GitHub Gist.
 */
public final class GistRegistryPublisher {
    private final String gistId;
    private final String gistFilename;
    private final String gistToken;
    private final String ngrokApiUrl;
    private String lastPublishedPayload = "";

    public GistRegistryPublisher() {
        Properties props = loadProperties();
        this.gistId = System.getProperty("chat.gist.id",
                System.getenv().getOrDefault("CHAT_GIST_ID", props.getProperty("gist.id", "").trim()));
        this.gistFilename = props.getProperty("gist.filename", "ltm-chat-endpoint.txt").trim();
        this.gistToken = System.getProperty("chat.gist.token",
                System.getenv().getOrDefault("GITHUB_TOKEN", props.getProperty("gist.token", "").trim()));
        this.ngrokApiUrl = props.getProperty("ngrok.api.url", "http://127.0.0.1:4040/api/tunnels").trim();
    }

    public boolean isConfigured() {
        return gistId != null && !gistId.isBlank();
    }

    public String getGistUrl() {
        return "https://gist.github.com/" + gistId;
    }

    /**
     * Checks current public tunnel (ngrok or explicit) + LAN IP and updates the Gist if changed.
     */
    public void checkAndPublish(String lanIp, int tcpPort) {
        if (!isConfigured()) return;

        String publicTunnel = System.getProperty("chat.server.public", System.getenv("CHAT_SERVER_PUBLIC"));
        if (publicTunnel == null || publicTunnel.isBlank()) {
            publicTunnel = detectNgrokTcpEndpoint();
        } else {
            publicTunnel = publicTunnel.trim().replaceFirst("^tcp://", "");
        }

        StringBuilder content = new StringBuilder();
        content.append("# Địa chỉ máy chủ LTM Chat (tự động cập nhật từ chat-server)\n");
        if (publicTunnel != null && !publicTunnel.isBlank()) {
            content.append(publicTunnel).append("\n");
        }
        if (lanIp != null && !lanIp.isBlank() && !"127.0.0.1".equals(lanIp)) {
            content.append(lanIp).append(":").append(tcpPort).append("\n");
        }
        content.append("127.0.0.1:").append(tcpPort).append("\n");

        String newPayload = content.toString();
        if (newPayload.equals(lastPublishedPayload)) {
            return;
        }

        boolean updated = publishToGist(newPayload);
        if (updated) {
            lastPublishedPayload = newPayload;
            String primary = (publicTunnel != null && !publicTunnel.isBlank())
                    ? publicTunnel + " (Internet Ngrok)"
                    : lanIp + ":" + tcpPort + " (LAN)";
            System.out.println(" [Gist Registry]   Đã đồng bộ địa chỉ lên GitHub Gist -> " + primary);
        }
    }

    /**
     * Queries Ngrok's local agent API (http://127.0.0.1:4040/api/tunnels) to automatically
     * discover any active TCP tunnel (e.g. "0.tcp.ap.ngrok.io:14567").
     */
    public String detectNgrokTcpEndpoint() {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(ngrokApiUrl).toURL().openConnection();
            conn.setConnectTimeout(600);
            conn.setReadTimeout(600);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    JsonArray tunnels = root.getAsJsonArray("tunnels");
                    if (tunnels != null) {
                        for (JsonElement el : tunnels) {
                            JsonObject t = el.getAsJsonObject();
                            if (t.has("public_url") && !t.get("public_url").isJsonNull()) {
                                String pubUrl = t.get("public_url").getAsString().trim();
                                if (pubUrl.startsWith("tcp://")) {
                                    return pubUrl.substring("tcp://".length());
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean publishToGist(String content) {
        // 1. Try REST API if GITHUB_TOKEN is provided
        if (gistToken != null && !gistToken.isBlank()) {
            if (publishViaRestApi(content)) {
                return true;
            }
        }
        // 2. Fallback to GitHub CLI (`gh gist edit`) if installed and authenticated on machine
        return publishViaGhCli(content);
    }

    private boolean publishViaRestApi(String content) {
        try {
            URI uri = URI.create("https://api.github.com/gists/" + gistId);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(2500);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("X-HTTP-Method-Override", "PATCH");
            conn.setRequestProperty("Authorization", "Bearer " + gistToken);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);

            JsonObject fileContent = new JsonObject();
            fileContent.addProperty("content", content);
            JsonObject files = new JsonObject();
            files.add(gistFilename, fileContent);
            JsonObject body = new JsonObject();
            body.add("files", files);

            byte[] bytes = Packet.GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }
            int code = conn.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean publishViaGhCli(String content) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("ltm-gist-", ".txt");
            Files.writeString(tempFile, content, StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(
                    "gh", "gist", "edit", gistId, "-f", gistFilename, tempFile.toAbsolutePath().toString()
            ).redirectErrorStream(true).start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = GistRegistryPublisher.class.getResourceAsStream("/server.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
        }
        File ext = new File("server.properties");
        if (ext.exists() && ext.isFile()) {
            try (InputStream fis = new FileInputStream(ext)) {
                props.load(fis);
            } catch (IOException ignored) {
            }
        }
        return props;
    }
}


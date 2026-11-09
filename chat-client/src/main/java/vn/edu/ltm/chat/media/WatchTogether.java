package vn.edu.ltm.chat.media;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Xem YouTube cùng nhau": trạng thái video/playlist dùng chung của một cuộc gọi.
 * Hỗ trợ tìm kiếm trực tiếp YouTube, danh sách Trending đề xuất, Shared Playlist và chế độ phóng rộng (Expand Video)
 * tương tự Discord Watch Together.
 */
public final class WatchTogether {

    public static final String ACTION_STATE = "YT_STATE";
    public static final String ACTION_STOP = "YT_STOP";
    public static final String ACTION_REQ = "YT_REQ";

    private static final Pattern YT_ID = Pattern.compile(
            "(?:youtu\\.be/|[?&]v=|/embed/|/shorts/|/live/)([A-Za-z0-9_-]{11})|^([A-Za-z0-9_-]{11})$");

    private static final Pattern YT_SEARCH_ITEM = Pattern.compile(
            "\"videoRenderer\":\\{\"videoId\":\"([A-Za-z0-9_-]{11})\".*?\"title\":\\{\"runs\":\\[\\{\"text\":\"((?:\\\\.|[^\"\\\\])+)\"\\}\\].*?\"lengthText\":\\{.*?\"simpleText\":\"([0-9:]+)\"");

    private static final Pattern OEMBED_TITLE = Pattern.compile("\"title\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])+)\"");

    private final String selfId;
    private final BiConsumer<String, String> sendSignal;
    private final String token = HexFormat.of().formatHex(randomBytes());
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private HttpServer server;

    // Trạng thái dùng chung (khóa bằng this)
    private final List<String> queue = new ArrayList<>();
    private final Map<String, String> titles = new ConcurrentHashMap<>();
    private int index = -1;
    private boolean playing;
    private double pos;
    private long stampMs = System.currentTimeMillis();
    private boolean everyoneControls = true;
    private String hostId;
    private long seq = 1;
    private volatile boolean stopped = false;

    private record VideoCard(String id, String title, String duration) {}

    private static final List<VideoCard> DEFAULT_TRENDING = List.of(
            new VideoCard("kJQP7kiw5Fk", "Luis Fonsi - Despacito ft. Daddy Yankee", "4:42"),
            new VideoCard("bM7SZ5SBzyY", "Alan Walker - Fade [NCS Release]", "4:20"),
            new VideoCard("3AtDnEC4zak", "Charlie Puth - We Don't Talk Anymore (feat. Selena Gomez)", "3:50"),
            new VideoCard("fJ9rUzIMcZQ", "Queen – Bohemian Rhapsody (Official Video Remastered)", "5:59"),
            new VideoCard("dQw4w9WgXcQ", "Rick Astley - Never Gonna Give You Up (Official Music Video)", "3:32"),
            new VideoCard("lTRiuFIWV54", "1 A.M Study Session - [lofi hip hop/chill beats]", "1:01:14"),
            new VideoCard("n61ULEU7CO0", "Best of Lofi Hip Hop - Chill Beats Mix", "2:12:30"),
            new VideoCard("DWcJFNfaw9c", "Ambient Study Music To Concentrate - 4 Hours", "3:58:12")
    );

    private static final String[] TRENDING_TOPICS = {
            "trending vietnam",
            "nhac tre remix hot tiktok",
            "lofi hip hop chill beats study",
            "top hits nhac viet moi nhat",
            "edm ncs gaming music",
            "acoustic viet nam cover",
            "nhac chill ballad hay nhat"
    };

    public WatchTogether(String selfId, BiConsumer<String, String> sendSignal) {
        this.selfId = selfId;
        this.sendSignal = sendSignal;
        for (VideoCard c : DEFAULT_TRENDING) {
            titles.put(c.id(), c.title());
        }
    }

    private static byte[] randomBytes() {
        byte[] b = new byte[12];
        new SecureRandom().nextBytes(b);
        return b;
    }

    public static String parseVideoId(String text) {
        if (text == null) return null;
        Matcher m = YT_ID.matcher(text.trim());
        if (!m.find()) return null;
        return m.group(1) != null ? m.group(1) : m.group(2);
    }

    public synchronized String ensureServerRunning() {
        this.stopped = false;
        if (server == null) {
            try {
                server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
                server.createContext("/" + token + "/", this::handle);
                server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
                server.start();
            } catch (IOException e) {
                System.err.println("[WatchTogether] Không khởi tạo được server: " + e.getMessage());
            }
        }
        return getLocalUrl();
    }

    public synchronized String getLocalUrl() {
        if (server == null) return null;
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/" + token + "/";
    }

    /** Khởi tạo phiên xem chung và thông báo ngay cho các thành viên khác trong cuộc gọi. */
    public synchronized void startSession() {
        if (hostId == null) {
            hostId = selfId;
            if (queue.isEmpty() && !DEFAULT_TRENDING.isEmpty()) {
                VideoCard first = DEFAULT_TRENDING.get(0);
                queue.add(first.id());
                titles.put(first.id(), first.title());
                index = 0;
                pos = 0;
                playing = true;
            }
            stampMs = System.currentTimeMillis();
            seq++;
            broadcastLocked();
        }
    }

    /** Tạo component trình phát YouTube trực tiếp trong ứng dụng (sử dụng JavaFX WebView). */
    public synchronized javax.swing.JComponent createInAppPlayerComponent() {
        String url = ensureServerRunning();
        if (url == null) return null;
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("linux")) {
                if (System.getProperty("prism.order") == null) {
                    System.setProperty("prism.order", "es2,sw");
                }
            }
            javafx.application.Platform.setImplicitExit(false);
            javafx.embed.swing.JFXPanel fxPanel = new javafx.embed.swing.JFXPanel();
            fxPanel.setPreferredSize(new java.awt.Dimension(800, 500));
            fxPanel.setOpaque(true);
            fxPanel.setBackground(new java.awt.Color(0x11, 0x12, 0x14));

            javafx.application.Platform.runLater(() -> {
                try {
                    javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
                    webView.getEngine().setUserAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 LTMApp/1.0");
                    String appUrl = url + (url.contains("?") ? "&env=app" : "?env=app");
                    webView.getEngine().load(appUrl);
                    javafx.scene.Scene scene = new javafx.scene.Scene(webView);
                    fxPanel.setScene(scene);
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        fxPanel.revalidate();
                        fxPanel.repaint();
                        if (fxPanel.getParent() != null) {
                            fxPanel.getParent().revalidate();
                            fxPanel.getParent().repaint();
                        }
                    });
                } catch (Throwable t) {
                    System.err.println("[WatchTogether] Lỗi nạp WebView JavaFX: " + t.getMessage());
                }
            });
            return fxPanel;
        } catch (Throwable t) {
            System.err.println("[WatchTogether] JFXPanel không khả dụng trên môi trường này: " + t.getMessage());
            return null;
        }
    }

    /** Mở trang xem chung trong trình duyệt (khởi động server cục bộ nếu cần). */
    public synchronized void openBrowser() {
        String url = ensureServerRunning();
        if (url == null) return;
        openExternalUrl(url);
    }

    /** Mở một đường dẫn URL bất kỳ bằng trình duyệt mặc định của hệ thống. */
    public synchronized void openExternalUrl(String url) {
        if (url == null || url.isBlank()) return;
        try {
            URI uri = URI.create(url);
            try {
                Desktop.getDesktop().browse(uri);
            } catch (Exception e) {
                String os = System.getProperty("os.name", "").toLowerCase();
                if (os.contains("win")) {
                    new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
                } else if (os.contains("mac")) {
                    new ProcessBuilder("open", url).start();
                } else {
                    new ProcessBuilder("xdg-open", uri.toString()).start();
                }
            }
        } catch (Exception e) {
            System.err.println("[WatchTogether] Không mở được trình duyệt: " + e.getMessage());
        }
    }

    public synchronized void stop() {
        this.stopped = true;
        this.playing = false;
        this.queue.clear();
        this.index = -1;
        this.hostId = null;
        this.seq++;
        final HttpServer s = this.server;
        this.server = null;
        if (s != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(800);
                } catch (InterruptedException ignored) {
                }
                try {
                    s.stop(0);
                } catch (Throwable ignored) {
                }
            });
        }
    }

    public synchronized boolean isActive() {
        return hostId != null || !queue.isEmpty();
    }

    /** Người mới vào cuộc gọi xin trạng thái hiện tại. */
    public void requestState() {
        sendSignal.accept(ACTION_REQ, "");
    }

    /** Có người xin trạng thái: nếu đang xem thì phát lại cho họ. */
    public void onRequest() {
        String data;
        synchronized (this) {
            if (!isActive()) return;
            data = encode();
        }
        sendSignal.accept(ACTION_STATE, data);
    }

    public synchronized boolean canControl() {
        if (everyoneControls) return true;
        return hostId != null && selfId.equals(hostId);
    }

    /** Áp dụng trạng thái do người khác gửi; bỏ qua nếu họ không có quyền điều khiển. */
    public synchronized boolean onRemoteState(String senderId, String data) {
        Map<String, String> m = decode(data);
        String sId = senderId != null ? senderId.trim() : "";
        boolean allowed = hostId == null || everyoneControls || sId.equals(hostId);
        if (!allowed) return false;
        queue.clear();
        String q = m.getOrDefault("q", "");
        if (!q.isEmpty()) {
            for (String id : q.split(",")) {
                if (!id.isBlank()) {
                    queue.add(id);
                    ensureTitleAsync(id);
                }
            }
        }
        index = parseInt(m.get("i"), -1);
        playing = "1".equals(m.get("p"));
        pos = parseDouble(m.get("t"));
        stampMs = System.currentTimeMillis();
        everyoneControls = !"0".equals(m.get("e"));
        String h = m.get("h");
        hostId = (h != null && !h.isBlank()) ? h.trim() : null;
        seq++;
        return true;
    }

    // ---- Trình duyệt / WebView -> Java ----

    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath().substring(("/" + token + "/").length());
        Map<String, String> q = query(ex.getRequestURI().getRawQuery());
        String type = "application/json; charset=utf-8";
        String out = null;
        switch (path) {
            case "" -> {
                type = "text/html; charset=utf-8";
                out = PAGE;
            }
            case "state" -> out = stateJson();
            case "search" -> out = searchJson(q.get("q"), parseInt(q.get("page"), 1));
            case "browser" -> {
                openBrowser();
                out = "{\"status\":\"ok\"}";
            }
            case "openUrl" -> {
                openExternalUrl(q.get("url"));
                out = "{\"status\":\"ok\"}";
            }
            case "event" -> {
                localEvent("1".equals(q.get("p")), parseDouble(q.get("t")), parseInt(q.get("i"), -2));
                out = stateJson();
            }
            case "ended" -> ended(parseInt(q.get("i"), -2));
            case "add" -> {
                add(q.get("url"), q.get("title"), "1".equals(q.get("playNow")));
                out = stateJson();
            }
            case "remove" -> {
                remove(parseInt(q.get("i"), -1));
                out = stateJson();
            }
            case "play" -> {
                playIndex(parseInt(q.get("i"), -1));
                out = stateJson();
            }
            case "everyone" -> {
                toggleEveryone();
                out = stateJson();
            }
            case "stop" -> stopForAll();
            default -> {
                ex.sendResponseHeaders(404, -1);
                ex.close();
                return;
            }
        }
        byte[] bytes = (out != null ? out : "{}").getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private void broadcastLocked() {
        sendSignal.accept(ACTION_STATE, encode());
    }

    private synchronized void mutate(Runnable change) {
        if (!canControl()) {
            seq++;
            return;
        }
        if (hostId == null) hostId = selfId;
        change.run();
        stampMs = System.currentTimeMillis();
        seq++;
        broadcastLocked();
    }

    private void localEvent(boolean p, double t, int i) {
        mutate(() -> {
            if (i >= 0 && i != index) return;
            playing = p;
            pos = t;
        });
    }

    private void ended(int i) {
        mutate(() -> {
            if (i != index) return;
            if (index + 1 < queue.size()) {
                index++;
                pos = 0;
                playing = true;
            } else {
                playing = false;
            }
        });
    }

    private void add(String url, String title, boolean playNow) {
        String id = parseVideoId(url);
        if (id == null) return;
        if (title != null && !title.isBlank()) {
            titles.put(id, title.trim());
        } else {
            ensureTitleAsync(id);
        }
        mutate(() -> {
            int existing = queue.indexOf(id);
            if (existing < 0) {
                queue.add(id);
                existing = queue.size() - 1;
            }
            if (index < 0) {
                index = existing;
                pos = 0;
                playing = true;
            } else if (playNow && existing != index) {
                index = existing;
                pos = 0;
                playing = true;
            }
        });
    }

    private void ensureTitleAsync(String id) {
        if (id == null || titles.containsKey(id)) return;
        CompletableFuture.runAsync(() -> {
            try {
                String u = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=" + id + "&format=json";
                HttpRequest req = HttpRequest.newBuilder(URI.create(u))
                        .timeout(Duration.ofSeconds(4))
                        .header("User-Agent", "Mozilla/5.0")
                        .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() == 200) {
                    Matcher m = OEMBED_TITLE.matcher(resp.body());
                    if (m.find()) {
                        String t = unescapeJson(m.group(1));
                        titles.put(id, t);
                        synchronized (WatchTogether.this) {
                            seq++;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    private String searchJson(String rawQuery, int page) {
        List<VideoCard> cards = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int p = Math.max(1, page);

        if (rawQuery == null || rawQuery.isBlank()) {
            if (p == 1) {
                cards.addAll(DEFAULT_TRENDING);
                for (VideoCard c : DEFAULT_TRENDING) seen.add(c.id());
            }
            String topic = TRENDING_TOPICS[(p - 1) % TRENDING_TOPICS.length];
            fetchYoutubeQuery(topic, cards, seen, 24);
            return toJsonArray(cards);
        }

        String base = rawQuery.trim();
        String query;
        switch ((p - 1) % 6) {
            case 0 -> query = base;
            case 1 -> query = base + " mv";
            case 2 -> query = base + " live";
            case 3 -> query = base + " remix";
            case 4 -> query = base + " acoustic";
            default -> query = base + " official";
        }
        fetchYoutubeQuery(query, cards, seen, 24);
        if (cards.isEmpty() && p == 1) {
            cards.addAll(DEFAULT_TRENDING);
        }
        return toJsonArray(cards);
    }

    private void fetchYoutubeQuery(String query, List<VideoCard> cards, Set<String> seen, int limit) {
        try {
            String url = "https://www.youtube.com/results?search_query="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8")
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200 && resp.body() != null) {
                Matcher m = YT_SEARCH_ITEM.matcher(resp.body());
                while (m.find() && cards.size() < limit) {
                    String vid = m.group(1);
                    if (seen.add(vid)) {
                        String title = unescapeJson(m.group(2));
                        String dur = m.group(3);
                        titles.put(vid, title);
                        cards.add(new VideoCard(vid, title, dur));
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String toJsonArray(List<VideoCard> cards) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < cards.size(); i++) {
            if (i > 0) sb.append(',');
            VideoCard c = cards.get(i);
            sb.append("{\"id\":\"").append(escapeJson(c.id()))
                    .append("\",\"title\":\"").append(escapeJson(c.title()))
                    .append("\",\"dur\":\"").append(escapeJson(c.duration()))
                    .append("\"}");
        }
        return sb.append("]").toString();
    }

    private void remove(int i) {
        mutate(() -> {
            if (i < 0 || i >= queue.size()) return;
            queue.remove(i);
            if (queue.isEmpty()) {
                index = -1;
                playing = false;
            } else if (i < index) {
                index--;
            } else if (i == index) {
                index = Math.min(index, queue.size() - 1);
                pos = 0;
            }
        });
    }

    private void playIndex(int i) {
        mutate(() -> {
            if (i < 0 || i >= queue.size()) return;
            index = i;
            pos = 0;
            playing = true;
        });
    }

    public synchronized boolean canToggleControl() {
        if (everyoneControls) return true;
        return hostId != null && selfId.equals(hostId);
    }

    private synchronized void toggleEveryone() {
        if (!canToggleControl()) return;
        everyoneControls = !everyoneControls;
        if (!everyoneControls) {
            hostId = selfId;
        }
        stampMs = System.currentTimeMillis();
        seq++;
        broadcastLocked();
    }

    private void stopForAll() {
        if (!canControl()) return;
        sendSignal.accept(ACTION_STOP, "");
        stop();
    }

    /** Có người dừng xem chung: đóng trạng thái cục bộ. */
    public void onRemoteStop() {
        stop();
    }

    // ---- Mã hóa trạng thái ----

    private double effectivePos() {
        return playing ? pos + (System.currentTimeMillis() - stampMs) / 1000.0 : pos;
    }

    private String encode() {
        return "q=" + String.join(",", queue) + ";i=" + index + ";p=" + (playing ? 1 : 0)
                + ";t=" + String.format(java.util.Locale.ROOT, "%.2f", effectivePos())
                + ";e=" + (everyoneControls ? 1 : 0) + ";h=" + (hostId == null ? "" : hostId);
    }

    private static Map<String, String> decode(String data) {
        Map<String, String> m = new HashMap<>();
        if (data == null) return m;
        for (String part : data.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0) m.put(part.substring(0, eq), part.substring(eq + 1));
        }
        return m;
    }

    private synchronized String stateJson() {
        boolean isHost = everyoneControls ? (hostId == null || selfId.equals(hostId)) : (hostId != null && selfId.equals(hostId));
        StringBuilder sb = new StringBuilder("{\"seq\":").append(seq)
                .append(",\"stopped\":").append(stopped)
                .append(",\"i\":").append(index)
                .append(",\"p\":").append(playing)
                .append(",\"t\":").append(String.format(java.util.Locale.ROOT, "%.2f", effectivePos()))
                .append(",\"e\":").append(everyoneControls)
                .append(",\"host\":").append(isHost)
                .append(",\"can\":").append(canControl())
                .append(",\"q\":[");
        for (int k = 0; k < queue.size(); k++) {
            if (k > 0) sb.append(',');
            sb.append('"').append(escapeJson(queue.get(k))).append('"');
        }
        sb.append("],\"titles\":{");
        for (int k = 0; k < queue.size(); k++) {
            if (k > 0) sb.append(',');
            String id = queue.get(k);
            String t = titles.getOrDefault(id, "Video " + id);
            sb.append('"').append(escapeJson(id)).append("\":\"").append(escapeJson(t)).append('"');
        }
        return sb.append("}}").toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }

    private static String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\u0026", "&");
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null) return m;
        for (String kv : raw.split("&")) {
            int eq = kv.indexOf('=');
            if (eq > 0) {
                m.put(kv.substring(0, eq), URLDecoder.decode(kv.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return m;
    }

    private static int parseInt(String s, int def) {
        try {
            return s == null ? def : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double parseDouble(String s) {
        try {
            return s == null ? 0 : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static final String PAGE = """
            <!doctype html><html><head><meta charset="utf-8"><title>Xem YouTube cùng nhau</title>
            <meta name="referrer" content="strict-origin-when-cross-origin">
            <style>
            *{box-sizing:border-box}
            body{margin:0;background:#111214;color:#f2f3f5;font:13px -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;display:flex;flex-direction:column;height:100vh;overflow:hidden;user-select:none}
            #topbar{display:flex;align-items:center;gap:12px;padding:10px 18px;background:#111214;border-bottom:1px solid #232428}
            .host-pill{display:inline-flex;align-items:center;gap:6px;background:#1e1f22;color:#23a55a;border:1px solid #2b2d31;border-radius:16px;padding:5px 12px;font-weight:600;font-size:12px;cursor:pointer;white-space:nowrap;transition:all .15s}
            .host-pill:hover{background:#2b2d31}
            .host-pill.locked{color:#f0b232;border-color:#5c4314;background:#261f12}
            .host-pill.locked:hover{background:#382d17}
            .search-wrap{flex:1;max-width:540px;margin:0 auto;display:flex;align-items:center;background:#1e1f22;border:1px solid #2b2d31;border-radius:6px;padding:0 10px}
            .search-wrap:focus-within{border-color:#5865f2}
            .search-wrap input{flex:1;background:transparent;border:0;color:#fff;padding:8px 6px;font-size:13px;outline:none}
            .search-wrap button{background:transparent;border:0;color:#b5bac1;cursor:pointer;font-size:14px;padding:4px 6px}
            .search-wrap button:hover{color:#fff}
            #stage{flex:1;display:flex;min-height:0;position:relative}
            #leftCol{width:48%;display:flex;flex-direction:column;border-right:1px solid #232428;background:#111214;min-width:320px}
            body.expanded #leftCol{width:100%;border-right:0}
            body.expanded #playlistSection,body.expanded #rightCol{display:none}
            #playerWrap{position:relative;width:100%;aspect-ratio:16/9;background:#000;flex-shrink:0}
            body.expanded #playerWrap{aspect-ratio:auto;flex:1;height:100%}
            #player{width:100%;height:100%}
            #expandBtn{position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);background:rgba(255,255,255,0.92);color:#111214;border:0;border-radius:20px;padding:8px 16px;font-weight:700;font-size:12px;cursor:pointer;opacity:0;transition:opacity .15s;box-shadow:0 4px 14px rgba(0,0,0,.5);z-index:5}
            #playerWrap:hover #expandBtn{opacity:1}
            body.expanded #expandBtn{display:none}
            #collapseBtn{display:none;position:absolute;left:14px;top:14px;background:#1e1f22;color:#f2f3f5;border:1px solid #3b3d44;border-radius:18px;padding:7px 15px;font-weight:700;font-size:12px;cursor:pointer;z-index:99;box-shadow:0 4px 14px rgba(0,0,0,.6);transition:background .15s}
            #collapseBtn:hover{background:#2b2d31;color:#fff}
            body.expanded #collapseBtn{display:inline-flex;align-items:center;gap:6px}
            #playlistSection{flex:1;display:flex;flex-direction:column;min-height:0;padding:12px 14px}
            .pl-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:8px}
            .pl-head h4{margin:0;font-size:13px;color:#f2f3f5}
            #list{flex:1;overflow-y:auto;display:flex;flex-direction:column;gap:6px}
            .it{display:flex;align-items:center;gap:10px;padding:6px 8px;border-radius:6px;background:#1e1f22;cursor:pointer;border:1px solid transparent}
            .it:hover{background:#2b2d31}
            .it.cur{border-color:#23a55a;background:#1a2921}
            .it img{width:76px;height:43px;object-fit:cover;border-radius:4px;flex-shrink:0}
            .it .meta{flex:1;min-width:0}
            .it .t{font-weight:600;font-size:12px;color:#fff;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
            .it .s{font-size:11px;color:#23a55a;margin-top:2px}
            .it .del{background:#da373c;color:#fff;border:0;border-radius:4px;padding:4px 8px;font-size:11px;font-weight:600;cursor:pointer}
            #rightCol{flex:1;display:flex;flex-direction:column;min-width:0;padding:14px 18px;overflow-y:auto}
            .sec-title{display:flex;align-items:center;gap:6px;font-weight:700;font-size:13px;color:#f2f3f5;margin-bottom:12px}
            #grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(195px,1fr));gap:14px}
            .card{display:flex;flex-direction:column;cursor:pointer;border-radius:8px;overflow:hidden;background:#1e1f22;border:1px solid #2b2d31;transition:transform .12s,border-color .12s}
            .card:hover{transform:translateY(-2px);border-color:#5865f2}
            .thumb{position:relative;width:100%;aspect-ratio:16/9;background:#18191c;overflow:hidden}
            .thumb img{width:100%;height:100%;object-fit:cover;display:block}
            .dur{position:absolute;right:6px;bottom:6px;background:rgba(0,0,0,.82);color:#fff;font-size:11px;font-weight:600;padding:2px 5px;border-radius:4px}
            .ov{position:absolute;inset:0;background:rgba(0,0,0,.55);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:6px;opacity:0;transition:opacity .15s}
            .card:hover .ov{opacity:1}
            .plus{width:38px;height:38px;border-radius:50%;background:#23a55a;color:#fff;display:flex;align-items:center;justify-content:center;font-size:20px;font-weight:700}
            .ov span{font-size:11px;font-weight:600;color:#fff}
            .ctitle{padding:8px 10px;font-size:12px;font-weight:600;color:#f2f3f5;line-height:1.35;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}
            </style></head><body>
            <div id="topbar">
              <button class="host-pill" id="everyone" title="Chuyển quyền điều khiển danh sách phát">● Đang chia sẻ ⌄</button>
              <div class="search-wrap">
                <input id="qInput" placeholder="Tìm kiếm video YouTube hoặc dán đường dẫn URL...">
                <button id="qBtn" title="Tìm kiếm">🔍</button>
              </div>
              <button id="stopBtn" class="host-pill" style="color:#f23f43;border-color:#5c1d24;background:#261214;cursor:pointer;margin-left:auto;" title="Dừng xem cùng nhau cho tất cả mọi người">⏹ Dừng xem chung</button>
            </div>
            <div id="stage">
              <button id="collapseBtn">⤡ Thu nhỏ / Hiện danh sách</button>
              <div id="leftCol">
                <div id="playerWrap">
                  <div id="player"></div>
                  <button id="expandBtn">⤢ Phóng to video</button>
                  <div id="controlShield" style="display:none;position:absolute;inset:0;z-index:12;cursor:not-allowed;"></div>
                  <div id="shieldToast" style="display:none;position:absolute;top:16px;left:50%;transform:translateX(-50%);background:rgba(20,20,24,0.92);color:#f0b232;border:1px solid #5c4314;padding:8px 18px;border-radius:20px;font-size:12px;font-weight:600;z-index:14;pointer-events:none;box-shadow:0 4px 16px rgba(0,0,0,0.5);">
                    🔒 Chỉ chủ phòng mới có quyền điều khiển phát/dừng video
                  </div>
                  <div id="playPrompt" style="display:none;position:absolute;inset:0;background:rgba(0,0,0,0.6);z-index:15;align-items:center;justify-content:center;cursor:pointer;">
                    <div style="background:#5865f2;color:#fff;padding:12px 24px;border-radius:24px;font-weight:700;font-size:13px;box-shadow:0 4px 16px rgba(0,0,0,0.5);display:flex;align-items:center;gap:8px;">
                      ▶ Bấm vào đây để phát video cùng phòng
                    </div>
                  </div>
                  <div id="emptyPlaceholder" style="display:none;position:absolute;inset:0;background:#0d0e10;flex-direction:column;align-items:center;justify-content:center;gap:12px;color:#949ba4;z-index:16;">
                    <div style="font-size:36px;color:#5865f2;">▶</div>
                    <div style="font-weight:700;font-size:14px;color:#f2f3f5;">Chưa có video nào trong hàng đợi</div>
                    <div style="font-size:12px;color:#949ba4;">Bấm vào một video ở danh sách bên phải để phát ngay!</div>
                  </div>
                  <div id="errOverlay" style="display:none;position:absolute;inset:0;background:rgba(17,18,20,0.94);flex-direction:column;align-items:center;justify-content:center;gap:10px;padding:24px;text-align:center;z-index:20;">
                    <div style="font-size:32px;">⚠️</div>
                    <div style="font-weight:700;font-size:14px;color:#f2f3f5;">Video bị giới hạn bản quyền hoặc không hỗ trợ nhúng</div>
                    <div id="errDesc" style="font-size:12px;color:#949ba4;max-width:380px;line-height:1.4;">Chủ sở hữu hoặc YouTube không cho phép phát video này trên trình phát nhúng. Bạn có thể mở xem cùng nhau trên Trình duyệt hoặc mở trực tiếp trên YouTube.</div>
                    <div style="display:flex;gap:8px;margin-top:6px;flex-wrap:wrap;justify-content:center;">
                      <button id="errBrowserBtn" style="background:#5865f2;color:#fff;border:0;border-radius:6px;padding:8px 16px;font-weight:600;font-size:12px;cursor:pointer;">🌐 Xem cùng nhau trên Trình duyệt</button>
                      <button id="errDirectBtn" style="background:#2b2d31;color:#f2f3f5;border:1px solid #3b3d44;border-radius:6px;padding:8px 16px;font-weight:600;font-size:12px;cursor:pointer;">🔗 Mở trên YouTube</button>
                      <button id="errBackBtn" style="background:#2b2d31;color:#f2f3f5;border:1px solid #3b3d44;border-radius:6px;padding:8px 16px;font-weight:600;font-size:12px;cursor:pointer;">⤡ Thu nhỏ</button>
                    </div>
                  </div>
                </div>
                <div id="playlistSection">
                  <div class="pl-head">
                    <h4>Danh sách phát chung (Shared Playlist)</h4>
                    <span id="msg" style="font-size:11px;color:#b5bac1"></span>
                  </div>
                  <div id="list"></div>
                </div>
              </div>
              <div id="rightCol">
                <div class="sec-title" id="gridTitle">🔥 Video Thịnh hành &amp; Đề xuất (Lướt xem không giới hạn)</div>
                <div id="grid"></div>
                <div id="loader" style="display:none;align-items:center;justify-content:center;padding:16px 0;color:#949ba4;font-size:12px;gap:8px;">
                  <span>⌛ Đang tải thêm video đề xuất...</span>
                </div>
              </div>
            </div>
            <script src="https://www.youtube.com/iframe_api"></script>
            <script>
            let player=null,ready=false,st=null,seq=0,cur=-1,applying=0,lastT=0,lastWall=Date.now(),expanded=false;
            let results=[],curQ='',curPage=1,loadingMore=false,hasMore=true,playCheckTimer=null;
            const $=id=>document.getElementById(id);
            const call=(p,q)=>fetch(p+(q?'?'+new URLSearchParams(q):'')).then(r=>r.json()).catch(()=>null);
            function onYouTubeIframeAPIReady(){
              player=new YT.Player('player',{
                width:'100%',height:'100%',
                playerVars:{autoplay:1,playsinline:1,rel:0,modestbranding:1,enablejsapi:1,origin:window.location.origin},
                events:{
                  onReady:()=>{
                    ready=true;
                    if(st){render();apply();}
                  },
                  onStateChange:onState,
                  onError:onPlayerError
                }
              });
            }
            function onPlayerError(e){
              $('errOverlay').style.display='flex';
            }
            function checkPlayHealth(){
              if(playCheckTimer)clearTimeout(playCheckTimer);
              playCheckTimer=setTimeout(()=>{
                if(ready&&player&&player.getPlayerState&&st&&st.p&&cur>=0){
                  const s=player.getPlayerState();
                  if(s===-1||s===2||s===5){
                    if($('playPrompt')) $('playPrompt').style.display='flex';
                  }
                }
              },3500);
            }
            function guard(){applying=Date.now()+2000;}
            function apply(){
              if(!ready||!st)return;
              if(st.i<0||st.i>=st.q.length){try{player.pauseVideo();}catch(e){}return;}
              if(cur!==st.i){
                cur=st.i;guard();
                $('errOverlay').style.display='none';
                if($('playPrompt')) $('playPrompt').style.display='none';
                lastT=st.t||0;lastWall=Date.now();
                player.loadVideoById(st.q[cur],st.t||0);
                if(st.p){
                  try{player.playVideo();}catch(e){}
                  checkPlayHealth();
                }else{
                  setTimeout(()=>{try{player.pauseVideo();}catch(e){}},400);
                }
                return;
              }
              if(Date.now()<applying)return;
              const t=player.getCurrentTime?player.getCurrentTime():0;
              if(Math.abs(t-st.t)>3.5){
                guard();
                lastT=st.t;lastWall=Date.now();
                player.seekTo(st.t,true);
              }
              const s=player.getPlayerState?player.getPlayerState():-1;
              if(st.p){
                if(s!==1&&s!==3){
                  guard();
                  try{player.playVideo();}catch(e){}
                  checkPlayHealth();
                }
              }else{
                if(s!==2){
                  guard();
                  try{player.pauseVideo();}catch(e){}
                }
              }
            }
            function onState(e){
              if(e.data===1||e.data===3){
                if(playCheckTimer)clearTimeout(playCheckTimer);
                $('errOverlay').style.display='none';
                if($('playPrompt')) $('playPrompt').style.display='none';
              }
              if(e.data===0){call('ended',{i:cur});return;}
              if(e.data===1||e.data===2){
                $('errOverlay').style.display='none';
                if(Date.now()<applying)return;
                if(st&&!st.can){
                  guard();
                  apply();
                  return;
                }
                const isPlaying=(e.data===1);
                const userToggled=st&&(isPlaying!==!!st.p);
                if(userToggled){
                  const t=player.getCurrentTime?player.getCurrentTime():0;
                  lastT=t;lastWall=Date.now();
                  if(st){st.p=isPlaying;st.t=t;}
                  guard();
                  call('event',{p:isPlaying?1:0,t:t,i:cur});
                }
              }
            }
            setInterval(()=>{
              if(!ready||!st||!player.getCurrentTime)return;
              const ps=player.getPlayerState?player.getPlayerState():-1;
              if(ps!==1&&ps!==2)return;
              const t=player.getCurrentTime(),now=Date.now();
              const expect=lastT+(ps===1?(now-lastWall)/1000:0);
              const diff=Math.abs(t-expect);
              if(Date.now()>=applying&&diff>3.0){
                if(st&&!st.can){guard();apply();return;}
                lastT=t;lastWall=now;
                if(st)st.t=t;
                guard();
                call('event',{p:ps===1?1:0,t:t,i:cur});
              }else if(Date.now()>=applying){
                lastT=t;lastWall=now;
              }
            },600);
            function render(){
              if(!st)return;
              const hasQ=st.q&&st.q.length>0;
              document.body.className=expanded?'expanded':'';
              $('emptyPlaceholder').style.display=hasQ?'none':'flex';
              const evBtn=$('everyone');
              if(st.e){
                evBtn.className='host-pill';
                evBtn.textContent='● Mọi người cùng điều khiển';
                evBtn.title='Bấm để chuyển sang: Chỉ tôi điều khiển';
                evBtn.style.cursor='pointer';
              }else{
                evBtn.className='host-pill locked';
                evBtn.textContent=st.host?'🔒 Chỉ bạn điều khiển':'🔒 Chỉ chủ phòng điều khiển';
                evBtn.title=st.host?'Bấm để mở lại quyền cho mọi người':'Chỉ chủ phòng mới có quyền thay đổi';
                evBtn.style.cursor=st.host?'pointer':'not-allowed';
              }
              $('msg').textContent=st.can?'':('Chỉ xem (Chủ phòng khóa điều khiển)');
              const sBtn=$('stopBtn');
              if(sBtn){
                sBtn.style.display=st.can?'inline-flex':'none';
              }
              const shield=$('controlShield');
              if(shield){
                shield.style.display=(!st.can&&hasQ)?'block':'none';
              }
              const l=$('list');l.innerHTML='';
              const titles=st.titles||{};
              (st.q||[]).forEach((id,i)=>{
                const d=document.createElement('div');
                d.className='it'+(i===st.i?' cur':'');
                const t=titles[id]||('Video '+id);
                d.innerHTML='<img src="https://i.ytimg.com/vi/'+id+'/mqdefault.jpg">'
                  +'<div class="meta"><div class="t"></div><div class="s">'+(i===st.i?(st.p?'▶ Đang phát':'⏸ Đã tạm dừng'):'Bấm để phát ngay')+'</div></div>';
                d.querySelector('.t').textContent=t;
                const x=document.createElement('button');
                x.className='del';x.textContent='Xóa';
                if(!st.can) x.style.display='none';
                x.onclick=ev=>{ev.stopPropagation();if(st&&st.can)call('remove',{i});};
                d.onclick=()=>{
                  if(!st||!st.can)return;
                  if(i===st.i){
                    const pNow=!st.p;
                    if(st)st.p=pNow;
                    call('event',{p:pNow?1:0,t:player.getCurrentTime?player.getCurrentTime():0,i:cur});
                    if(pNow){try{player.playVideo();}catch(e){}}else{try{player.pauseVideo();}catch(e){}}
                  }else{
                    call('play',{i});
                  }
                };
                d.appendChild(x);l.appendChild(d);
              });
              updateBadges();
            }
            function updateBadges(){
              const qSet=new Set(st&&st.q?st.q:[]);
              document.querySelectorAll('.card').forEach(c=>{
                const vid=c.dataset.vid;
                const inQ=qSet.has(vid);
                const p=c.querySelector('.plus');
                const txt=c.querySelector('.ov span');
                if(p)p.textContent=inQ?'✓':'+';
                if(txt)txt.textContent=inQ?'Đã thêm':'Thêm vào danh sách';
              });
            }
            function appendCards(items){
              const g=$('grid');
              const qSet=new Set(st&&st.q?st.q:[]);
              items.forEach(v=>{
                const inQ=qSet.has(v.id);
                const c=document.createElement('div');
                c.className='card';
                c.dataset.vid=v.id;
                c.innerHTML='<div class="thumb"><img src="https://i.ytimg.com/vi/'+v.id+'/hqdefault.jpg" onerror="this.onerror=null;this.src=\\'https://i.ytimg.com/vi/'+v.id+'/mqdefault.jpg\\'">'
                  +'<span class="dur"></span>'
                  +'<div class="ov"><div class="plus">'+(inQ?'✓':'+')+'</div><span>'+(inQ?'Đã thêm':'Thêm vào danh sách')+'</span></div></div>'
                  +'<div class="ctitle"></div>';
                c.querySelector('.dur').textContent=v.dur||'';
                c.querySelector('.ctitle').textContent=v.title;
                c.onclick=()=>{
                  if(st&&!st.can)return;
                  call('add',{url:v.id,title:v.title,playNow:0});
                };
                g.appendChild(c);
              });
            }
            async function doSearch(raw){
              const val=(raw||'').trim();
              if(/(?:youtu\\.be\\/|youtube\\.com\\/|^[A-Za-z0-9_-]{11}$)/.test(val)){
                if(st&&!st.can)return;
                await call('add',{url:val,playNow:0});
                $('qInput').value='';
                return;
              }
              curQ=val;
              curPage=1;
              hasMore=true;
              $('gridTitle').textContent=val?('🔎 Kết quả cho: "'+val+'"'):'🔥 Video Thịnh hành & Đề xuất (Lướt xem không giới hạn)';
              $('grid').innerHTML='';
              results=[];
              await loadPage(val,1);
            }
            async function loadPage(q,p){
              if(loadingMore)return;
              loadingMore=true;
              $('loader').style.display='flex';
              const r=await call('search',{q:q,page:p});
              loadingMore=false;
              $('loader').style.display='none';
              if(Array.isArray(r)&&r.length>0){
                const seen=new Set(results.map(v=>v.id));
                const newItems=r.filter(v=>!seen.has(v.id));
                if(newItems.length===0){hasMore=false;return;}
                results=results.concat(newItems);
                appendCards(newItems);
              }else{
                hasMore=false;
              }
            }
            $('rightCol').addEventListener('scroll',()=>{
              const col=$('rightCol');
              if(!loadingMore&&hasMore&&(col.scrollTop+col.clientHeight>=col.scrollHeight-300)){
                curPage++;
                loadPage(curQ,curPage);
              }
            });
            $('qInput').addEventListener('keydown',e=>{if(e.key==='Enter')doSearch(e.target.value);});
            $('qBtn').onclick=()=>doSearch($('qInput').value);
            $('everyone').onclick=async()=>{
              if(!st||(!st.e&&!st.host))return;
              const s=await call('everyone');
              if(s&&s.seq){st=s;seq=s.seq;render();}
            };
            const isApp=new URLSearchParams(window.location.search).get('env')==='app'||/LTMApp/i.test(navigator.userAgent);
            if(!isApp){
              const eb=$('expandBtn');if(eb)eb.remove();
              const cb=$('collapseBtn');if(cb)cb.remove();
              const b=$('errBrowserBtn');if(b)b.style.display='none';
              const d=$('errDesc');if(d)d.textContent='Chủ sở hữu hoặc YouTube đã tắt tính năng nhúng đối với video này. Bạn có thể mở trực tiếp trên YouTube.';
            }
            $('errBrowserBtn').onclick=()=>call('browser');
            $('errDirectBtn').onclick=()=>{
              if(cur>=0&&st&&st.q&&st.q[cur]){
                const u='https://www.youtube.com/watch?v='+st.q[cur];
                if(isApp){call('openUrl',{url:u});}else{window.open(u,'_blank');}
              }
            };
            if($('expandBtn')) $('expandBtn').onclick=()=>{expanded=true;render();};
            if($('collapseBtn')) $('collapseBtn').onclick=()=>{expanded=false;render();};
            $('errBackBtn').onclick=()=>{expanded=false;render();};
            window.addEventListener('keydown',e=>{if((e.key==='Escape'||e.key==='Esc')&&expanded){expanded=false;render();}});
            let sessionEnded=false;
            function onSessionEnded(){
              if(sessionEnded)return;
              sessionEnded=true;
              try{if(player&&player.stopVideo)player.stopVideo();}catch(e){}
              try{if(player&&player.pauseVideo)player.pauseVideo();}catch(e){}
              const p=$('player');if(p)p.innerHTML='';
              document.body.innerHTML=`
                <div style="display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;background:#111214;color:#f2f3f5;font-family:sans-serif;text-align:center;gap:14px;padding:24px;">
                  <div style="font-size:42px;">⏹️</div>
                  <div style="font-size:18px;font-weight:700;">Cuộc gọi hoặc phiên xem chung đã kết thúc</div>
                  <div style="font-size:13px;color:#949ba4;">Trình phát đã dừng và ngắt kết nối. Bạn có thể đóng tab này.</div>
                  <button onclick="window.close()" style="background:#5865f2;color:#fff;border:0;border-radius:6px;padding:8px 20px;font-size:13px;font-weight:600;cursor:pointer;margin-top:6px;">Đóng tab</button>
                </div>
              `;
              try{window.close();}catch(e){}
            }

            let toastTimer=null;
            const cs=$('controlShield');
            if(cs){
              cs.onclick=()=>{
                const t=$('shieldToast');
                if(!t)return;
                t.style.display='block';
                if(toastTimer)clearTimeout(toastTimer);
                toastTimer=setTimeout(()=>{t.style.display='none';},2000);
              };
            }
            if($('playPrompt')){
              $('playPrompt').onclick=()=>{
                $('playPrompt').style.display='none';
                if(player&&player.playVideo){
                  try{player.playVideo();}catch(e){}
                }
              };
            }
            if($('stopBtn')){
              $('stopBtn').onclick=()=>{
                if(confirm('Bạn có chắc muốn dừng xem YouTube cùng nhau cho cả phòng?')){
                  call('stop');
                }
              };
            }
            window.addEventListener('keydown',e=>{
              if(!st||st.can)return;
              if(e.key===' '||e.code==='Space'||e.key==='k'||e.key==='K'){
                e.preventDefault();
                e.stopPropagation();
                const t=$('shieldToast');
                if(t){
                  t.style.display='block';
                  if(toastTimer)clearTimeout(toastTimer);
                  toastTimer=setTimeout(()=>{t.style.display='none';},2000);
                }
              }
            },true);

            doSearch('');
            let failCount=0;
            setInterval(async()=>{
              if(sessionEnded)return;
              const s=await call('state');
              if(!s){
                failCount++;
                if(failCount>=3){
                  onSessionEnded();
                }
                return;
              }
              failCount=0;
              if(s.stopped){
                onSessionEnded();
                return;
              }
              st=s;
              if(s.seq!==seq){
                seq=s.seq;
                render();
                apply();
              }else if(ready&&st&&cur>=0&&Date.now()>=applying){
                const ps=player.getPlayerState?player.getPlayerState():-1;
                if(st.p && ps!==1 && ps!==3){
                  guard();
                  try{player.playVideo();}catch(e){}
                }else if(!st.p && ps===1){
                  guard();
                  try{player.pauseVideo();}catch(e){}
                }
              }
            },500);
            </script></body></html>
            """;
}

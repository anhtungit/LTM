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

    private record VideoCard(String id, String title, String duration) {}

    private static final List<VideoCard> DEFAULT_TRENDING = List.of(
            new VideoCard("jfKfPfyJRdk", "lofi hip hop radio - beats to relax/study to", "LIVE"),
            new VideoCard("4xDzrJKXOOY", "synthwave radio - chill synth / retro beats", "LIVE"),
            new VideoCard("5yx6BWlEVcY", "Chillhop Radio - jazzy & lofi hip hop beats", "LIVE"),
            new VideoCard("DWcJFNfaw9c", "Ambient Study Music To Concentrate - 4 Hours", "3:58:12"),
            new VideoCard("hHW1oY26kxQ", "Coding Session Lofi Beats - Deep Focus Mix", "1:45:20"),
            new VideoCard("lTRiuFIWV54", "1 A.M Study Session - [lofi hip hop/chill beats]", "1:01:14"),
            new VideoCard("n61ULEU7CO0", "Best of Lofi Hip Hop - Chill Beats Mix", "2:12:30"),
            new VideoCard("bM7SZ5SBzyY", "Alan Walker - Fade [NCS Release]", "4:20")
    );

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
            javafx.embed.swing.JFXPanel fxPanel = new javafx.embed.swing.JFXPanel();
            javafx.application.Platform.runLater(() -> {
                try {
                    javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
                    webView.getEngine().setUserAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
                    webView.getEngine().load(url);
                    javafx.scene.Scene scene = new javafx.scene.Scene(webView);
                    fxPanel.setScene(scene);
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
        try {
            URI uri = URI.create(url);
            try {
                Desktop.getDesktop().browse(uri);
            } catch (Exception e) {
                new ProcessBuilder("xdg-open", uri.toString()).start();
            }
        } catch (Exception e) {
            System.err.println("[WatchTogether] Không mở được trình duyệt: " + e.getMessage());
        }
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        queue.clear();
        index = -1;
        playing = false;
        hostId = null;
        seq++;
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
        return everyoneControls || selfId.equals(hostId) || hostId == null;
    }

    /** Áp dụng trạng thái do người khác gửi; bỏ qua nếu họ không có quyền điều khiển. */
    public synchronized boolean onRemoteState(String senderId, String data) {
        Map<String, String> m = decode(data);
        boolean allowed = hostId == null || everyoneControls || senderId.equals(hostId);
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
        hostId = m.get("h");
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
            case "search" -> out = searchJson(q.get("q"));
            case "event" -> localEvent("1".equals(q.get("p")), parseDouble(q.get("t")), parseInt(q.get("i"), -2));
            case "ended" -> ended(parseInt(q.get("i"), -2));
            case "add" -> add(q.get("url"), q.get("title"), "1".equals(q.get("playNow")));
            case "remove" -> remove(parseInt(q.get("i"), -1));
            case "play" -> playIndex(parseInt(q.get("i"), -1));
            case "everyone" -> toggleEveryone();
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
            if (i != index) return;
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
            if (index < 0 || playNow) {
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

    private String searchJson(String rawQuery) {
        List<VideoCard> cards = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String q = (rawQuery == null || rawQuery.isBlank())
                ? "nhạc chill lofi hip hop trending 2026"
                : rawQuery.trim();
        try {
            String url = "https://www.youtube.com/results?search_query="
                    + URLEncoder.encode(q, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8")
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200 && resp.body() != null) {
                Matcher m = YT_SEARCH_ITEM.matcher(resp.body());
                while (m.find() && cards.size() < 16) {
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
        if (cards.isEmpty()) {
            cards.addAll(DEFAULT_TRENDING);
        }
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

    private synchronized void toggleEveryone() {
        if (hostId != null && !selfId.equals(hostId)) return;
        everyoneControls = !everyoneControls;
        if (hostId == null) hostId = selfId;
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
        StringBuilder sb = new StringBuilder("{\"seq\":").append(seq)
                .append(",\"i\":").append(index)
                .append(",\"p\":").append(playing)
                .append(",\"t\":").append(String.format(java.util.Locale.ROOT, "%.2f", effectivePos()))
                .append(",\"e\":").append(everyoneControls)
                .append(",\"host\":").append(hostId == null || selfId.equals(hostId))
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
            .host-pill{display:inline-flex;align-items:center;gap:6px;background:#1e1f22;color:#23a55a;border:1px solid #2b2d31;border-radius:16px;padding:5px 12px;font-weight:600;font-size:12px;cursor:pointer;white-space:nowrap}
            .host-pill:hover{background:#2b2d31}
            .search-wrap{flex:1;max-width:480px;margin:0 auto;display:flex;align-items:center;background:#1e1f22;border:1px solid #2b2d31;border-radius:6px;padding:0 10px}
            .search-wrap:focus-within{border-color:#5865f2}
            .search-wrap input{flex:1;background:transparent;border:0;color:#fff;padding:8px 6px;font-size:13px;outline:none}
            .search-wrap button{background:transparent;border:0;color:#b5bac1;cursor:pointer;font-size:14px;padding:4px 6px}
            .search-wrap button:hover{color:#fff}
            #stage{flex:1;display:flex;min-height:0;position:relative}
            #leftCol{width:46%;display:flex;flex-direction:column;border-right:1px solid #232428;background:#111214;min-width:0}
            body.empty #leftCol{display:none}
            body.expanded #leftCol{width:100%;border-right:0}
            body.expanded #playlistSection,body.expanded #rightCol{display:none}
            #playerWrap{position:relative;width:100%;aspect-ratio:16/9;background:#000;flex-shrink:0}
            body.expanded #playerWrap{aspect-ratio:auto;flex:1;height:100%}
            #player{width:100%;height:100%}
            #expandBtn{position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);background:rgba(255,255,255,0.92);color:#111214;border:0;border-radius:20px;padding:8px 16px;font-weight:700;font-size:12px;cursor:pointer;opacity:0;transition:opacity .15s;box-shadow:0 4px 14px rgba(0,0,0,.5);z-index:5}
            #playerWrap:hover #expandBtn{opacity:1}
            body.expanded #expandBtn{display:none}
            #collapseBtn{display:none;position:absolute;left:12px;top:50%;transform:translateY(-50%);background:rgba(255,255,255,0.92);color:#111214;border:0;border-radius:16px;padding:6px 14px;font-weight:700;font-size:12px;cursor:pointer;z-index:10;box-shadow:0 4px 12px rgba(0,0,0,.5)}
            body.expanded #collapseBtn{display:inline-flex;align-items:center;gap:4px}
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
            .card{display:flex;flex-direction:column;cursor:pointer;border-radius:8px;overflow:hidden;background:#1e1f22;border:1px solid #232428;transition:transform .12s,border-color .12s}
            .card:hover{transform:translateY(-2px);border-color:#5865f2}
            .thumb{position:relative;width:100%;aspect-ratio:16/9;background:#000;overflow:hidden}
            .thumb img{width:100%;height:100%;object-fit:cover}
            .dur{position:absolute;right:6px;bottom:6px;background:rgba(0,0,0,.82);color:#fff;font-size:11px;font-weight:600;padding:2px 5px;border-radius:4px}
            .ov{position:absolute;inset:0;background:rgba(0,0,0,.55);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:6px;opacity:0;transition:opacity .15s}
            .card:hover .ov{opacity:1}
            .plus{width:38px;height:38px;border-radius:50%;background:#23a55a;color:#fff;display:flex;align-items:center;justify-content:center;font-size:20px;font-weight:700}
            .ov span{font-size:11px;font-weight:600;color:#fff}
            .ctitle{padding:8px 10px;font-size:12px;font-weight:600;color:#f2f3f5;line-height:1.35;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}
            </style></head><body class="empty">
            <div id="topbar">
              <button class="host-pill" id="everyone" title="Chuyển quyền điều khiển danh sách phát">● Đang chia sẻ ⌄</button>
              <div class="search-wrap">
                <input id="qInput" placeholder="Tìm kiếm video YouTube hoặc dán đường dẫn URL...">
                <button id="qBtn" title="Tìm kiếm">🔍</button>
              </div>
            </div>
            <div id="stage">
              <button id="collapseBtn">Danh sách phát &gt;</button>
              <div id="leftCol">
                <div id="playerWrap">
                  <div id="player"></div>
                  <button id="expandBtn">⤢ Phóng to video</button>
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
                <div class="sec-title" id="gridTitle">🔥 Video Thịnh hành &amp; Đề xuất</div>
                <div id="grid"></div>
              </div>
            </div>
            <script src="https://www.youtube.com/iframe_api"></script>
            <script>
            let player=null,ready=false,st=null,seq=0,cur=-1,applying=0,lastT=0,lastWall=Date.now(),expanded=false,results=[];
            const $=id=>document.getElementById(id);
            const call=(p,q)=>fetch(p+(q?'?'+new URLSearchParams(q):'')).then(r=>r.json()).catch(()=>null);
            function onYouTubeIframeAPIReady(){
              player=new YT.Player('player',{width:'100%',height:'100%',playerVars:{origin:location.origin,playsinline:1,rel:0},
                events:{onReady:()=>{ready=true;apply();},onStateChange:onState,onError:()=>{if(st&&st.q.length>1)call('ended',{i:cur});}}});
            }
            function guard(){applying=Date.now()+1500;}
            function apply(){
              if(!ready||!st)return;
              if(st.i<0||st.i>=st.q.length){try{player.pauseVideo();}catch(e){}return;}
              if(cur!==st.i){cur=st.i;guard();player.loadVideoById(st.q[cur],st.t);if(!st.p)setTimeout(()=>player.pauseVideo(),400);return;}
              const t=player.getCurrentTime?player.getCurrentTime():0;
              if(Math.abs(t-st.t)>2.2){guard();player.seekTo(st.t,true);}
              const s=player.getPlayerState?player.getPlayerState():-1;
              if(st.p&&s!==1&&s!==3){guard();player.playVideo();}
              if(!st.p&&s===1){guard();player.pauseVideo();}
            }
            function onState(e){
              if(Date.now()<applying)return;
              if(e.data===0){call('ended',{i:cur});return;}
              if(e.data===1||e.data===2)call('event',{p:e.data===1?1:0,t:player.getCurrentTime(),i:cur});
            }
            setInterval(()=>{
              if(!ready||!st||Date.now()<applying||!player.getCurrentTime)return;
              const t=player.getCurrentTime(),now=Date.now(),ps=player.getPlayerState();
              const expect=lastT+(ps===1?(now-lastWall)/1000:0);
              if(Math.abs(t-expect)>2.2)call('event',{p:ps===1?1:0,t:t,i:cur});
              lastT=t;lastWall=now;
            },1000);
            function render(){
              if(!st)return;
              const hasQ=st.q&&st.q.length>0;
              document.body.className=hasQ?(expanded?'expanded':''):'empty';
              $('everyone').textContent=st.e?'● Mọi người cùng điều khiển':'● Chỉ chủ phòng điều khiển';
              $('msg').textContent=st.can?'':('Chỉ xem (Chủ phòng khóa điều khiển)');
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
                x.onclick=ev=>{ev.stopPropagation();call('remove',{i});};
                d.onclick=()=>call('play',{i});
                d.appendChild(x);l.appendChild(d);
              });
              renderGrid();
            }
            function renderGrid(){
              const g=$('grid');g.innerHTML='';
              const qSet=new Set(st&&st.q?st.q:[]);
              results.forEach(v=>{
                const inQ=qSet.has(v.id);
                const c=document.createElement('div');c.className='card';
                c.innerHTML='<div class="thumb"><img src="https://i.ytimg.com/vi/'+v.id+'/hqdefault.jpg">'
                  +'<span class="dur"></span>'
                  +'<div class="ov"><div class="plus">'+(inQ?'✓':'+')+'</div><span>'+(inQ?'Phát ngay':'Thêm vào danh sách')+'</span></div></div>'
                  +'<div class="ctitle"></div>';
                c.querySelector('.dur').textContent=v.dur||'';
                c.querySelector('.ctitle').textContent=v.title;
                c.onclick=()=>call('add',{url:v.id,title:v.title,playNow:inQ?1:0});
                g.appendChild(c);
              });
            }
            async function doSearch(raw){
              const val=(raw||'').trim();
              if(/(?:youtu\\.be\\/|youtube\\.com\\/|^[A-Za-z0-9_-]{11}$)/.test(val)){
                await call('add',{url:val,playNow:1});
                $('qInput').value='';
                return;
              }
              $('gridTitle').textContent=val?('🔎 Kết quả cho: "'+val+'"'):'🔥 Video Thịnh hành & Đề xuất';
              const r=await call('search',{q:val});
              if(Array.isArray(r)){results=r;renderGrid();}
            }
            $('qInput').addEventListener('keydown',e=>{if(e.key==='Enter')doSearch(e.target.value);});
            $('qBtn').onclick=()=>doSearch($('qInput').value);
            $('everyone').onclick=()=>call('everyone');
            $('expandBtn').onclick=()=>{expanded=true;render();};
            $('collapseBtn').onclick=()=>{expanded=false;render();};
            doSearch('');
            setInterval(async()=>{const s=await call('state');if(!s)return;st=s;if(s.seq!==seq){seq=s.seq;render();apply();}},500);
            </script></body></html>
            """;
}

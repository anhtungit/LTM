package vn.edu.ltm.chat.presentation;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Xem YouTube cùng nhau": trạng thái video/playlist dùng chung của một cuộc gọi.
 * Video phát trong trình duyệt hệ thống qua trang cục bộ (YouTube IFrame API, chỉ lắng nghe 127.0.0.1 + token);
 * trạng thái được đồng bộ tới mọi thành viên qua tín hiệu cuộc gọi YT_STATE / YT_STOP / YT_REQ.
 */
final class WatchTogether {

    static final String ACTION_STATE = "YT_STATE";
    static final String ACTION_STOP = "YT_STOP";
    static final String ACTION_REQ = "YT_REQ";

    private static final Pattern YT_ID = Pattern.compile(
            "(?:youtu\\.be/|[?&]v=|/embed/|/shorts/|/live/)([A-Za-z0-9_-]{11})|^([A-Za-z0-9_-]{11})$");

    private final String selfId;
    private final BiConsumer<String, String> sendSignal;
    private final String token = HexFormat.of().formatHex(randomBytes());
    private HttpServer server;

    // Trạng thái dùng chung (khóa bằng this)
    private final List<String> queue = new ArrayList<>();
    private int index = -1;
    private boolean playing;
    private double pos;
    private long stampMs = System.currentTimeMillis();
    private boolean everyoneControls = true;
    private String hostId;
    private long seq = 1;

    WatchTogether(String selfId, BiConsumer<String, String> sendSignal) {
        this.selfId = selfId;
        this.sendSignal = sendSignal;
    }

    private static byte[] randomBytes() {
        byte[] b = new byte[12];
        new SecureRandom().nextBytes(b);
        return b;
    }

    static String parseVideoId(String text) {
        if (text == null) return null;
        Matcher m = YT_ID.matcher(text.trim());
        if (!m.find()) return null;
        return m.group(1) != null ? m.group(1) : m.group(2);
    }

    /** Mở trang xem chung trong trình duyệt (khởi động server cục bộ nếu cần). */
    synchronized void openBrowser() {
        try {
            if (server == null) {
                server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
                server.createContext("/" + token + "/", this::handle);
                server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
                server.start();
            }
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/" + token + "/");
            try {
                Desktop.getDesktop().browse(uri);
            } catch (Exception e) {
                new ProcessBuilder("xdg-open", uri.toString()).start();
            }
        } catch (IOException e) {
            System.err.println("[WatchTogether] Không mở được trình duyệt: " + e.getMessage());
        }
    }

    synchronized void stop() {
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

    synchronized boolean isActive() {
        return hostId != null || !queue.isEmpty();
    }

    /** Người mới vào cuộc gọi xin trạng thái hiện tại. */
    void requestState() {
        sendSignal.accept(ACTION_REQ, "");
    }

    /** Có người xin trạng thái: nếu đang xem thì phát lại cho họ. */
    void onRequest() {
        String data;
        synchronized (this) {
            if (!isActive()) return;
            data = encode();
        }
        sendSignal.accept(ACTION_STATE, data);
    }

    synchronized boolean canControl() {
        return everyoneControls || selfId.equals(hostId) || hostId == null;
    }

    /** Áp dụng trạng thái do người khác gửi; bỏ qua nếu họ không có quyền điều khiển. */
    synchronized boolean onRemoteState(String senderId, String data) {
        Map<String, String> m = decode(data);
        boolean allowed = hostId == null || everyoneControls || senderId.equals(hostId);
        if (!allowed) return false;
        queue.clear();
        String q = m.getOrDefault("q", "");
        if (!q.isEmpty()) queue.addAll(List.of(q.split(",")));
        index = parseInt(m.get("i"), -1);
        playing = "1".equals(m.get("p"));
        pos = parseDouble(m.get("t"));
        stampMs = System.currentTimeMillis();
        everyoneControls = !"0".equals(m.get("e"));
        hostId = m.get("h");
        seq++;
        return true;
    }

    // ---- Trình duyệt -> Java ----

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
            case "event" -> localEvent("1".equals(q.get("p")), parseDouble(q.get("t")), parseInt(q.get("i"), -2));
            case "ended" -> ended(parseInt(q.get("i"), -2));
            case "add" -> add(q.get("url"));
            case "remove" -> remove(parseInt(q.get("i"), -1));
            case "play" -> playIndex(parseInt(q.get("i"), -1));
            case "everyone" -> toggleEveryone();
            case "stop" -> {
                stopForAll();
            }
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
            seq++; // ép trang tải lại trạng thái chung, hoàn tác thao tác không được phép
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

    private void add(String url) {
        String id = parseVideoId(url);
        if (id == null) return;
        mutate(() -> {
            queue.add(id);
            if (index < 0) {
                index = 0;
                pos = 0;
                playing = true;
            }
        });
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
        if (hostId != null && !selfId.equals(hostId)) return; // chỉ chủ phòng xem được quyền chỉnh
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

    /** Có người dừng xem chung: đóng trạng thái cục bộ (trang trình duyệt tự đóng băng khi hết video). */
    void onRemoteStop() {
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
            sb.append('"').append(queue.get(k)).append('"');
        }
        return sb.append("]}").toString();
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
            body{margin:0;background:#1e1f22;color:#f2f3f5;font:14px sans-serif;display:flex;height:100vh}
            #main{flex:1;display:flex;flex-direction:column;min-width:0}
            #player{flex:1;background:#000}
            #side{width:340px;background:#2b2d31;display:flex;flex-direction:column;padding:12px;box-sizing:border-box}
            h3{margin:0 0 8px}
            input{width:100%;box-sizing:border-box;padding:8px;border:0;border-radius:4px;background:#1e1f22;color:#fff;margin-bottom:6px}
            button{background:#5865f2;color:#fff;border:0;border-radius:4px;padding:6px 10px;cursor:pointer;margin:2px 0}
            button.d{background:#da373c}button.g{background:#3b3d44}
            #list{flex:1;overflow:auto;margin-top:8px}
            .it{display:flex;align-items:center;gap:6px;padding:6px;border-radius:4px;cursor:pointer}
            .it.cur{background:#404249}.it span{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
            #msg{color:#b5bac1;font-size:12px;margin:6px 0}
            </style></head><body>
            <div id="main"><div id="player"></div></div>
            <div id="side"><h3>Xem YouTube cùng nhau</h3>
            <input id="url" placeholder="Dán link YouTube rồi nhấn Enter để thêm vào danh sách">
            <div id="msg"></div>
            <div><button class="g" id="everyone"></button> <button class="d" id="stop">Dừng xem chung</button></div>
            <div id="list"></div></div>
            <script src="https://www.youtube.com/iframe_api"></script>
            <script>
            let player=null,ready=false,st=null,seq=0,cur=-1,applying=0,lastT=0,lastWall=Date.now();
            const $=id=>document.getElementById(id);
            const call=(p,q)=>fetch(p+(q?'?'+new URLSearchParams(q):'')).then(r=>r.json()).catch(()=>null);
            function onYouTubeIframeAPIReady(){
              player=new YT.Player('player',{width:'100%',height:'100%',playerVars:{origin:location.origin,playsinline:1},
                events:{onReady:()=>{ready=true;apply();},onStateChange:onState}});}
            function guard(){applying=Date.now()+1500;}
            function apply(){
              if(!ready||!st)return;
              if(st.i<0||st.i>=st.q.length){player.pauseVideo();return;}
              if(cur!==st.i){cur=st.i;guard();player.loadVideoById(st.q[cur],st.t);if(!st.p)setTimeout(()=>player.pauseVideo(),400);return;}
              const t=player.getCurrentTime();
              if(Math.abs(t-st.t)>2){guard();player.seekTo(st.t,true);}
              const s=player.getPlayerState();
              if(st.p&&s!==1&&s!==3){guard();player.playVideo();}
              if(!st.p&&s===1){guard();player.pauseVideo();}
            }
            function onState(e){
              if(Date.now()<applying)return;
              if(e.data===0){call('ended',{i:cur});return;}
              if(e.data===1||e.data===2)call('event',{p:e.data===1?1:0,t:player.getCurrentTime(),i:cur});
            }
            setInterval(()=>{ // phát hiện tua
              if(!ready||!st||Date.now()<applying)return;
              const t=player.getCurrentTime(),now=Date.now();
              const expect=lastT+(player.getPlayerState()===1?(now-lastWall)/1000:0);
              if(Math.abs(t-expect)>2)call('event',{p:player.getPlayerState()===1?1:0,t:t,i:cur});
              lastT=t;lastWall=now;
            },1000);
            function render(){
              $('everyone').textContent=st.e?'Mọi người đều điều khiển được':'Chỉ chủ phòng điều khiển';
              $('everyone').style.display=st.host?'':'none';
              $('msg').textContent=st.can?'':'Chủ phòng không cho phép bạn điều khiển.';
              const l=$('list');l.innerHTML='';
              st.q.forEach((id,i)=>{const d=document.createElement('div');d.className='it'+(i===st.i?' cur':'');
                d.innerHTML='<img src="https://i.ytimg.com/vi/'+id+'/default.jpg" width="64"><span>'+id+'</span>';
                const x=document.createElement('button');x.className='d';x.textContent='✕';
                x.onclick=ev=>{ev.stopPropagation();call('remove',{i});};
                d.onclick=()=>call('play',{i});d.appendChild(x);l.appendChild(d);});
            }
            $('url').addEventListener('keydown',e=>{if(e.key==='Enter'){call('add',{url:e.target.value});e.target.value='';}});
            $('everyone').onclick=()=>call('everyone');
            $('stop').onclick=()=>{call('stop');document.body.innerHTML='<p style="padding:20px">Đã dừng xem chung. Bạn có thể đóng tab này.</p>';};
            setInterval(async()=>{const s=await call('state');if(!s)return;st=s;if(s.seq!==seq){seq=s.seq;render();apply();}},500);
            </script></body></html>
            """;
}

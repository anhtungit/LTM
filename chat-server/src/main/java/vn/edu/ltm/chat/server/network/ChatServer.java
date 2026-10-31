package vn.edu.ltm.chat.server.network;

import vn.edu.ltm.chat.server.dal.DatabaseManager;
import vn.edu.ltm.chat.server.protocol.Packet;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Centralized Multi-threaded TCP Socket Server with UDP Zero-Config Auto-Discovery.
 * Uses Executors.newCachedThreadPool() and ConcurrentHashMap for Online Registry.
 */
public final class ChatServer {
    public static final int DEFAULT_PORT = 8888;
    public static final int DISCOVERY_UDP_PORT = 8889;
    public static final String DISCOVERY_MAGIC_REQ = "LTM_CHAT_DISCOVER_REQ";
    public static final String DISCOVERY_MAGIC_RES_PREFIX = "LTM_CHAT_DISCOVER_RES:";

    private final int port;
    private final int discoveryPort;
    private final DatabaseManager db;
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<String, ClientHandler> onlineRegistry = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, InetSocketAddress> udpVoiceEndpoints = new ConcurrentHashMap<>();
    private final Set<ClientHandler> activeHandlers = new CopyOnWriteArraySet<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private DatagramSocket discoverySocket;
    private DatagramSocket voiceUdpSocket;

    public ChatServer(int port, DatabaseManager db) {
        this(port, port == DEFAULT_PORT ? DISCOVERY_UDP_PORT : 0, db);
    }

    public ChatServer(int port, int discoveryPort, DatabaseManager db) {
        this.port = port;
        this.discoveryPort = discoveryPort;
        this.db = db;
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        serverSocket = new ServerSocket(port);
        startUdpDiscoveryResponder();
        startUdpVoiceRelay(serverSocket.getLocalPort());

        String lanIp = detectLanIp();
        int actualPort = serverSocket.getLocalPort();
        System.out.println("====================================================");
        System.out.println(" [LTM Chat Server] Đang lắng nghe tại cổng TCP " + actualPort);
        System.out.println(" [Auto-Discovery]  UDP cổng " + getDiscoveryPort() + " (Zero-Config)");
        System.out.println(" [Địa chỉ LAN]     " + lanIp + ":" + actualPort);
        String dalName = db.isPostgres() ? "Supabase Cloud DB" : "SQLite Local DAL";
        System.out.println(" [" + dalName + "] Sẵn sàng lưu trữ tin nhắn & tệp");
        System.out.println("====================================================");

        if (port == DEFAULT_PORT) {
            startGistAutoPublisher(lanIp, actualPort);
        }

        threadPool.submit(() -> {
            while (running.get()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setTcpNoDelay(true);
                    ClientHandler handler = new ClientHandler(clientSocket, this, db);
                    activeHandlers.add(handler);
                    threadPool.submit(handler);
                } catch (IOException e) {
                    if (running.get()) {
                        System.err.println("[ChatServer] Lỗi khi chấp nhận kết nối: " + e.getMessage());
                    }
                }
            }
        });
    }

    private void startGistAutoPublisher(String lanIp, int actualPort) {
        GistRegistryPublisher publisher = new GistRegistryPublisher();
        if (!publisher.isConfigured()) {
            return;
        }
        Thread gistThread = new Thread(() -> {
            while (running.get()) {
                try {
                    publisher.checkAndPublish(lanIp, actualPort);
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception ignored) {
                }
            }
        }, "gist-registry-publisher");
        gistThread.setDaemon(true);
        gistThread.start();
    }

    private void startUdpDiscoveryResponder() {
        try {
            DatagramSocket ds = new DatagramSocket(null);
            ds.setReuseAddress(true);
            ds.setBroadcast(true);
            ds.bind(new InetSocketAddress("0.0.0.0", discoveryPort));
            this.discoverySocket = ds;

            threadPool.submit(() -> {
                byte[] buf = new byte[256];
                while (running.get() && !ds.isClosed()) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        ds.receive(packet);
                        String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
                        if (DISCOVERY_MAGIC_REQ.equals(msg)) {
                            String replyStr = DISCOVERY_MAGIC_RES_PREFIX + getPort();
                            byte[] replyBytes = replyStr.getBytes(StandardCharsets.UTF_8);
                            DatagramPacket reply = new DatagramPacket(
                                    replyBytes, replyBytes.length, packet.getAddress(), packet.getPort());
                            ds.send(reply);
                        }
                    } catch (IOException ignored) {
                    }
                }
            });
        } catch (IOException e) {
            System.err.println("[Auto-Discovery] Không thể mở cổng UDP " + discoveryPort + ": " + e.getMessage());
        }
    }

    private void startUdpVoiceRelay(int udpPort) {
        try {
            DatagramSocket vs = new DatagramSocket(null);
            vs.setReuseAddress(true);
            vs.bind(new InetSocketAddress("0.0.0.0", udpPort));
            this.voiceUdpSocket = vs;

            threadPool.submit(() -> {
                byte[] buf = new byte[2048];
                while (running.get() && !vs.isClosed()) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        vs.receive(packet);
                        handleUdpVoicePacket(vs, packet);
                    } catch (IOException ignored) {
                    }
                }
            });
        } catch (IOException e) {
            System.err.println("[Voice-UDP] Không thể mở cổng UDP thoại " + udpPort + ": " + e.getMessage());
        }
    }

    private void handleUdpVoicePacket(DatagramSocket vs, DatagramPacket packet) {
        byte[] data = packet.getData();
        int offset = packet.getOffset();
        int len = packet.getLength();
        int nl = -1;
        for (int i = offset; i < offset + len; i++) {
            if (data[i] == '\n') {
                nl = i;
                break;
            }
        }
        if (nl <= offset) return;

        String header = new String(data, offset, nl - offset, StandardCharsets.UTF_8);
        InetSocketAddress senderAddr = (InetSocketAddress) packet.getSocketAddress();

        if (header.startsWith("LTM_VOICE_REG|")) {
            String uid = header.substring("LTM_VOICE_REG|".length()).trim();
            if (!uid.isEmpty() && isUserOnline(uid)) {
                udpVoiceEndpoints.put(uid, senderAddr);
                byte[] ack = "LTM_VOICE_ACK\n".getBytes(StandardCharsets.UTF_8);
                try {
                    vs.send(new DatagramPacket(ack, ack.length, senderAddr));
                } catch (IOException ignored) {
                }
            }
            return;
        }

        if (!header.startsWith("LTM_VOICE|")) return;
        String[] parts = header.split("\\|", 5);
        if (parts.length < 5) return;

        String senderId = parts[1];
        String senderName = parts[2];
        String convId = parts[3];
        if (senderId.isEmpty() || convId.isEmpty() || !isUserOnline(senderId)) return;

        udpVoiceEndpoints.put(senderId, senderAddr);
        int pcmOffset = nl + 1;
        int pcmLen = (offset + len) - pcmOffset;
        if (pcmLen <= 0) return;

        if (db.isGroupConversation(convId)) {
            if (!db.isUserMemberOfGroup(senderId, convId)) return;
            byte[] forwardBytes = new byte[len];
            System.arraycopy(data, offset, forwardBytes, 0, len);
            for (String memberId : onlineRegistry.keySet()) {
                if (senderId.equals(memberId) || !db.isUserMemberOfGroup(memberId, convId)) continue;
                InetSocketAddress target = udpVoiceEndpoints.get(memberId);
                if (target != null) {
                    try {
                        vs.send(new DatagramPacket(forwardBytes, forwardBytes.length, target));
                    } catch (IOException ignored) {
                    }
                }
            }
            return;
        }

        InetSocketAddress target = udpVoiceEndpoints.get(convId);
        if (target != null) {
            byte[] outHdr = ("LTM_VOICE|" + senderId + "|" + senderName + "|" + senderId + "|\n")
                    .getBytes(StandardCharsets.UTF_8);
            byte[] outBuf = new byte[outHdr.length + pcmLen];
            System.arraycopy(outHdr, 0, outBuf, 0, outHdr.length);
            System.arraycopy(data, pcmOffset, outBuf, outHdr.length, pcmLen);
            try {
                vs.send(new DatagramPacket(outBuf, outBuf.length, target));
            } catch (IOException ignored) {
            }
        } else {
            ClientHandler peerHandler = onlineRegistry.get(convId);
            if (peerHandler != null) {
                byte[] pcm = new byte[pcmLen];
                System.arraycopy(data, pcmOffset, pcm, 0, pcmLen);
                peerHandler.sendPacket(Packet.broadcast("CALL_SIGNAL_BROADCAST")
                        .put("conversationId", senderId)
                        .put("channel", "")
                        .put("senderId", senderId)
                        .put("senderName", senderName)
                        .put("action", "AUDIO")
                        .put("data", java.util.Base64.getEncoder().encodeToString(pcm)));
            }
        }
    }

    private static String detectLanIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }

    public int getPort() {
        return serverSocket != null ? serverSocket.getLocalPort() : port;
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getDiscoveryPort() {
        return discoverySocket != null ? discoverySocket.getLocalPort() : discoveryPort;
    }

    public boolean isUserOnline(String userId) {
        return userId != null && onlineRegistry.containsKey(userId);
    }

    public void registerOnlineUser(String userId, ClientHandler handler) {
        if (userId != null) {
            onlineRegistry.put(userId, handler);
        }
    }

    public void unregisterHandler(ClientHandler handler) {
        activeHandlers.remove(handler);
        if (handler.getCurrentUser() != null) {
            String uid = handler.getCurrentUser().id();
            onlineRegistry.remove(uid, handler);
            udpVoiceEndpoints.remove(uid);
        }
    }

    public void broadcastAll(Packet packet) {
        for (ClientHandler handler : activeHandlers) {
            handler.sendPacket(packet);
        }
    }

    public void broadcastExcept(ClientHandler excluded, Packet packet) {
        for (ClientHandler handler : activeHandlers) {
            if (handler != excluded) {
                handler.sendPacket(packet);
            }
        }
    }

    public Set<ClientHandler> getActiveHandlers() {
        return activeHandlers;
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            if (discoverySocket != null && !discoverySocket.isClosed()) {
                discoverySocket.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (voiceUdpSocket != null && !voiceUdpSocket.isClosed()) {
                voiceUdpSocket.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        for (ClientHandler h : activeHandlers) {
            h.closeSocket();
        }
        threadPool.shutdownNow();
        try {
            threadPool.awaitTermination(500, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        activeHandlers.clear();
        onlineRegistry.clear();
        if (db != null) {
            db.close();
        }
    }
}

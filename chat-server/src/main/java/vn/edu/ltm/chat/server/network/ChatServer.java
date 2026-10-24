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
    private final Set<ClientHandler> activeHandlers = new CopyOnWriteArraySet<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private DatagramSocket discoverySocket;

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

        String lanIp = detectLanIp();
        int actualPort = serverSocket.getLocalPort();
        System.out.println("====================================================");
        System.out.println(" [LTM Chat Server] Đang lắng nghe tại cổng TCP " + actualPort);
        System.out.println(" [Auto-Discovery]  UDP cổng " + getDiscoveryPort() + " (Zero-Config)");
        System.out.println(" [Địa chỉ LAN]     " + lanIp + ":" + actualPort);
        System.out.println(" [SQLite DAL]      Sẵn sàng lưu trữ tin nhắn & tệp");
        System.out.println("====================================================");

        if (port == DEFAULT_PORT) {
            startGistAutoPublisher(lanIp, actualPort);
        }

        threadPool.submit(() -> {
            while (running.get()) {
                try {
                    Socket clientSocket = serverSocket.accept();
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

    public void registerOnlineUser(String userId, ClientHandler handler) {
        if (userId != null) {
            onlineRegistry.put(userId, handler);
        }
    }

    public void unregisterHandler(ClientHandler handler) {
        activeHandlers.remove(handler);
        if (handler.getCurrentUser() != null) {
            onlineRegistry.remove(handler.getCurrentUser().id(), handler);
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
    }
}

package vn.edu.ltm.chat.server;

import vn.edu.ltm.chat.server.dal.DatabaseManager;
import vn.edu.ltm.chat.server.network.ChatServer;

import java.util.concurrent.CountDownLatch;

public final class ServerMain {
    private ServerMain() {
    }

    public static void main(String[] args) throws Exception {
        int port = ChatServer.DEFAULT_PORT;
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            try {
                port = Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }
        String portProp = System.getProperty("chat.server.port");
        if (portProp != null && !portProp.isBlank()) {
            port = Integer.parseInt(portProp.trim());
        }

        DatabaseManager db = DatabaseManager.getInstance();
        ChatServer server = new ChatServer(port, db);
        server.start();

        CountDownLatch keepAlive = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[LTM Chat Server] Đang đóng kết nối...");
            server.stop();
            keepAlive.countDown();
        }));
        keepAlive.await();
    }
}


package vn.edu.ltm.chat.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.ltm.chat.server.dal.DatabaseManager;
import vn.edu.ltm.chat.server.network.ChatServer;
import vn.edu.ltm.chat.server.protocol.Packet;
import vn.edu.ltm.chat.server.protocol.ProtocolDtos.ConversationDto;
import vn.edu.ltm.chat.server.protocol.ProtocolDtos.MessageDto;
import vn.edu.ltm.chat.server.protocol.ProtocolDtos.UserDto;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatServerTest {
    @TempDir
    Path tempDir;

    private DatabaseManager db;
    private ChatServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        db = new DatabaseManager(
                tempDir.resolve("test_chat.db").toString(),
                tempDir.resolve("test_storage").toString()
        );
        server = new ChatServer(0, db);
        server.start();
        port = server.getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void supportsLoginSendEditRecallDeleteAndRealtimeBroadcastBetweenTwoClients() throws Exception {
        try (Socket s1 = new Socket("127.0.0.1", port);
             BufferedReader in1 = new BufferedReader(new InputStreamReader(s1.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out1 = new PrintWriter(new OutputStreamWriter(s1.getOutputStream(), StandardCharsets.UTF_8), true);
             Socket s2 = new Socket("127.0.0.1", port);
             BufferedReader in2 = new BufferedReader(new InputStreamReader(s2.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out2 = new PrintWriter(new OutputStreamWriter(s2.getOutputStream(), StandardCharsets.UTF_8), true)) {

            // Client 1 register
            out1.println(new Packet("REGISTER_REQ", "req-1")
                    .put("email", "alice@example.com")
                    .put("password", "secret")
                    .put("displayName", "Alice").toJsonLine());
            Packet loginRes1 = Packet.fromJsonLine(in1.readLine());
            assertTrue(loginRes1.isOk());
            UserDto alice = loginRes1.getObj("user", UserDto.class);
            assertNotNull(alice);
            assertEquals("alice@example.com", alice.email());

            // Client 2 register
            out2.println(new Packet("REGISTER_REQ", "req-2")
                    .put("email", "bob@example.com")
                    .put("password", "secret")
                    .put("displayName", "Bob").toJsonLine());
            Packet loginRes2 = readNextNonStatus(in2);
            assertEquals("REGISTER_RES", loginRes2.getType());
            assertTrue(loginRes2.isOk());

            // Client 1 receives USER_STATUS_BROADCAST for Bob
            Packet statusBroadcast = Packet.fromJsonLine(in1.readLine());
            assertEquals("USER_STATUS_BROADCAST", statusBroadcast.getType());

            // Client 1 sends message
            out1.println(new Packet("SEND_MSG_REQ", "req-3")
                    .put("conversationId", "nhom-ban-than")
                    .put("channel", "học-tập")
                    .put("senderId", alice.id())
                    .put("senderName", alice.displayName())
                    .put("senderInitials", "AL")
                    .put("content", "Xin chào từ Alice qua TCP!").toJsonLine());

            Packet sendRes = readNextNonStatus(in1);
            assertTrue(sendRes.isOk());
            MessageDto sentMsg = sendRes.getObj("message", MessageDto.class);
            assertEquals("Xin chào từ Alice qua TCP!", sentMsg.content());

            // Client 2 receives NEW_MSG_BROADCAST in real-time
            Packet broadcastMsg = readNextNonStatus(in2);
            assertEquals("NEW_MSG_BROADCAST", broadcastMsg.getType());
            MessageDto receivedByBob = broadcastMsg.getObj("message", MessageDto.class);
            assertEquals(sentMsg.id(), receivedByBob.id());
            assertEquals("Xin chào từ Alice qua TCP!", receivedByBob.content());

            // Client 1 edits message
            out1.println(new Packet("EDIT_MSG_REQ", "req-4")
                    .put("messageId", sentMsg.id())
                    .put("newContent", "Tin nhắn đã sửa").toJsonLine());
            Packet editRes = Packet.fromJsonLine(in1.readLine());
            assertTrue(editRes.isOk());
            assertTrue(editRes.getObj("message", MessageDto.class).edited());

            // Client 2 receives edit broadcast
            Packet editBroadcast = Packet.fromJsonLine(in2.readLine());
            assertEquals("MSG_UPDATED_BROADCAST", editBroadcast.getType());
            assertEquals("EDIT", editBroadcast.getString("action"));

            // Client 1 recalls message
            out1.println(new Packet("RECALL_MSG_REQ", "req-5")
                    .put("messageId", sentMsg.id()).toJsonLine());
            Packet recallRes = Packet.fromJsonLine(in1.readLine());
            assertTrue(recallRes.isOk());
            assertTrue(recallRes.getObj("message", MessageDto.class).recalled());
            server.stop();
        }
    }

    @Test
    void supportsFileUploadAndDownloadAndGroupCreation() throws Exception {
        try (Socket s = new Socket("127.0.0.1", port);
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {

            byte[] sampleBytes = "Hello LTM File Storage!".getBytes(StandardCharsets.UTF_8);
            String b64 = Base64.getEncoder().encodeToString(sampleBytes);

            out.println(new Packet("FILE_UPLOAD_REQ", "up-1")
                    .put("conversationId", "mai-phuong")
                    .put("channel", "")
                    .put("senderId", "alice")
                    .put("senderName", "Alice")
                    .put("senderInitials", "AL")
                    .put("fileName", "hello.txt")
                    .put("fileSize", sampleBytes.length)
                    .put("failed", false)
                    .put("base64Data", b64).toJsonLine());

            Packet upRes = Packet.fromJsonLine(in.readLine());
            assertTrue(upRes.isOk());
            MessageDto fileMsg = upRes.getObj("message", MessageDto.class);
            assertEquals("hello.txt", fileMsg.attachmentName());

            out.println(new Packet("FILE_DOWNLOAD_REQ", "dl-1")
                    .put("messageId", fileMsg.id())
                    .put("fileName", "hello.txt").toJsonLine());
            Packet dlRes = Packet.fromJsonLine(in.readLine());
            assertTrue(dlRes.isOk());
            byte[] downloaded = Base64.getDecoder().decode(dlRes.getString("base64Data"));
            assertEquals("Hello LTM File Storage!", new String(downloaded, StandardCharsets.UTF_8));

            // Create group
            out.println(new Packet("CREATE_GROUP_REQ", "grp-1")
                    .put("groupName", "Nhóm Backend TCP")
                    .putObj("members", List.of("Alice", "Bob")).toJsonLine());
            Packet grpRes = Packet.fromJsonLine(in.readLine());
            assertTrue(grpRes.isOk());
            ConversationDto grp = grpRes.getObj("conversation", ConversationDto.class);
            assertTrue(grp.isGroup());
            assertEquals("Nhóm Backend TCP", grp.displayName());
            assertFalse(grp.channels().isEmpty());
        }
    }

    @Test
    void supportsUdpZeroConfigAutoDiscovery() throws Exception {
        int udpPort = server.getDiscoveryPort();
        assertTrue(udpPort > 0);
        try (java.net.DatagramSocket ds = new java.net.DatagramSocket()) {
            ds.setSoTimeout(1000);
            byte[] req = ChatServer.DISCOVERY_MAGIC_REQ.getBytes(StandardCharsets.UTF_8);
            java.net.DatagramPacket p = new java.net.DatagramPacket(
                    req, req.length, java.net.InetAddress.getByName("127.0.0.1"), udpPort);
            ds.send(p);

            byte[] buf = new byte[128];
            java.net.DatagramPacket res = new java.net.DatagramPacket(buf, buf.length);
            ds.receive(res);
            String reply = new String(res.getData(), res.getOffset(), res.getLength(), StandardCharsets.UTF_8).trim();
            assertEquals(ChatServer.DISCOVERY_MAGIC_RES_PREFIX + port, reply);
        }
    }

    @Test
    void rejectsUnregisteredLoginAndAllowsLoginAfterRegister() throws Exception {
        try (Socket s = new Socket("127.0.0.1", port);
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {

            // 1. Unregistered login fails
            out.println(new Packet("LOGIN_REQ", "req-unregistered")
                    .put("email", "stranger@example.com")
                    .put("password", "anypass").toJsonLine());
            Packet failRes = Packet.fromJsonLine(in.readLine());
            assertFalse(failRes.isOk());
            assertTrue(failRes.getError().contains("Tài khoản không tồn tại"));

            // 2. Explicit register succeeds
            out.println(new Packet("REGISTER_REQ", "req-register")
                    .put("email", "stranger@example.com")
                    .put("password", "correct_pass")
                    .put("displayName", "Người Lạ").toJsonLine());
            Packet regRes = Packet.fromJsonLine(in.readLine());
            assertTrue(regRes.isOk());
            UserDto registered = regRes.getObj("user", UserDto.class);
            assertNotNull(registered);
            assertEquals("stranger@example.com", registered.email());

            // 3. Login with wrong password fails
            out.println(new Packet("LOGIN_REQ", "req-wrong-pass")
                    .put("email", "stranger@example.com")
                    .put("password", "wrong_pass").toJsonLine());
            Packet wrongPassRes = readNextNonStatus(in);
            assertFalse(wrongPassRes.isOk());
            assertTrue(wrongPassRes.getError().contains("Mật khẩu không chính xác"));

            // 4. Login with correct password succeeds
            out.println(new Packet("LOGIN_REQ", "req-login-ok")
                    .put("email", "stranger@example.com")
                    .put("password", "correct_pass").toJsonLine());
            Packet okRes = readNextNonStatus(in);
            assertTrue(okRes.isOk());
            assertEquals(registered.id(), okRes.getObj("user", UserDto.class).id());
        }
    }

    @Test
    void isolatesDirectMessagesAndGroupsPerAccount() throws Exception {
        try (Socket s1 = new Socket("127.0.0.1", port);
             BufferedReader in1 = new BufferedReader(new InputStreamReader(s1.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out1 = new PrintWriter(new OutputStreamWriter(s1.getOutputStream(), StandardCharsets.UTF_8), true);
             Socket s2 = new Socket("127.0.0.1", port);
             BufferedReader in2 = new BufferedReader(new InputStreamReader(s2.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out2 = new PrintWriter(new OutputStreamWriter(s2.getOutputStream(), StandardCharsets.UTF_8), true)) {

            // User 1 registers
            out1.println(new Packet("REGISTER_REQ", "u1-reg")
                    .put("email", "user1@example.com")
                    .put("password", "pass1")
                    .put("displayName", "User One").toJsonLine());
            UserDto user1 = readNextNonStatus(in1).getObj("user", UserDto.class);

            // User 2 registers
            out2.println(new Packet("REGISTER_REQ", "u2-reg")
                    .put("email", "user2@example.com")
                    .put("password", "pass2")
                    .put("displayName", "User Two").toJsonLine());
            UserDto user2 = readNextNonStatus(in2).getObj("user", UserDto.class);

            // Initially both new accounts have 0 conversations (no leaked demo chats)
            out1.println(new Packet("LIST_CONVERSATIONS_REQ", "u1-list-0")
                    .put("userId", user1.id()).toJsonLine());
            ConversationDto[] u1Initial = readNextNonStatus(in1).getObj("conversations", ConversationDto[].class);
            assertEquals(0, u1Initial.length);

            // User 1 starts a DM with An Nguyễn and sends a private message
            out1.println(new Packet("OPEN_DM_REQ", "u1-open-dm")
                    .put("userId", user1.id())
                    .put("peerId", "an-nguyen").toJsonLine());
            Packet openDmRes = readNextNonStatus(in1);
            assertTrue(openDmRes.isOk());

            out1.println(new Packet("SEND_MSG_REQ", "u1-send-dm")
                    .put("conversationId", "an-nguyen")
                    .put("channel", "")
                    .put("senderId", user1.id())
                    .put("senderName", user1.displayName())
                    .put("senderInitials", "U1")
                    .put("content", "Tin nhắn riêng của User 1 cho An").toJsonLine());
            assertTrue(readNextNonStatus(in1).isOk());

            // User 1 creates a private group without User 2
            out1.println(new Packet("CREATE_GROUP_REQ", "u1-grp")
                    .put("creatorId", user1.id())
                    .put("creatorName", user1.displayName())
                    .put("groupName", "Nhóm kín User 1")
                    .putObj("members", List.of("An Nguyễn")).toJsonLine());
            assertTrue(readNextNonStatus(in1).isOk());

            // User 1 now has 2 conversations (an-nguyen DM + private group)
            out1.println(new Packet("LIST_CONVERSATIONS_REQ", "u1-list-1")
                    .put("userId", user1.id()).toJsonLine());
            ConversationDto[] u1Convs = readNextNonStatus(in1).getObj("conversations", ConversationDto[].class);
            assertEquals(2, u1Convs.length);

            // User 2 still has 0 conversations!
            out2.println(new Packet("LIST_CONVERSATIONS_REQ", "u2-list-1")
                    .put("userId", user2.id()).toJsonLine());
            ConversationDto[] u2Convs = readNextNonStatus(in2).getObj("conversations", ConversationDto[].class);
            assertEquals(0, u2Convs.length);

            // Even if User 2 opens a DM with An Nguyễn, User 2 sees 0 messages (not User 1's messages with An Nguyễn)
            out2.println(new Packet("GET_CHAT_HISTORY_REQ", "u2-hist")
                    .put("userId", user2.id())
                    .put("conversationId", "an-nguyen")
                    .put("channel", "").toJsonLine());
            MessageDto[] u2HistoryWithAn = readNextNonStatus(in2).getObj("messages", MessageDto[].class);
            assertEquals(0, u2HistoryWithAn.length);

            // While User 1 sees their 1 message with An Nguyễn
            out1.println(new Packet("GET_CHAT_HISTORY_REQ", "u1-hist")
                    .put("userId", user1.id())
                    .put("conversationId", "an-nguyen")
                    .put("channel", "").toJsonLine());
            MessageDto[] u1HistoryWithAn = readNextNonStatus(in1).getObj("messages", MessageDto[].class);
            assertEquals(1, u1HistoryWithAn.length);
            assertEquals("Tin nhắn riêng của User 1 cho An", u1HistoryWithAn[0].content());
        }
    }

    private Packet readNextNonStatus(BufferedReader in) throws Exception {
        while (true) {
            String line = in.readLine();
            Packet p = Packet.fromJsonLine(line);
            if (!"USER_STATUS_BROADCAST".equals(p.getType())) {
                return p;
            }
        }
    }
}

# Project Rules — LTM Multi-Threaded Chat (`chat-client` & `chat-server`)

## 1. Triết lý Phát triển (Ponytail / YAGNI)
- **Tối giản & Đúng trọng tâm**: Thực hiện thay đổi nhỏ nhất giải quyết triệt để bài toán. Không tự ý thêm tính năng ngoài yêu cầu, không tạo interface/wrapper chỉ có 1 implementation nếu không phục vụ test/DI.
- **Tách file theo trách nhiệm**: Mỗi class chỉ đảm nhận một trách nhiệm rõ ràng. Giữ các class UI (`ChatWorkspace`, `ChatInfoPanel`, `WorkspaceDialogs`) và DAL (`DatabaseManager`, `DatabaseSchemaMigrator`, `FileStorageRepository`) gọn gàng, độc lập.
- **Bảo toàn tài liệu**: Giữ nguyên các comment, docstring và chữ ký hàm công khai không liên quan đến phạm vi sửa đổi.

---

## 2. Kiến trúc Hệ thống
- **`chat-server`** (`vn.edu.ltm.chat.server`):
  - `network`: TCP Server đa luồng (`ChatServer`, `ClientHandler` sử dụng Virtual Threads), UDP LAN Auto-Discovery và `GistRegistryPublisher`.
  - `dal`: Quản lý lưu trữ SQLite / PostgreSQL (`DatabaseManager`), khởi tạo & di trú schema (`DatabaseSchemaMigrator`), lưu trữ tệp đính kèm vật lý (`FileStorageRepository`).
  - `protocol`: Giao thức gói tin JSON-over-TCP 1 dòng (`Packet`, `ProtocolDtos`).
- **`chat-client`** (`vn.edu.ltm.chat`):
  - `domain`: Các record bất biến (`UserProfile`, `Conversation`, `ChatMessage`).
  - `application`: Logic nghiệp vụ (`ChatService`, `LoginService`) giao tiếp qua cổng `ChatGateway`.
  - `infrastructure`: `TcpChatGateway` (kết nối TCP thực tế), `InMemoryChatGateway` (chế độ offline/demo & unit test), `ServerEndpointResolver`.
  - `presentation` & `theme`: Giao diện Swing chuẩn Discord (`DiscordTheme`, `IconFactory`, `ChatWorkspace`, `ChatInfoPanel`, `WorkspaceDialogs`).

---

## 3. Quy tắc Kỹ thuật Bắt buộc

### 3.1. Giao diện Swing & Hiệu năng (EDT Rules)
- **Tuyệt đối không chặn EDT**: Mọi tác vụ mạng (`chatService.*`), đọc/ghi file hoặc khởi tạo tài nguyên nặng phải chạy trên background thread (`CompletableFuture`) và cập nhật lại giao diện qua `SwingUtilities.invokeLater`.
- **Hạn chế Component lồng nhau trong danh sách**: Không nhúng `JTextArea` hoặc quá nhiều `JPanel` con bên trong các nút danh sách hội thoại (`ConversationButton`) vì sẽ làm phình `BasicTextUI` và nuốt sự kiện `MouseEvent` của `JButton`.
- **Cache tài nguyên**: Font chữ và SVG icon phải được lấy qua bộ nhớ đệm của `DiscordTheme` và `IconFactory`, không parse lại SVG hay tạo font mới trong vòng lặp render.

### 3.2. Bảo mật, Phân quyền & Cô lập Dữ liệu
- **Tài khoản mới**: Người dùng mới đăng ký phải bắt đầu với danh sách cuộc trò chuyện trống (`empty`), tuyệt đối không tự động gán vào nhóm chat của người khác.
- **Kiểm tra thành viên nhóm**: Mọi yêu cầu `GET_MESSAGES`, `SEND_MESSAGE`, `ADD_CHANNEL`, `REMOVE_CHANNEL` trên nhóm chat tại `ClientHandler` / `DatabaseManager` phải xác thực người gọi nằm trong danh sách thành viên của nhóm (`isConversationMember`).
- **Định tuyến Tin nhắn Trực tiếp (1-1 DM)**:
  - Sử dụng khóa hội thoại chuẩn hóa `dm:<min_user_id>:<max_user_id>` (`DatabaseManager.canonicalDmKey`) ở tầng lưu trữ để hai chiều gửi/nhận dùng chung lịch sử.
  - Khi phát tin nhắn DM realtime, chỉ gửi gói `NEW_MESSAGE` tới đúng người nhận và người gửi, không broadcast toàn server.

### 3.3. Trạng thái Hoạt động (Online Presence)
- Trạng thái online của người dùng phải phản ánh kết nối phiên TCP thực tế (`ChatServer.isUserOnline(userId)`), không dùng cứng cờ trạng thái tĩnh trong cơ sở dữ liệu.
- Khi người dùng đăng nhập hoặc ngắt kết nối TCP, server phát sự kiện `USER_STATUS` để các client đang mở tự động cập nhật chấm xanh trạng thái.

### 3.4. An toàn Luồng & Cơ sở Dữ liệu (SQLite / JDBC)
- Mọi thao tác ghi SQLite trong `DatabaseManager` phải đi qua khóa đồng bộ `dbWriteLock` kết hợp `WAL` mode và `busy_timeout = 5000` để tránh lỗi `SQLITE_BUSY`.
- Mọi đối tượng JDBC (`Connection`, `PreparedStatement`, `ResultSet`) bắt buộc đặt trong `try-with-resources`.

---

## 4. Build, Kiểm thử & Quản lý Mã nguồn (Git)

- **Lệnh kiểm thử bắt buộc trước khi hoàn tất công việc**:
  - **Trên Windows (PowerShell / CMD)**:
    ```powershell
    .\mvnw.cmd -f chat-server/pom.xml clean test
    .\mvnw.cmd -f chat-client/pom.xml clean test
    # Hoặc chạy toàn bộ dự án từ thư mục gốc:
    .\mvnw.cmd clean test
    ```
  - **Trên Linux / WSL**:
    ```bash
    JAVA_HOME=/home/dunggawr/.vscode/extensions/redhat.java-1.58.0-linux-x64/jre/25.0.4.1-linux-x86_64 \
    /home/dunggawr/.m2/wrapper/dists/apache-maven-3.9.16/56ba1f9f/bin/mvn -f chat-server/pom.xml clean test

    JAVA_HOME=/home/dunggawr/.vscode/extensions/redhat.java-1.58.0-linux-x64/jre/25.0.4.1-linux-x86_64 \
    /home/dunggawr/.m2/wrapper/dists/apache-maven-3.9.16/56ba1f9f/bin/mvn -f chat-client/pom.xml clean test
    ```
- **Quy tắc Git**:
  - Viết commit message ngắn gọn theo chuẩn Conventional Commits (`feat(...)`, `fix(...)`, `refactor(...)`).
  - Luôn `git pull --rebase origin main` trước khi push và đồng bộ lên cả hai remote: `origin` (`anhtungit/LTM`) và `dunggawr` (`dunggawr/LTM`).


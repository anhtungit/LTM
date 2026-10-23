# Chat đa luồng — desktop demo

Prototype desktop Java Swing + FlatLaf theo giao diện Discord Dark đã duyệt. Giai đoạn hiện tại chỉ có đăng nhập demo cục bộ và chat trực tiếp trong bộ nhớ.

> **Bàn giao & trạng thái:** xem [docs/HANDOFF.md](docs/HANDOFF.md) — gồm thiết kế tham chiếu
> (canvas + node id từng màn), design tokens, kiến trúc, danh sách lỗi hiển thị đã sửa và backlog
> cho các giai đoạn sau. Đọc file này trước khi tiếp tục ở session mới.

## Chạy thử

Yêu cầu JDK 21.

### Linux / macOS (Bash)
```bash
cd chat-client
./mvnw clean package
java -jar target/chat-client-1.0-SNAPSHOT.jar
```

### Windows (PowerShell / CMD)
```powershell
cd chat-client
.\mvnw.cmd clean package
java -jar target\chat-client-1.0-SNAPSHOT.jar
```

### Chạy test tự động (JUnit 5)
```bash
./mvnw test          # Linux / macOS
.\mvnw.cmd test      # Windows
```

Đăng nhập bằng email hợp lệ bất kỳ (ví dụ `minh.nguyen@example.com`) và mật khẩu không rỗng bất kỳ.

## Kiểm tra giao diện (Offscreen Snapshot)

`src/test/java/vn/edu/ltm/chat/presentation/UiSnapshotTool.java` render trực tiếp `LoginPanel` và `ChatWorkspace` ra PNG trong `target/ui-snapshots/` để soát bố cục ở nhiều kích thước cửa sổ mà không cần mở app:

```bash
# Linux / macOS
./mvnw test-compile exec:java -Dexec.mainClass="vn.edu.ltm.chat.presentation.UiSnapshotTool" -Dexec.classpathScope="test"

# Windows
.\mvnw.cmd test-compile exec:java -Dexec.mainClass="vn.edu.ltm.chat.presentation.UiSnapshotTool" -Dexec.classpathScope="test"
```

Thêm `-Dexec.args="target/ui-snapshots debug"` để in chi tiết cây component kèm kích thước từng panel.

## Tính năng đã hoàn thiện

- **10/10 màn hình theo mẫu Kombai**: Đăng nhập, Chat nhóm, Chat riêng, Sửa/thu hồi/xóa tin, Tạo nhóm, Hộp thư thông báo, Kênh gặp (Sảnh + Trong phòng meeting chia sẻ màn hình), Hồ sơ người dùng & cài đặt, Gửi tệp & trạng thái lỗi, Khu vực trò chơi.
- **Emoji & Sticker Discord**: Tích hợp 182 icon Twemoji full màu offline vào khung soạn thảo và tin nhắn chat, không bị lỗi font hay ô chữ nhật `[][]`. 16 nhãn dán Sticker hiển thị thẻ card đồ họa.
- **Chỉnh sửa hồ sơ (Canvas 07)**: Đổi ảnh đại diện từ file máy tính, sửa display name, username `@...`, bio, chọn trạng thái (Online/Idle/DND/Invisible) với thẻ bản xem trước trực tiếp (Live Preview).
- **Phân tách kênh**: Từng kênh văn bản (`#chung`, `#học-tập`,...) có luồng tin riêng biệt. Hỗ trợ thêm/xóa kênh văn bản và kênh thoại linh hoạt.

## Giới hạn demo hiện tại

- Dữ liệu demo lưu trong bộ nhớ ram của phiên làm việc (`InMemoryChatGateway`).
- `ChatGateway` là ranh giới sẵn sàng để nối với server socket TCP đa luồng ở giai đoạn kế tiếp.

## Tài nguyên bên thứ ba

Xem [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md). Font Nunito Sans có giấy phép OFL đi kèm trong `src/main/resources/licenses/`.

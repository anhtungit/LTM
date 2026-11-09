# Tính năng: Xem YouTube Cùng nhau (Watch Together)

Tài liệu này giải thích chi tiết cấu trúc, luồng hoạt động, vị trí mã nguồn và ý nghĩa của từng biến trong tính năng xem YouTube đồng bộ giữa các thành viên trong cuộc gọi.

---

## 1. Vị trí Mã nguồn (File Locations)

- **Máy chủ HTTP cục bộ & Logic đồng bộ**:
  [`chat-client/src/main/java/vn/edu/ltm/chat/media/WatchTogether.java`](file:///home/dunggawr/Documents/ltm/LTM/chat-client/src/main/java/vn/edu/ltm/chat/media/WatchTogether.java)
- **Kích hoạt & Điều phối giao diện**:
  [`chat-client/src/main/java/vn/edu/ltm/chat/presentation/ChatWorkspace.java` (hàm `openWatchTogether`, `handleWatchSignal`)](file:///home/dunggawr/Documents/ltm/LTM/chat-client/src/main/java/vn/edu/ltm/chat/presentation/ChatWorkspace.java#L532-L610)

---

## 2. Luồng Hoạt động (Data Flow)

```
[Bấm "Xem YouTube"] ──> w.startSession() ──> HttpServer mở tại 127.0.0.1:port/token/
                                  │
                                  ├──> Mở thẳng trình duyệt hệ thống (Chrome/Edge/Firefox)
                                  │
                                  └──> Phát tín hiệu ACTION_STATE qua mạng TCP
                                                 │
                                                 ▼
[Đối phương nhận ACTION_STATE] ──> Mở thẳng trình duyệt trên máy đối phương
                                                 │
                                                 ▼
Trình duyệt web gọi API nội bộ (/state, /add, /event, /everyone, /stop)
YouTube IFrame API chạy trực tiếp với phần cứng đầy đủ của trình duyệt
                                                 │
                                                 ▼
Khi Host tua/tạm dừng/đổi bài ──> Gửi sự kiện lên máy chủ nội bộ
                                                 │
                                                 ▼
Tự động phát tín hiệu ACTION_STATE tới các máy khác để giữ đồng bộ thời gian thực
```

---

## 3. Các Cơ chế Bảo vệ Đồng bộ

1. **Phân quyền Chủ phòng (Host-only Mode)**:
   - Khi chủ phòng bật chế độ `"Chỉ bạn điều khiển"`, một lớp chắn trong suốt (`#controlShield`) bao phủ trình phát YouTube trên máy người xem. Người xem không thể bấm vào video hay phím `Space`/`K` để tạm dừng hoặc tua lén.
   - Ở tầng backend, logic `canControl()` kiểm tra điều kiện `everyoneControls || selfId.equals(hostId)`, từ chối mọi yêu cầu thay đổi từ người không phải host.
2. **Bộ hẹn giờ chống ngộ nhận (Guard Timer - `applying`)**:
   - Khi đổi bài hoặc tua, YouTube iframe phát sinh sự kiện chuyển trạng thái nội bộ (ví dụ `state 2` - Paused trước khi phát).
   - Biến `applying = Date.now() + 2000` đặt một khoảng hoãn 2 giây, bỏ qua tất cả sự kiện tạm dừng nội bộ của iframe, triệt tiêu lỗi ngộ nhận người dùng vừa bấm Pause làm kẹt video đối phương.

---

## 4. Chi tiết và Ý nghĩa từng Biến trong `WatchTogether.java`

| Tên biến | Kiểu dữ liệu | Ý nghĩa & Cơ chế bảo vệ |
| :--- | :--- | :--- |
| `queue` | `List<String>` | Danh sách các mã video YouTube (Video IDs) đang có trong danh sách phát chung. |
| `index` | `int` | Chỉ số video hiện tại đang được phát (`-1` nếu danh sách trống). |
| `playing` | `boolean` | Trạng thái video: `true` là đang phát, `false` là đang tạm dừng. |
| `pos` | `double` | Thời gian phát (giây) tại thời điểm lưu mốc `stampMs`. |
| `stampMs` | `long` | Dấu thời gian hệ thống (timestamp) tính bằng miligiây tại thời điểm cập nhật `pos`. Giúp tính toán vị trí thực tế `effectivePos = pos + (now - stampMs) / 1000`. |
| `everyoneControls` | `boolean` | Cờ chế độ điều khiển: `true` = Mọi người cùng điều khiển; `false` = Chỉ chủ phòng (Host) mới có quyền đổi bài, tua hoặc dừng. |
| `hostId` | `String` | ID của người tạo phòng hoặc nắm giữ quyền điều khiển. |
| `seq` | `long` | Số thứ tự phiên bản trạng thái (Sequence number), tăng lên sau mỗi thao tác để trình duyệt nhận biết có dữ liệu mới. |
| `applying` | `long (trong JS)` | Bộ đếm thời gian hoãn (Guard Timer) trong JavaScript (2000ms) để không bị nhầm trạng thái đệm video của YouTube thành thao tác dừng video của người dùng. |

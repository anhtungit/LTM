# Tính năng: Chia sẻ Màn hình & Cửa sổ (Screen & Window Sharing)

Tài liệu này giải thích chi tiết cấu trúc, luồng hoạt động, vị trí mã nguồn và ý nghĩa của từng biến trong tính năng chia sẻ màn hình máy tính và cửa sổ ứng dụng trong LTM.

---

## 1. Vị trí Mã nguồn (File Locations)

- **Engine chụp màn hình & cửa sổ ứng dụng**:
  [`chat-client/src/main/java/vn/edu/ltm/chat/media/ScreenCaptureEngine.java`](file:///home/dunggawr/Documents/ltm/LTM/chat-client/src/main/java/vn/edu/ltm/chat/media/ScreenCaptureEngine.java)
- **Điều khiển tín hiệu chia sẻ**:
  [`chat-client/src/main/java/vn/edu/ltm/chat/presentation/ChatWorkspace.java` (dòng 626 - 685, 817 - 860)](file:///home/dunggawr/Documents/ltm/LTM/chat-client/src/main/java/vn/edu/ltm/chat/presentation/ChatWorkspace.java#L626-L685)
- **Giao diện trình chiếu**:
  [`chat-client/src/main/java/vn/edu/ltm/chat/presentation/MeetRoomPanel.java` (hàm `updateScreenShare`)](file:///home/dunggawr/Documents/ltm/LTM/chat-client/src/main/java/vn/edu/ltm/chat/presentation/MeetRoomPanel.java)

---

## 2. Luồng Hoạt động (Data Flow)

```
[Thao tác người dùng: Bấm "Chia sẻ màn hình"]
                     │
     ┌───────────────┴───────────────┐
     ▼                               ▼
(Linux Wayland)            (Windows / macOS / X11)
XDG Desktop Portal ScreenCast       java.awt.Robot
     │                               │
     ▼                               ▼
PipeWire + GStreamer            createScreenCapture()
(Chọn toàn màn hình             (Chụp Rectangle màn hình)
 hoặc 1 cửa sổ ứng dụng)
     │                               │
     └───────────────┬───────────────┘
                     ▼
         Nén ảnh JPEG (Chất lượng 0.55, 30fps)
                     │
      Kiểm tra screenSending (AtomicBoolean)
      [Nếu đang bận gửi frame trước thì DROP frame này]
                     │
                     ▼
      Base64 -> ChatService.sendCallSignal("SCREEN", b64)
                     │
            [Server chuyển tiếp]
                     │
                     ▼
      Đối phương nhận tín hiệu "SCREEN":
      Kiểm tra peerScreenDrawing (AtomicBoolean)
      [Nếu EDT đang bận vẽ frame trước thì BỎ QUA frame này]
                     │
                     ▼
      Giải mã JPEG trong CompletableFuture (Luồng nền)
                     │
                     ▼
      SwingUtilities.invokeLater() -> MeetRoomPanel hiển thị
```

---

## 3. Các Điểm Kỹ thuật Đột phá

1. **Hỗ trợ Wayland không bị đen màn hình**:
   - Trên Linux Wayland (Ubuntu, Fedora), cơ chế bảo mật cô lập ngăn cản `Robot.createScreenCapture` đọc pixel từ các ứng dụng khác, dẫn đến màn hình bị đen.
   - `ScreenCaptureEngine` sử dụng giao thức **XDG Desktop Portal (org.freedesktop.portal.ScreenCast)** kết hợp **PipeWire / GStreamer**. Hệ điều hành sẽ hiển thị popup native cho phép người dùng chọn chia sẻ Toàn màn hình hoặc 1 Cửa sổ ứng dụng cụ thể (chuẩn giống Discord/Chrome).
2. **Kỹ thuật Triệt tiêu Độ trễ (Non-blocking Backpressure)**:
   - Dùng `screenSending` (`AtomicBoolean`) ở phía người phát: Chỉ gửi gói tin qua mạng khi gói trước đã hoàn tất.
   - Dùng `peerScreenDrawing` (`AtomicBoolean`) ở phía người nhận: Nếu CPU hoặc giao diện Swing đang bận vẽ frame cũ, frame mới lập tức bị bỏ qua (frame-dropping) thay vì xếp hàng trong bộ nhớ đệm, giúp hình ảnh luôn là **thời gian thực tuyệt đối** (không bị trôi hình 5-10 giây sau khi mạng lag).

---

## 4. Chi tiết và Ý nghĩa từng Biến trong `ScreenCaptureEngine.java`

| Tên biến | Kiểu dữ liệu | Ý nghĩa & Mục đích |
| :--- | :--- | :--- |
| `SOURCE_SCREEN` | `int (1)` | Hằng số đại diện cho chế độ chia sẻ: Toàn bộ màn hình hiển thị. |
| `SOURCE_WINDOW` | `int (2)` | Hằng số đại diện cho chế độ chia sẻ: Một cửa sổ ứng dụng cụ thể. |
| `running` | `volatile boolean` | Cờ trạng thái engine đang hoạt động hay đã kết thúc. |
| `captureThread` | `Thread` | Luồng nền chạy vòng lặp chụp và nén frame màn hình. |
| `portalProcess` | `volatile Process` | Tiến trình liên lạc với D-Bus Portal và PipeWire gstreamer pipeline trên Linux. |
| `sourceType` | `int` | Loại nguồn chia sẻ hiện tại (`SOURCE_SCREEN` hoặc `SOURCE_WINDOW`). |
| `targetFps` | `int` | Tần số khung hình mục tiêu (mặc định 30 FPS). |
| `maxWidth` / `maxHeight` | `int` | Độ phân giải tối đa cho phép (chuẩn HD 1280x720 hoặc Full HD 1920x1080). |
| `jpegQuality` | `float (0.55f)` | Mức nén chất lượng ảnh JPEG từ 0.0 đến 1.0 (0.55 là tỷ lệ tối ưu cho đường truyền mạng và độ nét chữ viết). |
| `localPreviewDecoding` | `AtomicBoolean` | Đảm bảo khung xem trước của chính mình chỉ giải mã khi vòng lặp trước đã xong, tránh nghẽn CPU nội bộ. |

# Tính năng: Bật / Tắt Camera (Webcam Video Capture)

Tài liệu này giải thích chi tiết cấu trúc, luồng hoạt động, vị trí mã nguồn và ý nghĩa của từng biến trong tính năng thu hình camera trong ứng dụng LTM.

---

## 1. Vị trí Mã nguồn (File Locations)

- **Engine thu hình trực tiếp từ phần cứng**:
  [`chat-client/src/main/java/vn/edu/ltm/chat/media/VideoCaptureEngine.java`](file:///home/dunggawr/Documents/ltm/LTM/chat-client/src/main/java/vn/edu/ltm/chat/media/VideoCaptureEngine.java)
- **Điều phối phát & nhận khung hình qua mạng**:
  [`chat-client/src/main/java/vn/edu/ltm/chat/presentation/ChatWorkspace.java` (dòng 403 - 435, 767 - 815)](file:///home/dunggawr/Documents/ltm/LTM/chat-client/src/main/java/vn/edu/ltm/chat/presentation/ChatWorkspace.java#L403-L435)
- **Hiển thị giao diện người tham gia cuộc gọi**:
  [`chat-client/src/main/java/vn/edu/ltm/chat/presentation/MeetRoomPanel.java` (hàm `updateParticipantVideo`)](file:///home/dunggawr/Documents/ltm/LTM/chat-client/src/main/java/vn/edu/ltm/chat/presentation/MeetRoomPanel.java)

---

## 2. Luồng Hoạt động (Data Flow)

```
[Webcam vật lý]
      │
      ▼
VideoCaptureEngine (DirectShow / V4L2 / FFmpeg)
      │
      ├──> onLocalPreview (BufferedImage) ──> MeetRoomPanel.updateParticipantVideo (Hiển thị ngay cho bản thân)
      │
      └──> onJpegFrame (byte[]) ──> Base64 Encoding
                                           │
                                           ▼
                                 ChatService.sendCallSignal("VIDEO", base64)
                                           │ (TCP Gói tin JSON)
                                           ▼
                                  [Chat Server TCP] ──> Chuyển tiếp tới thành viên phòng
                                           │
                                           ▼
                                 Client đối phương nhận "VIDEO"
                                           │
                                 Base64.decode() ──> ImageIO.read()
                                           │
                                 MeetRoomPanel.updateParticipantVideo (Vẽ lên lưới ô của người đó)
```

1. **Khởi động**: Khi người dùng nhấn nút Camera trong cuộc gọi, `ChatWorkspace.startLocalCamera()` được kích hoạt.
2. **Gửi tín hiệu báo hiệu**: Gửi tín hiệu `CAM_ON` tới các thành viên trong phòng để họ chuẩn bị hiển thị video tile.
3. **Chụp hình đa nền tảng**:
   - Trên **Windows**: Dùng thư viện `Sarxos Webcam Capture` thông qua `DirectShow Java Native Wrapper`.
   - Trên **Linux**: Quét trực tiếp danh sách node video trong `/sys/class/video4linux/`. Nếu có FFmpeg thì dùng lệnh nền v4l2 capture với định dạng chuẩn mjpeg.
4. **Nén & Stream**: Khung hình được chụp liên tục ở tốc độ 30 FPS với kích thước chuẩn 1280x720 (HD), nén định dạng JPEG (chất lượng cao nhưng kích thước chỉ ~25-35KB).
5. **Chống trễ (Backpressure)**: Nếu khung hình trước đó đối phương chưa kịp giải mã hoặc render trên EDT, khung hình tiếp theo sẽ ghi đè lên frame cũ để đảm bảo không bị tích lũy độ trễ (Zero-latency playback).
6. **Tắt camera**: Gọi `videoEngine.stop()`, xóa hình đại diện video và gửi gói `CAM_OFF`. Bên đối phương có bộ hẹn giờ dự phòng (`peerVideoTimers`) 1.5 giây; nếu mất tín hiệu camera mà không kịp nhận `CAM_OFF`, hệ thống tự chuyển về Avatar chữ cái để tránh đóng băng hình cũ.

---

## 3. Chi tiết và Ý nghĩa từng Biến trong `VideoCaptureEngine.java`

| Tên biến | Kiểu dữ liệu | Ý nghĩa & Mục đích |
| :--- | :--- | :--- |
| `WIDTH` | `int (1280)` | Chiều rộng chuẩn của khung hình thu từ webcam (HD 720p). |
| `HEIGHT` | `int (720)` | Chiều cao chuẩn của khung hình thu từ webcam (HD 720p). |
| `FPS` | `int (30)` | Tần số lấy mẫu khung hình mỗi giây (30 khung hình/giây). |
| `FRAME_INTERVAL_MS` | `int (1000/30 ≈ 33)` | Khoảng thời gian ngủ giữa 2 lần lấy khung hình liên tiếp để duy trì tốc độ 30fps đều đặn. |
| `SHARED_FRAME_PATH` | `Path` | Đường dẫn file tạm (`ltm_shared_webcam.jpg`) để chia sẻ khung hình giữa các tiến trình chạy thử nghiệm cục bộ khi webcam bị chiếm dụng. |
| `selectedCameraId` | `String` | ID thiết bị webcam mà người dùng chọn (mặc định là `"default"` hoặc `/dev/video0`). |
| `running` | `volatile boolean` | Cờ trạng thái cho biết luồng thu hình camera đang chạy hay đã dừng. Dùng `volatile` để đồng bộ giữa các luồng. |
| `activeWebcam` | `volatile Webcam` | Thể hiện đối tượng camera của thư viện Sarxos đang mở. |
| `captureProcess` | `volatile Process` | Tiến trình con của FFmpeg nếu chạy ở chế độ fallback thu hình trực tiếp từ v4l2. |
| `captureThread` | `Thread` | Luồng nền (background thread) chuyên biệt chạy vòng lặp đọc dữ liệu ảnh, tránh làm treo luồng giao diện Swing (EDT). |

# Tài liệu Kỹ thuật: Hệ thống Đa phương tiện & Cuộc gọi (Media & Calling Architecture)

Tài liệu này tổng hợp toàn bộ cấu trúc kiến trúc, cơ chế hoạt động thời gian thực và liên kết tới các tài liệu chi tiết cho từng tính năng đa phương tiện trong dự án LTM.

---

## 1. Mục lục Tài liệu Chi tiết theo Tính năng

Tất cả tài liệu chi tiết cho từng tính năng đã được tách thành các file riêng biệt trong thư mục [`docs/`](file:///home/dunggawr/Documents/ltm/LTM/docs):

1. 📷 **[Tính năng 1: Bật / Tắt Camera (Webcam Video Capture)](file:///home/dunggawr/Documents/ltm/LTM/docs/01_CAMERA_VIDEO_CAPTURE.md)**
   - Cơ chế thu hình DirectShow / V4L2 / FFmpeg.
   - Chuẩn nén MJPEG HD 1280x720 @ 30 FPS.
   - Ý nghĩa từng biến trong `VideoCaptureEngine.java`.
2. 🖥️ **[Tính năng 2: Chia sẻ Màn hình & Cửa sổ (Screen & Window Sharing)](file:///home/dunggawr/Documents/ltm/LTM/docs/02_SCREEN_WINDOW_SHARING.md)**
   - Cơ chế XDG Desktop Portal ScreenCast + PipeWire trên Wayland (chống màn hình đen, chọn cửa sổ app cụ thể).
   - Cơ chế `java.awt.Robot` trên Windows/macOS/X11.
   - Kỹ thuật triệt tiêu độ trễ (Non-blocking backpressure drop-frame).
   - Ý nghĩa từng biến trong `ScreenCaptureEngine.java`.
3. 🎙️ **[Tính năng 3: Đàm thoại Giọng nói (Real-time Voice & Audio Engine)](file:///home/dunggawr/Documents/ltm/LTM/docs/03_VOICE_AUDIO_ENGINE.md)**
   - Luồng âm thanh 16kHz 16-bit Mono PCM.
   - Thuật toán VAD (phát hiện giọng nói) và Adaptive Jitter Buffer bù trễ.
   - Ý nghĩa từng biến trong `VoiceAudioEngine.java`.
4. 🎬 **[Tính năng 4: Xem YouTube Cùng nhau (Watch Together)](file:///home/dunggawr/Documents/ltm/LTM/docs/04_WATCH_TOGETHER.md)**
   - Máy chủ HTTP cục bộ tích hợp YouTube IFrame API chạy trực tiếp trên trình duyệt web.
   - Phân quyền Host-only với lớp chắn bảo vệ (`#controlShield`).
   - Bộ đệm hoãn `applying` (Guard Timer 2s) chống ngộ nhận sự kiện Pause.
   - Ý nghĩa từng biến trong `WatchTogether.java`.
5. 📡 **[Tính năng 5: Giao thức Báo hiệu & Phân luồng Mạng (Call Signaling Protocol)](file:///home/dunggawr/Documents/ltm/LTM/docs/05_CALL_SIGNALING_PROTOCOL.md)**
   - Cấu trúc gói tin JSON báo hiệu `CALL_SIGNAL_REQ` và `CALL_SIGNAL_BROADCAST`.
   - Danh mục mã hành động (`action`) và cơ chế định tuyến an toàn trên server.

---

## 2. Bản đồ Mã nguồn (Source Code Locations)

| Tên File | Đường dẫn | Trách nhiệm chính |
| :--- | :--- | :--- |
| **`VideoCaptureEngine.java`** | `chat-client/src/main/java/vn/edu/ltm/chat/media/VideoCaptureEngine.java` | Thu hình từ Webcam thiết bị thật (DirectShow/V4L2) hoặc qua FFmpeg, trích xuất ảnh frame JPEG. |
| **`ScreenCaptureEngine.java`** | `chat-client/src/main/java/vn/edu/ltm/chat/media/ScreenCaptureEngine.java` | Chụp màn hình máy tính / cửa sổ ứng dụng (Wayland Portal / PipeWire / Robot), nén JPEG thời gian thực. |
| **`VoiceAudioEngine.java`** | `chat-client/src/main/java/vn/edu/ltm/chat/media/VoiceAudioEngine.java` | Thu âm Micro, xử lý VAD (phát hiện giọng nói), bộ đệm Jitter Buffer và phát ra Loa. |
| **`WatchTogether.java`** | `chat-client/src/main/java/vn/edu/ltm/chat/media/WatchTogether.java` | Máy chủ HTTP cục bộ, quản lý danh sách phát và đồng bộ tiến độ YouTube. |
| **`ChatWorkspace.java`** | `chat-client/src/main/java/vn/edu/ltm/chat/presentation/ChatWorkspace.java` | Điều phối tín hiệu báo hiệu cuộc gọi (`CALL_SIGNAL`), kết nối UI Swing và các Media Engines. |
| **`MeetRoomPanel.java`** | `chat-client/src/main/java/vn/edu/ltm/chat/presentation/MeetRoomPanel.java` | Giao diện Swing phòng họp: lưới video người tham gia (Video Grid), khung trình chiếu màn hình, thanh điều khiển cuộc gọi. |

# Tài liệu Kỹ thuật: Hệ thống Đa phương tiện & Cuộc gọi (Media & Calling Architecture)

Tài liệu này giải thích chi tiết cấu trúc, luồng hoạt động, vị trí mã nguồn và ý nghĩa của từng biến trong hệ thống Gọi thoại (Voice), Gọi video (Camera), Chia sẻ màn hình (Screen Share), và Xem YouTube cùng nhau (Watch Together) trong dự án LTM.

---

## 1. Bản đồ Mã nguồn (File Locations)

| Tên File | Đường dẫn | Trách nhiệm chính |
| :--- | :--- | :--- |
| **`VideoCaptureEngine.java`** | `chat-client/src/main/java/vn/edu/ltm/chat/media/VideoCaptureEngine.java` | Thu hình từ Webcam thiết bị thật (DirectShow/V4L2) hoặc qua FFmpeg, trích xuất ảnh frame JPEG. |
| **`ScreenCaptureEngine.java`** | `chat-client/src/main/java/vn/edu/ltm/chat/media/ScreenCaptureEngine.java` | Chụp màn hình máy tính / cửa sổ ứng dụng (Wayland Portal / PipeWire / Robot), nén JPEG thời gian thực. |
| **`VoiceAudioEngine.java`** | `chat-client/src/main/java/vn/edu/ltm/chat/media/VoiceAudioEngine.java` | Thu âm Micro, xử lý VAD (phát hiện giọng nói), bộ đệm Jitter Buffer và phát ra Loa. |
| **`WatchTogether.java`** | `chat-client/src/main/java/vn/edu/ltm/chat/media/WatchTogether.java` | Máy chủ HTTP cục bộ, quản lý danh sách phát và đồng bộ tiến độ YouTube. |
| **`ChatWorkspace.java`** | `chat-client/src/main/java/vn/edu/ltm/chat/presentation/ChatWorkspace.java` | Điều phối tín hiệu báo hiệu cuộc gọi (`CALL_SIGNAL`), kết nối UI Swing và các Media Engines. |
| **`MeetRoomPanel.java`** | `chat-client/src/main/java/vn/edu/ltm/chat/presentation/MeetRoomPanel.java` | Giao diện Swing phòng họp: lưới video người tham gia (Video Grid), khung trình chiếu màn hình, thanh điều khiển cuộc gọi. |

---

## 2. Tính năng: Bật / Tắt Camera (Webcam Video Capture)

### 2.1. Vị trí Code
- **Engine thu hình**: [`VideoCaptureEngine.java`](file:///home/dunggawr/Documents/ltm/LTM/chat-client/src/main/java/vn/edu/ltm/chat/media/VideoCaptureEngine.java)
- **Điều phối phát & nhận khung hình**: [`ChatWorkspace.java` (dòng 403 - 435, 767 - 815)](file:///home/dunggawr/Documents/ltm/LTM/chat-client/src/main/java/vn/edu/ltm/chat/presentation/ChatWorkspace.java#L403-L435)
- **Hiển thị giao diện**: [`MeetRoomPanel.java` (hàm `updateParticipantVideo`)](file:///home/dunggawr/Documents/ltm/LTM/chat-client/src/main/java/vn/edu/ltm/chat/presentation/MeetRoomPanel.java)

### 2.2. Cơ chế Hoạt động (Flow)
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

### 2.3. Chi tiết và Ý nghĩa từng biến trong `VideoCaptureEngine.java`

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

---

## 3. Tính năng: Chia sẻ Màn hình (Screen & Window Sharing)

### 3.1. Vị trí Code
- **Engine chụp màn hình**: [`ScreenCaptureEngine.java`](file:///home/dunggawr/Documents/ltm/LTM/chat-client/src/main/java/vn/edu/ltm/chat/media/ScreenCaptureEngine.java)
- **Điều khiển tín hiệu chia sẻ**: [`ChatWorkspace.java` (dòng 626 - 685, 817 - 860)](file:///home/dunggawr/Documents/ltm/LTM/chat-client/src/main/java/vn/edu/ltm/chat/presentation/ChatWorkspace.java#L626-L685)
- **Giao diện trình chiếu**: [`MeetRoomPanel.java` (hàm `updateScreenShare`)](file:///home/dunggawr/Documents/ltm/LTM/chat-client/src/main/java/vn/edu/ltm/chat/presentation/MeetRoomPanel.java)

### 3.2. Cơ chế Hoạt động (Flow)
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
      [Nếu đang gửi frame trước thì DROP frame này]
                     │
                     ▼
      Base64 -> ChatService.sendCallSignal("SCREEN", b64)
                     │
            [Server chuyển tiếp]
                     │
                     ▼
      Đối phương nhận tín hiệu "SCREEN":
      Kiểm tra peerScreenDrawing (AtomicBoolean)
      [Nếu EDT đang vẽ frame trước thì BỎ QUA frame này]
                     │
                     ▼
      Giải mã JPEG trong CompletableFuture (Luồng nền)
                     │
                     ▼
      SwingUtilities.invokeLater() -> MeetRoomPanel hiển thị
```

### 3.3. Các điểm Kỹ thuật Đột phá
1. **Hỗ trợ Wayland không bị đen màn hình**: Trên các bản Linux hiện đại (Ubuntu, Fedora) dùng Wayland, lệnh chụp truyền thống của Java (`Robot.createScreenCapture`) sẽ chỉ ra màn hình đen vì cơ chế bảo mật của Wayland. `ScreenCaptureEngine` tích hợp **XDG Desktop Portal ScreenCast** kết hợp **PipeWire / GStreamer** mở hộp thoại chọn màn hình/cửa sổ chuẩn hệ thống (giống hệt Discord và Chrome).
2. **Kỹ thuật Triệt tiêu Độ trễ (Non-blocking Backpressure)**:
   - Dùng `screenSending` (`AtomicBoolean`) ở phía người phát: Chỉ gửi gói tin qua mạng khi gói trước đã gửi xong.
   - Dùng `peerScreenDrawing` (`AtomicBoolean`) ở phía người nhận: Nếu CPU hoặc giao diện đang bận vẽ frame cũ, frame mới lập tức bị bỏ qua (frame-dropping) thay vì xếp hàng trong bộ nhớ đệm, giúp hình ảnh luôn là **thời gian thực tuyệt đối** (không bị trôi hình 5-10 giây sau khi mạng lag).

### 3.4. Chi tiết và Ý nghĩa từng biến trong `ScreenCaptureEngine.java`

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

---

## 4. Tính năng: Đàm thoại Giọng nói (Real-time Voice & Audio Engine)

### 4.1. Vị trí Code
- [`VoiceAudioEngine.java`](file:///home/dunggawr/Documents/ltm/LTM/chat-client/src/main/java/vn/edu/ltm/chat/media/VoiceAudioEngine.java)
- Điều phối gói âm thanh: [`ChatWorkspace.java` (hàm `handlePeerAudioReceived`, dòng 759 - 765)](file:///home/dunggawr/Documents/ltm/LTM/chat-client/src/main/java/vn/edu/ltm/chat/presentation/ChatWorkspace.java#L759-L765)

### 4.2. Cơ chế Hoạt động & Ý nghĩa Biến
Hệ thống sử dụng luồng âm thanh PCM không nén định dạng 16kHz, 16-bit Mono trực tiếp qua Java Sound API (`javax.sound.sampled`):

| Tên biến | Kiểu dữ liệu | Ý nghĩa & Tác dụng kỹ thuật |
| :--- | :--- | :--- |
| `FORMAT` | `AudioFormat` | Định dạng chuẩn: `SampleRate = 16000Hz`, `SampleSizeInBits = 16`, `Channels = 1` (Mono), `Signed = true`, `BigEndian = false`. Tần số 16kHz mang lại giọng nói trong trẻo (chuẩn Wideband Voice) mà dung lượng vừa phải (~32KB/giây). |
| `CHUNK_BYTES` | `int (1280)` | Dung lượng của 1 gói âm thanh: tương ứng với **40 miligiây** âm thanh (`16000 * 2 byte * 0.04s = 1280 byte`). Cứ 40ms sẽ có 1 gói gửi đi (25 gói/giây). |
| `SPEAKER_BUFFER_BYTES` | `int (5120)` | Bộ đệm phần cứng của loa (bằng 4 lần `CHUNK_BYTES` = 160ms) để việc phát âm thanh không bị giật rè khi xung đồng hồ phần cứng chênh lệch. |
| `MIC_BUFFER_BYTES` | `int (5120)` | Bộ đệm phần cứng của Micro để không bị mất mẫu âm khi CPU bận. |
| `JITTER_QUEUE_CAPACITY` | `int (20)` | Độ dài tối đa của hàng đợi bù trễ (Jitter Buffer = 20 khung x 40ms = 800ms) chống hiện tượng gói tin đến không đều do mạng chập chờn. |
| `PREBUFFER_FRAMES` | `int (3)` | Bộ đệm trước khi bắt đầu nói: đợi gom đủ 3 khung (120ms) mới bắt đầu phát để âm thanh mượt mà liên tục. |
| `SILENCE_FRAME` | `byte[]` | Mảng byte chứa toàn giá trị 0 (khung im lặng) dùng để bù vào loa khi mạng bị mất gói. |
| `noiseSuppressionEnabled` | `boolean` | Cờ bật/tắt tính năng lọc tạp âm thông minh qua thuật toán phát hiện giọng nói (VAD). |
| `micVolume` | `double` | Hệ số khuếch đại âm lượng micro (từ 0.0 đến 1.5). |

---

## 5. Tính năng: Xem YouTube Cùng nhau (Watch Together)

### 5.1. Vị trí Code
- **Máy chủ & Đồng bộ**: [`WatchTogether.java`](file:///home/dunggawr/Documents/ltm/LTM/chat-client/src/main/java/vn/edu/ltm/chat/media/WatchTogether.java)
- **Kích hoạt & Điều phối**: [`ChatWorkspace.java` (hàm `openWatchTogether`, `handleWatchSignal`)](file:///home/dunggawr/Documents/ltm/LTM/chat-client/src/main/java/vn/edu/ltm/chat/presentation/ChatWorkspace.java#L532-L610)

### 5.2. Cơ chế Hoạt động & Ý nghĩa Biến
Một `HttpServer` ảo siêu nhẹ được mở trên cổng ngẫu nhiên ở địa chỉ cục bộ `127.0.0.1`. Khi xem chung:
1. Ứng dụng mở thẳng URL trên trình duyệt web mặc định của hệ thống.
2. Trình duyệt web giao tiếp với máy chủ Java qua API nội bộ (`/state`, `/add`, `/event`, `/everyone`, `/stop`).
3. Khi có thay đổi (chọn bài, tạm dừng, tua), trạng thái được mã hóa thành chuỗi báo hiệu `ACTION_STATE` và gửi qua mạng TCP cho tất cả người trong cuộc gọi.

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
| `applying` | `long (trong JS)` | Bộ đếm thời gian hoãn (Guard Timer) trong JavaScript: khi đổi bài hoặc tua, hệ thống đặt `applying = Date.now() + 2000ms`. Trong 2 giây này, các sự kiện tạm dừng do YouTube tải trước (state 2) bị bỏ qua, tránh việc ngộ nhận người dùng vừa bấm Pause làm kẹt video đối phương. |

---

## 6. Giao thức Báo hiệu Cuộc gọi (Signaling Protocol)

Mọi tương tác thời gian thực trong phòng gọi đều được đóng gói dưới dạng JSON 1 dòng gửi qua kết nối TCP:

```json
{
  "type": "CALL_SIGNAL_REQ",
  "requestId": "uuid",
  "conversationId": "id_cuoc_tro_chuyen",
  "channel": "voice",
  "action": "TÊN_LỆNH",
  "data": "DỮ_LIỆU_HOẶC_BASE64"
}
```

### Bảng tóm tắt các Lệnh (`action`):
- `INVITE` / `ACCEPT` / `REJECT` / `LEAVE`: Quản lý vòng đời cuộc gọi 1-1 và phòng nhóm.
- `AUDIO`: Chứa dữ liệu âm thanh giọng nói (Base64 của 1280 byte PCM).
- `VIDEO`: Chứa khung hình Camera (Base64 của ảnh JPEG).
- `CAM_ON` / `CAM_OFF`: Báo hiệu trạng thái bật/tắt camera.
- `SCREEN`: Chứa khung hình chia sẻ màn hình (Base64 ảnh JPEG).
- `SCREEN_OFF`: Báo hiệu kết thúc chia sẻ màn hình.
- `YT_REQ` / `YT_STATE` / `YT_STOP`: Yêu cầu trạng thái, đồng bộ tiến độ và kết thúc phiên xem YouTube cùng nhau.

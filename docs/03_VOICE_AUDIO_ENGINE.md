# Tính năng: Đàm thoại Giọng nói (Real-time Voice & Audio Engine)

Tài liệu này giải thích chi tiết cấu trúc, luồng hoạt động, vị trí mã nguồn và ý nghĩa của từng biến trong hệ thống đàm thoại giọng nói trong LTM.

---

## 1. Vị trí Mã nguồn (File Locations)

- **Engine âm thanh (Micro, Loa, Bộ đệm, VAD)**:
  [`chat-client/src/main/java/vn/edu/ltm/chat/media/VoiceAudioEngine.java`](file:///home/dunggawr/Documents/ltm/LTM/chat-client/src/main/java/vn/edu/ltm/chat/media/VoiceAudioEngine.java)
- **Điều phối gói âm thanh**:
  [`chat-client/src/main/java/vn/edu/ltm/chat/presentation/ChatWorkspace.java` (hàm `handlePeerAudioReceived`, dòng 759 - 765)](file:///home/dunggawr/Documents/ltm/LTM/chat-client/src/main/java/vn/edu/ltm/chat/presentation/ChatWorkspace.java#L759-L765)

---

## 2. Luồng Hoạt động (Data Flow)

```
[Microphone thu âm]
        │
        ▼
TargetDataLine (Java Sound API)
        │ Đọc từng khung 40ms (1280 bytes PCM 16kHz)
        ▼
Xử lý VAD & Lọc nhiễu (Noise Suppression)
        │
   [Có tiếng nói?]
   ├── KHÔNG: Bỏ qua (tiết kiệm băng thông mạng)
   └── CÓ:
        │
        ▼
   Áp dụng micVolume (Khuếch đại âm lượng)
        │
        ▼
   Base64 -> ChatService.sendCallSignal("AUDIO", b64)
        │
 [Server chuyển tiếp]
        │
        ▼
   Đối phương nhận "AUDIO":
   Base64.decode() -> VoiceAudioEngine.playChunk(senderId, data)
        │
        ▼
   Đưa vào Jitter Buffer (Hàng đợi bù trễ)
        │
   [Đủ 3 khung Pre-buffer (120ms)?]
        │
        ▼
   SourceDataLine (Phát ra Loa / Tai nghe)
```

---

## 3. Chi tiết và Ý nghĩa từng Biến trong `VoiceAudioEngine.java`

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

# Giao thức Báo hiệu & Phân luồng Mạng (Call Signaling Protocol)

Tài liệu này giải thích chi tiết cấu trúc gói tin, giao thức báo hiệu cuộc gọi và cơ chế phân phối tín hiệu thời gian thực trong LTM.

---

## 1. Cấu trúc Gói tin Báo hiệu (Packet Format)

Mọi tương tác trong cuộc gọi (kết nối, ngắt kết nối, gửi khung hình, âm thanh, xem YouTube) đều được đóng gói dưới dạng JSON một dòng gửi qua luồng TCP:

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

Máy chủ TCP (`chat-server`) tiếp nhận `CALL_SIGNAL_REQ`, kiểm tra quyền thành viên trong hội thoại và phát tán tới các thành viên khác bằng gói:

```json
{
  "type": "CALL_SIGNAL_BROADCAST",
  "conversationId": "id_cuoc_tro_chuyen",
  "channel": "voice",
  "senderId": "id_nguoi_gui",
  "senderName": "ten_nguoi_gui",
  "action": "TÊN_LỆNH",
  "data": "DỮ_LIỆU_HOẶC_BASE64"
}
```

---

## 2. Bảng Danh mục Lệnh Báo hiệu (`action`)

| Tên Action | Kiểu Data | Ý nghĩa & Luồng xử lý |
| :--- | :--- | :--- |
| `INVITE` | Chuỗi rỗng hoặc JSON thông tin cuộc gọi | Người khởi tạo gửi lời mời gọi thoại / video tới người nhận hoặc nhóm. |
| `ACCEPT` | Chuỗi rỗng | Người nhận chấp nhận cuộc gọi; hai bên bắt đầu luồng audio/video. |
| `REJECT` | Chuỗi rỗng | Người nhận từ chối cuộc gọi; dừng chuông và hủy trạng thái chờ. |
| `LEAVE` | Chuỗi rỗng | Người dùng rời cuộc gọi; máy chủ cập nhật danh sách người tham gia (Voice Roster). |
| `AUDIO` | Chuỗi Base64 (1280 bytes PCM 16kHz) | Gói âm thanh giọng nói (40ms âm thanh). |
| `VIDEO` | Chuỗi Base64 (ảnh JPEG 1280x720) | Khung hình Webcam của người tham gia. |
| `CAM_ON` | Chuỗi rỗng | Báo hiệu người dùng vừa bật camera. |
| `CAM_OFF` | Chuỗi rỗng | Báo hiệu người dùng vừa tắt camera; bên nhận gỡ bỏ khung video cũ. |
| `SCREEN` | Chuỗi Base64 (ảnh JPEG màn hình) | Khung hình chia sẻ màn hình máy tính hoặc cửa sổ ứng dụng. |
| `SCREEN_OFF`| Chuỗi rỗng | Báo hiệu kết thúc phiên chia sẻ màn hình. |
| `YT_REQ` | Chuỗi rỗng | Yêu cầu máy chủ/host gửi lại trạng thái YouTube hiện tại (dùng khi mới vào phòng). |
| `YT_STATE` | Chuỗi trạng thái mã hóa (`q=...;i=...;p=...;t=...;e=...;h=...`) | Đồng bộ toàn bộ trạng thái danh sách phát, vị trí tua và quyền điều khiển YouTube. |
| `YT_STOP` | Chuỗi rỗng | Lệnh kết thúc phiên xem YouTube cùng nhau cho tất cả mọi người. |

---

## 3. Cơ chế Định tuyến trên Máy chủ (`ClientHandler.java`)

1. **Cuộc gọi 1-1 (Direct Message)**:
   - Gói tin gửi tới đúng phiên TCP của người nhận (`targetUser.id().equals(peerId)`).
   - Tuyệt đối không broadcast ra toàn bộ người dùng đang online trên server.
2. **Cuộc gọi Nhóm (Group Conversation)**:
   - Máy chủ kiểm tra `db.isUserMemberOfGroup(senderId, convId)`.
   - Chỉ phát tán tới các socket của thành viên nằm trong danh sách nhóm, cô lập hoàn toàn với các phòng chat khác.

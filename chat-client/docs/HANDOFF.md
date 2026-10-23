# Bàn giao: Chat đa luồng — desktop client (Java Swing + FlatLaf)

Tài liệu này ghi lại **trạng thái hiện tại, thiết kế tham chiếu và các quyết định kỹ thuật** để có thể
tiếp tục công việc ở session khác mà không cần dò lại code.

- Cập nhật lần cuối: 2026-10-23
- Sub-project: `chat-client/`
- Yêu cầu gốc: `BÁO CÁO THIẾT KẾ & KIẾN TRÚC HỆ THỐNG CHAT ĐA LUỒNG.docx` (thư mục gốc repo)

---

## 1. Mục tiêu sản phẩm

App chat desktop nhiều luồng, giao diện **clone Discord**, tối giản (lược hiệu ứng động). Các nhóm
tính năng theo yêu cầu gốc: nhắn tin riêng, tạo nhóm & chat nhóm, thông báo, kênh meet (mic/cam/share
màn hình), game chung (tích hợp sau), sticker/emoji, sửa–xoá–thu hồi tin nhắn, gửi ảnh/tệp, sửa hồ sơ
và avatar.

Giai đoạn hiện tại **chỉ triển khai**: đăng nhập demo + chat trực tiếp (DM) chạy cục bộ, không có
server. `ChatGateway` là ranh giới để thay bằng adapter TCP/JSON sau này.

## 2. Môi trường & lệnh chạy

JDK 21 + Maven được cài cục bộ cho user (không có sẵn JDK/Maven hệ thống):

```powershell
$env:JAVA_HOME = Join-Path $env:LOCALAPPDATA 'Programs\Kombai\Temurin-21\jdk-21.0.12.1+1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
cd chat-client
.\mvnw.cmd test        # 10 test
.\mvnw.cmd package
java -jar target\chat-client-1.0-SNAPSHOT.jar
```

Đăng nhập demo: email hợp lệ bất kỳ + mật khẩu không rỗng.

## 3. Thiết kế tham chiếu (canvas)

Canvas chuẩn: `.kombai/canvas/chat-desktop-discord.canvas` — **10 màn**, mỗi màn là một node `variant`
(HTML Tailwind). Đây là ground-truth cho bố cục; khi sửa UI phải đối chiếu lại node tương ứng.

| Màn | Node id | Trạng thái code |
|---|---|---|
| 01 — Chat nhóm | `var_ef5b84e8861e` | **đã có** (hội thoại nhóm, kênh văn bản, danh sách thành viên) |
| 02 — Chat riêng & sửa tin | `var_99a2e3ebb54c` | **đã có** (DM, sửa inline + lưu/hủy, thu hồi, xóa) |
| 03 — Tạo nhóm | `var_6784802754c4` | **đã có** (modal `CreateGroupDialog`, chọn thành viên, tìm kiếm) |
| 04 — Hộp thư thông báo | `var_a08310cf98d9` | **đã có** (modal/flyout `InboxDialog`, tab đọc/chưa đọc, badge 3) |
| 05 — Sảnh kênh gặp | `var_c814a36d7453` | **đã có** (`MeetLobbyPanel`, preview cam/mic, nút vào phòng) |
| 06 — Trong phòng & chia sẻ màn hình | `var_b345ca9da416` | **đã có** (`MeetRoomPanel`, 4 video tile, dialog share màn hình) |
| 07 — Hồ sơ & avatar | `var_0902bd43dbea` | **đã có** (`ProfileDialog`, sửa tên hiển thị, bio, avatar) |
| 08 — Đăng nhập | `var_0151cd9b9e0f` | **đã có** (hộp password bo góc + nút mắt trong ô) |
| 09 — Gửi tệp thất bại | `var_9ee09dbb1743` | **đã có** (card tệp đính kèm, trạng thái lỗi đỏ + nút gửi lại) |
| 10 — Trò chơi chưa tích hợp | `var_cca1192fc0d1` | **đã có** (`GamePlaceholderPanel` đúng mẫu canvas) |

Lấy lại HTML/ảnh của một node:

```
canvas_get_node .kombai/canvas/chat-desktop-discord.canvas#<node_id>
```

## 4. Design tokens

Nguồn duy nhất: `src/main/java/vn/edu/ltm/chat/theme/DiscordTheme.java`.

| Nhóm | Giá trị |
|---|---|
| Surface | `RAIL #151619`, `SIDEBAR #1E1F23`, `TRANSCRIPT #27292D`, `COMPOSER #313338`, `SELECTED #3B3D44`, `BANNER #24262A`, `SUNKEN #17181B`, `ACCOUNT_BAR #191A1E`, `TILE_IDLE #24262B`, `HAIRLINE #202125`, `SEPARATOR #3B3D44` |
| Text | `TEXT_PRIMARY #EAEBEE`, `TEXT_SECONDARY #AFB1B6`, `TEXT_MUTED #898C94` |
| Accent / trạng thái | `ACCENT #5E68F7` (hover `#5059E8`, soft `#8F98FF`), `SUCCESS #43C07A`, `WARNING #F0BB3B`, `DANGER #F54748` |
| Typography | Nunito Sans, 4 mặt tĩnh: Regular 400 / SemiBold 600 / Bold 700 / ExtraBold 800. Truy cập qua `DiscordTheme.font/semibold/bold/extrabold(size)` |
| Bán kính | card 14, callout/tile 8, rail tile 16, input/button 8, thanh trượt bo tròn hoàn toàn |
| Kích thước cột | rail 72, sidebar 240, pane hồ sơ 220, cột nội dung tối đa **780** |
| Nhịp dọc | card padding 24/22, header→form 18, field label→input 6–7, giữa nhóm field 14–16 |

**Quan trọng:** không dùng lại `NunitoSans-Variable.ttf`. JDK chỉ render instance mặc định
(ExtraLight) của font biến thiên, mọi weight đều ra nét mảnh như nhau. Xem
`THIRD-PARTY-NOTICES.md`.

## 5. Kiến trúc code

```
chat-client/src/main/java/vn/edu/ltm/chat/
├── app/            AppMain (EDT + theme), AppController (CardLayout login ↔ chat, kích thước cửa sổ)
├── application/    LoginService, ChatService, ChatGateway (port)
├── domain/         UserProfile, Conversation(id, displayName, initials, about, online), ChatMessage
├── infrastructure/demo/  InMemoryChatGateway (dữ liệu mẫu + append tin trong RAM)
├── presentation/   LoginPanel, ChatWorkspace, MessageRow, AvatarBadge,
│                   ColumnLayout, WrappedTextArea, RoundedPanel, RailTile
└── theme/          DiscordTheme (tokens + font + FlatLaf install), IconFactory (Phosphor SVG)
```

Luồng: view phát intent → `AppController`/service → `ChatGateway`; view không biết socket/protocol.
Tin nhắn chỉ được ghi vào `InMemoryChatGateway`, không có network.

### Layout các màn đã có

```
ĐĂNG NHẬP (nền RAIL)                          CHAT (3 cột cố định + pane phụ)
┌────────────────────────────┐                ┌────┬──────────────┬──────────────────────┬──────────┐
│  JScrollPane (dự phòng)    │                │rail│ sidebar 240  │ cột tin nhắn (cap 780)│ hồ sơ 220│
│   ┌ card 448 ───────────┐  │                │ 72 │ search/inbox │ header 62            │ THÔNG TIN│
│   │ logo 44 · tên app   │  │                │    │ list 46/row  │ banner demo          │ avatar 74│
│   │ headline 23         │  │                │tile│ TIN NHẮN ... │ transcript (scroll)  │ tên/handle│
│   │ subtitle            │  │                │    │              │  intro + messages    │ giới thiệu│
│   │ Email 42px          │  │                │    │              │ composer 780         │ ghi chú   │
│   │ Mật khẩu 42px + 👁  │  │                │    │ voice state  │  input + footer hint  │          │
│   │ callout demo        │  │                │    │ account 58   │                      │          │
│   │ [Đăng nhập] 42      │  │                └────┴──────────────┴──────────────────────┴──────────┘
│   │ [Tạo tài khoản] 42  │  │
│   │ ────── footer note  │  │
│   └─────────────────────┘  │
└────────────────────────────┘
```

Header / transcript / composer nằm trong **một `JPanel` riêng** đặt ở `CENTER` của vùng chat, pane hồ
sơ ở `EAST` — tránh việc `BorderLayout.SOUTH` kéo composer chạy xuống dưới pane hồ sơ.

## 6. Lỗi hiển thị đã sửa (đừng tái phạm)

| Triệu chứng | Nguyên nhân gốc | Cách xử lý hiện tại |
|---|---|---|
| Card đăng nhập bị cắt đáy | card gán cứng `448×652`, không có vùng cuộn | card cao tự nhiên + `JScrollPane` với host `Scrollable`; card căn giữa khi đủ chỗ |
| Ô nhập hẹp ~2/3, phụ đề bị cắt | `BoxLayout` dọc không giãn con theo bề rộng | card dùng `GridBagLayout` một cột, `fill=HORIZONTAL`, `weightx=1` |
| Chữ quá mảnh so với canvas | font biến thiên → chỉ ra ExtraLight | bundle 4 mặt tĩnh |
| Tin nhắn bị rải khắp khung, wrap sai | `BoxLayout` chia khoảng dư; đo wrap bằng `getWidth()` | `ColumnLayout` + `WrappedTextArea` (wrap width do layout truyền xuống) |
| Composer hẹp, đè lên pane hồ sơ | `BoxLayout` không giãn; composer ở `SOUTH` của cả vùng chat | composer trong cột riêng, `ColumnLayout(780)`; pane hồ sơ tách sang `EAST` |
| Pane hồ sơ cắt nhãn / note cao 408px | `BoxLayout` + nhãn HTML tự đo sai | `GridBagLayout`, note dùng `WrappedTextArea` |
| Log `UnknownStyleException: unknown style 'arc'` | `FlatLaf.style="arc:..."` trên `JButton` | bỏ; dùng token `Component.arc` / `Button.arc` |
| Ô mật khẩu thấp 22px | không set preferred size | set 42px (bằng ô email) |
| Ký tự cuối bị cắt 1px | JLabel đo bằng fractional metrics | `ChatWorkspace.padRight(label)` thêm 4px phải |

## 7. Công cụ soát giao diện (không cần mở app)

`src/test/java/vn/edu/ltm/chat/presentation/UiSnapshotTool.java` dựng thật `LoginPanel` /
`ChatWorkspace`, chạy layout rồi render ra PNG:

```powershell
.\mvnw.cmd test-compile
mvn -q dependency:build-classpath -Dmdep.outputFile=target\cp.txt
java -cp "target\classes;target\test-classes;$(Get-Content target\cp.txt)" `
  vn.edu.ltm.chat.presentation.UiSnapshotTool target\ui-snapshots
```

- Ảnh render ở lần kiểm tra cuối được lưu tại `chat-client/docs/ui-snapshots/`:
  `login-1360x786.png`, `login-1024x640.png`, `chat-1360x786.png`, `chat-1000x660.png`
  (dùng làm mốc so sánh khi sửa UI; `target/` sẽ bị `mvn clean` xoá).
- Thêm tham số `debug` để in cây component kèm `bounds / pref / max` (dùng để tìm lỗi bề rộng).
- Lưu ý: tool này chạy vài lượt `validate()` để layout ổn định; nếu thấy số đo lạ, đọc cây
  component thay vì suy đoán từ ảnh.

## 8. Trạng thái tính năng

Đã có:
- Đăng nhập demo (validation email/mật khẩu, hiện/ẩn mật khẩu, thông báo lỗi, đăng xuất).
- 4 hội thoại mẫu, chuyển hội thoại, tìm kiếm (Ctrl+K), nháp riêng từng hội thoại.
- Gửi tin bằng nút hoặc Enter (Shift+Enter xuống dòng), transcript tự cuộn cuối, avatar + giờ.
- Banner + badge nói rõ dữ liệu demo/cục bộ; pane hồ sơ minh hoạ.

Chưa có (xem backlog mục 9): tạo nhóm, thông báo, sửa/xoá/thu hồi, emoji/sticker, gửi ảnh/tệp,
meet (mic/cam/share), game, sửa hồ sơ/avatar, đăng ký, kết nối server.

## 9. Backlog đề xuất (theo thứ tự)

1. **Icon mắt trong ô mật khẩu** (khớp canvas 100%): custom row vẽ field full width + button overlay.
2. **Sửa / xoá / thu hồi tin nhắn** (màn 02): menu hành động khi hover, trạng thái "đang sửa", cần
   thêm method vào `ChatGateway` (`editMessage`, `deleteMessage`, `recallMessage`) + unit test.
3. **Tạo nhóm & chat nhóm** (màn 01, 03): mở rộng `Conversation` thành `group` (thành viên), sidebar
   section "NHÓM GẦN ĐÂY", màn tạo nhóm có chọn thành viên.
4. **Hộp thư thông báo** (màn 04): danh sách mention/notification, badge số lượng.
5. **Kênh gặp + trong phòng** (màn 05, 06): sảnh kênh, lobby, phòng có mic/cam/share (chỉ UI + trạng
   thái giả lập, chưa media thật).
6. **Sticker/emoji + gửi ảnh/tệp** (màn 09 cho trạng thái lỗi): picker, bubble ảnh, card tệp.
7. **Hồ sơ & avatar** (màn 07): form sửa tên/tiểu sử, chọn avatar từ tệp.
8. **Game chung** (màn 10): trạng thái "chưa tích hợp" + chỗ nhúng sau này.
9. **Adapter TCP/JSON thật**: implement `ChatGateway` mới theo packet mô tả trong báo cáo DOCX
   (`newline` framing, login/register, send/history, presence/typing), giữ nguyên tầng presentation.

## 10. Quy ước khi sửa UI (bắt buộc)

- Bố cục cột/khung dùng `ColumnLayout` hoặc `GridBagLayout`; **không** dùng `BoxLayout` cho cột cần
  giãn bề rộng (đây là nguồn gốc của phần lớn lỗi đã sửa).
- Văn bản nhiều dòng: `WrappedTextArea` (tự tính chiều cao theo wrap width), không dùng
  `<div style='width:...'>` trong HTML của JLabel.
- Nhãn nằm sát mép: bọc `padRight` để tránh mất ký tự cuối.
- Màu/kích thước lấy từ `DiscordTheme`; thêm token mới vào đó thay vì hard-code.
- Icon: thêm SVG Phosphor vào `src/main/resources/icons/`, dùng `IconFactory.phosphor(name, size, color)`.
  Tải icon mới từ `https://cdn.jsdelivr.net/npm/@phosphor-icons/core@2.1.1/assets/regular/<name>.svg`.
- Trạng thái chưa hỗ trợ phải nói rõ "chưa hỗ trợ trong bản demo" (tooltip/nhãn), không giả lập hành vi.
- Giữ tối giản: không thêm animation/hiệu ứng khi chưa được yêu cầu (ràng buộc của engineer).
- Sau mỗi thay đổi UI: `.\mvnw.cmd test` + render `UiSnapshotTool` và đối chiếu node canvas tương ứng.

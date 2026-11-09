# Sơ đồ & Quan hệ Cơ sở Dữ liệu (Database ERD & Relationships)

Hệ thống `chat-server` sử dụng mô hình cơ sở dữ liệu quan hệ (hỗ trợ cả **SQLite** cục bộ và **PostgreSQL / Supabase** trên Cloud) với 5 bảng cốt lõi quản lý người dùng, nhóm chat, tin nhắn, tệp đính kèm và cảm xúc.

---

## 1. Sơ đồ Quan hệ Thực thể (Entity-Relationship Diagram)

```mermaid
erDiagram
    users ||--o{ user_dms : "user_id (chủ danh bạ)"
    users ||--o{ user_dms : "peer_id (người trò chuyện)"
    users ||--o{ messages : "sender_id (người gửi)"
    users ||--o{ message_reactions : "user_id (người thả cảm xúc)"
    conversations ||--o{ messages : "conversation_id (nhóm chat)"
    messages ||--o{ message_reactions : "message_id (tin nhắn gốc)"

    users {
        TEXT id PK "Mã định danh duy nhất (UUID hoặc ID số)"
        TEXT email UK "Email đăng nhập duy nhất"
        TEXT password "Mật khẩu băm hoặc chuỗi xác thực"
        TEXT display_name "Tên hiển thị công khai"
        TEXT username "Tên tài khoản duy nhất (@username)"
        TEXT bio "Mô tả / Tiểu sử cá nhân"
        TEXT status "Trạng thái hiển thị (online, idle, dnd, offline)"
        TEXT avatar_path "Đường dẫn ảnh đại diện"
    }

    conversations {
        TEXT id PK "Mã hội thoại / nhóm (UUID hoặc slug)"
        TEXT display_name "Tên nhóm hiển thị"
        TEXT initials "Chữ cái viết tắt đại diện nhóm"
        TEXT about "Mô tả / Mục đích nhóm"
        INTEGER online "Trạng thái hoạt động"
        INTEGER is_group "1 nếu là nhóm chat, 0 nếu không"
        TEXT members_json "Mảng JSON chứa danh sách user_id thành viên"
        TEXT channels_json "Mảng JSON danh sách kênh văn bản (text channels)"
        TEXT voice_channels_json "Mảng JSON danh sách kênh thoại (voice channels)"
    }

    user_dms {
        TEXT user_id PK_FK "ID của người dùng sở hữu danh bạ"
        TEXT peer_id PK_FK "ID của đối phương trong danh bạ"
        TEXT created_at "Thời điểm bắt đầu cuộc trò chuyện"
    }

    messages {
        TEXT id PK "Mã tin nhắn duy nhất (UUID)"
        TEXT conversation_id FK "Mã hội thoại (conversations.id hoặc dm:min:max)"
        TEXT channel "Tên kênh trong nhóm (rỗng nếu là DM)"
        TEXT sender_id FK "ID người gửi (users.id)"
        TEXT sender_name "Tên hiển thị người gửi tại thời điểm gửi"
        TEXT sender_initials "Chữ cái viết tắt người gửi"
        TEXT content "Nội dung văn bản tin nhắn"
        TEXT sent_at "Thời gian gửi (ISO-8601)"
        INTEGER edited "1 nếu tin nhắn đã được chỉnh sửa, 0 nếu chưa"
        INTEGER recalled "1 nếu tin nhắn đã bị thu hồi, 0 nếu chưa"
        TEXT attachment_name "Tên file đính kèm vật lý"
        INTEGER attachment_size "Kích thước tệp (bytes)"
        INTEGER attachment_failed "1 nếu tải file thất bại, 0 nếu thành công"
    }

    message_reactions {
        TEXT message_id PK_FK "Mã tin nhắn được thả cảm xúc (messages.id)"
        TEXT user_id PK_FK "Mã người thả cảm xúc (users.id)"
        TEXT emoji PK "Biểu tượng cảm xúc (unicode emoji)"
    }
```

---

## 2. Chi tiết Các Mối quan hệ (Table Relationships)

### 2.1. Quan hệ giữa `users` và `user_dms` (1 - N)
- **Cặp khóa ngoại**: `user_dms.user_id -> users.id` và `user_dms.peer_id -> users.id`.
- **Bản số (Cardinality)**: 1 User có thể có nhiều bản ghi trong `user_dms` (nhiều bạn chat 1-1).
- **Ý nghĩa nghiệp vụ**:
  - Khi User A bắt đầu nhắn tin với User B, hệ thống ghi 2 bản ghi: `(userA, userB)` và `(userB, userA)`.
  - Nhờ đó, truy vấn danh sách hội thoại DM của User A chỉ cần một câu lệnh đơn giản: `SELECT peer_id FROM user_dms WHERE user_id = ?`.

### 2.2. Quan hệ giữa `conversations` và `messages` (1 - N)
- **Khóa ngoại**: `messages.conversation_id -> conversations.id`.
- **Bản số**: 1 cuộc hội thoại (Group) có thể chứa **nhiều tin nhắn** (`0..N`).
- **Lưu ý đặc biệt với Tin nhắn trực tiếp 1-1**:
  - Với tin nhắn nhóm: `conversation_id` chính là `id` của bảng `conversations`.
  - Với tin nhắn 1-1: `conversation_id` sử dụng định dạng chuẩn hóa **`dm:<min_user_id>:<max_user_id>`** (ví dụ: `dm:1:2`). Nhờ vậy, hai chiều gửi A -> B và B -> A dùng chung một dòng lịch sử tin nhắn mà **không cần tạo bản ghi giả** trong bảng `conversations`.

### 2.3. Quan hệ giữa `users` và `messages` (1 - N)
- **Khóa ngoại**: `messages.sender_id -> users.id`.
- **Bản số**: 1 người dùng có thể gửi **nhiều tin nhắn** trong nhiều cuộc trò chuyện khác nhau.

### 2.4. Quan hệ giữa `messages` và `message_reactions` (1 - N)
- **Khóa ngoại**: `message_reactions.message_id -> messages.id`.
- **Bản số**: 1 tin nhắn có thể nhận được **nhiều biểu tượng cảm xúc** từ nhiều người dùng.
- **Khóa chính kết hợp**: `PRIMARY KEY (message_id, user_id, emoji)`. Đảm bảo mỗi người dùng chỉ có thể thả đúng 1 lần cho cùng 1 loại emoji trên 1 tin nhắn cụ thể (bấm lần nữa sẽ rút lại cảm xúc).

### 2.5. Quan hệ giữa `users` và `message_reactions` (1 - N)
- **Khóa ngoại**: `message_reactions.user_id -> users.id`.
- **Ý nghĩa**: Xác định ai là người đã bày tỏ cảm xúc lên tin nhắn.

---

## 3. Bảng Đặc tả Chi tiết các Bảng Dữ liệu

### 3.1. Bảng `users` (Thông tin tài khoản)
| Tên cột | Kiểu dữ liệu | Ràng buộc | Diễn giải |
| :--- | :--- | :--- | :--- |
| `id` | `TEXT` | `PRIMARY KEY` | Khóa chính nhận diện người dùng. |
| `email` | `TEXT` | `UNIQUE NOT NULL` | Email dùng để đăng nhập. |
| `password` | `TEXT` | `NOT NULL` | Mật khẩu xác thực. |
| `display_name` | `TEXT` | `NOT NULL` | Tên hiển thị công khai trên giao diện chat. |
| `username` | `TEXT` | `NOT NULL` | Username định danh người dùng (dạng `@name`). |
| `bio` | `TEXT` | `NOT NULL` | Mô tả tiểu sử hiển thị trên hồ sơ. |
| `status` | `TEXT` | `NOT NULL` | Trạng thái hiển thị (`online`, `idle`, `dnd`, `offline`). |
| `avatar_path` | `TEXT` | `NULL` | Đường dẫn file ảnh đại diện trên đĩa hoặc URL. |

---

### 3.2. Bảng `conversations` (Nhóm chat & Kênh)
| Tên cột | Kiểu dữ liệu | Ràng buộc | Diễn giải |
| :--- | :--- | :--- | :--- |
| `id` | `TEXT` | `PRIMARY KEY` | Khóa chính của nhóm chat. |
| `display_name` | `TEXT` | `NOT NULL` | Tên của nhóm chat. |
| `initials` | `TEXT` | `NOT NULL` | Ký tự viết tắt đại diện nhóm hiển thị trên icon. |
| `about` | `TEXT` | `NOT NULL` | Giới thiệu nội dung nhóm. |
| `online` | `INTEGER` | `NOT NULL DEFAULT 1` | Cờ trạng thái hoạt động. |
| `is_group` | `INTEGER` | `NOT NULL DEFAULT 0` | `1` nếu là nhóm chat đa thành viên. |
| `members_json` | `TEXT` | `NOT NULL` | Mảng JSON chứa danh sách `user_id` các thành viên hợp lệ. |
| `channels_json` | `TEXT` | `NOT NULL` | Mảng JSON danh sách kênh chat văn bản (vd: `["chung", "học-tập"]`). |
| `voice_channels_json` | `TEXT` | `NOT NULL` | Mảng JSON danh sách phòng thoại (vd: `["Phòng Thoại 1"]`). |

---

### 3.3. Bảng `user_dms` (Danh bạ hội thoại 1-1)
| Tên cột | Kiểu dữ liệu | Ràng buộc | Diễn giải |
| :--- | :--- | :--- | :--- |
| `user_id` | `TEXT` | `NOT NULL` | Khóa ngoại trỏ đến `users.id` (chủ danh bạ). |
| `peer_id` | `TEXT` | `NOT NULL` | Khóa ngoại trỏ đến `users.id` (người trò chuyện cùng). |
| `created_at` | `TEXT` | `NOT NULL` | Thời gian tạo liên hệ. |
| *Khóa chính* | | `PRIMARY KEY (user_id, peer_id)` | Ngăn chặn trùng lặp liên hệ. |

---

### 3.4. Bảng `messages` (Tin nhắn văn bản & Tệp đính kèm)
| Tên cột | Kiểu dữ liệu | Ràng buộc | Diễn giải |
| :--- | :--- | :--- | :--- |
| `id` | `TEXT` | `PRIMARY KEY` | Mã UUID định danh tin nhắn. |
| `conversation_id` | `TEXT` | `NOT NULL` | ID nhóm hoặc mã chuẩn hóa `dm:min:max`. |
| `channel` | `TEXT` | `NOT NULL DEFAULT ''` | Kênh text trong nhóm chat (rỗng nếu là DM). |
| `sender_id` | `TEXT` | `NOT NULL` | ID người gửi tin (`users.id`). |
| `sender_name` | `TEXT` | `NOT NULL` | Tên hiển thị người gửi tại lúc gửi. |
| `sender_initials`| `TEXT` | `NOT NULL` | Viết tắt người gửi. |
| `content` | `TEXT` | `NOT NULL` | Nội dung tin nhắn văn bản. |
| `sent_at` | `TEXT` | `NOT NULL` | Thời điểm gửi (chuỗi thời gian chuẩn ISO). |
| `edited` | `INTEGER` | `NOT NULL DEFAULT 0` | Đánh dấu tin nhắn đã qua chỉnh sửa hay chưa. |
| `recalled` | `INTEGER` | `NOT NULL DEFAULT 0` | Đánh dấu tin nhắn đã bị thu hồi hay chưa. |
| `attachment_name`| `TEXT` | `NULL` | Tên file đính kèm được lưu vật lý trong thư mục `server_storage/`. |
| `attachment_size`| `INTEGER` | `NOT NULL DEFAULT 0` | Kích thước file tính bằng byte. |
| `attachment_failed`| `INTEGER`| `NOT NULL DEFAULT 0` | Cờ báo lỗi tải file nếu tệp hỏng. |

---

### 3.5. Bảng `message_reactions` (Biểu cảm cảm xúc tin nhắn)
| Tên cột | Kiểu dữ liệu | Ràng buộc | Diễn giải |
| :--- | :--- | :--- | :--- |
| `message_id` | `TEXT` | `NOT NULL` | Khóa ngoại trỏ đến `messages.id`. |
| `user_id` | `TEXT` | `NOT NULL` | Khóa ngoại trỏ đến `users.id`. |
| `emoji` | `TEXT` | `NOT NULL` | Biểu tượng cảm xúc (vd: `👍`, `❤️`, `😂`). |
| *Khóa chính* | | `PRIMARY KEY (message_id, user_id, emoji)` | Đảm bảo tính duy nhất của cảm xúc mỗi người. |

---

## 4. Các Điểm Thiết kế Kiến trúc Quan trọng

1. **Khóa hội thoại chuẩn hóa (Canonical DM Key)**:
   - Thay vì lưu hội thoại 1-1 thành 2 luồng riêng, hàm `DatabaseManager.canonicalDmKey(userA, userB)` luôn sắp xếp theo thứ tự từ điển: `dm:<min>:<max>`.
   - Kết quả: Khi User 1 gửi cho User 2 và ngược lại, cả 2 đều đọc và ghi chung vào `conversation_id = "dm:1:2"`, đảm bảo tính toàn vẹn và đồng bộ lịch sử tin nhắn tuyệt đối.
2. **Cơ chế Lưu trữ Tệp lai (Hybrid Storage)**:
   - Dữ liệu tin nhắn dạng text, timestamp và metadata được lưu trong SQL.
   - Nội dung file đính kèm dung lượng lớn được lưu trực tiếp vào hệ thống tệp đĩa (`FileStorageRepository` trỏ vào thư mục `server_storage/`), ngăn ngừa tình trạng phình to cơ sở dữ liệu làm suy giảm tốc độ truy vấn.
3. **An toàn Luồng & Khóa Ghi (Thread-safety)**:
   - Trong `DatabaseManager.java`, mọi thao tác `INSERT`, `UPDATE`, `DELETE` trên SQLite đều đi qua khóa đồng thuận `dbWriteLock` (`ReentrantLock`) kết hợp cấu hình `PRAGMA journal_mode = WAL` và `busy_timeout = 5000` để loại bỏ hoàn toàn lỗi tranh chấp luồng `SQLITE_BUSY`.

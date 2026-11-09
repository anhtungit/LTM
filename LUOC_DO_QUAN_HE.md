# Lược đồ Quan hệ Cơ sở Dữ liệu (Relational Schema)

Tài liệu này thể hiện chính thức **Lược đồ quan hệ (Relational Schema)**, các phụ thuộc khóa ngoại và ràng buộc toàn vẹn của hệ thống cơ sở dữ liệu dự án LTM.

---

## 1. Ký hiệu Lược đồ Quan hệ Chuẩn (Formal Relational Schema)

> **Quy ước ký hiệu**:
> - Thuộc tính gạch chân: **Khóa chính (Primary Key - PK)**
> - Thuộc tính có dấu `*`: **Khóa ngoại (Foreign Key - FK)**

* **USERS** (<u>id</u>, email, password, display_name, username, bio, status, avatar_path)
* **CONVERSATIONS** (<u>id</u>, display_name, initials, about, online, is_group, members_json, channels_json, voice_channels_json)
* **USER_DMS** (<u>user_id*</u>, <u>peer_id*</u>, created_at)
* **MESSAGES** (<u>id</u>, conversation_id*, channel, sender_id*, sender_name, sender_initials, content, sent_at, edited, recalled, attachment_name, attachment_size, attachment_failed)
* **MESSAGE_REACTIONS** (<u>message_id*</u>, <u>user_id*</u>, <u>emoji</u>)

---

## 2. Sơ đồ Mối liên kết Khóa ngoại (Relational Schema Diagram)

```
  ┌──────────────┐
  │    USERS     │
  ├──────────────┤
  │ PK  id ◄─────┼─────────────────────────┐
  │     email    │                         │
  │     username │                         │
  └───────┬──────┘                         │
          │                                │
          │ 1. users.id -> user_dms.user_id│ 4. users.id -> message_reactions.user_id
          │ 2. users.id -> user_dms.peer_id│
          ▼                                │
  ┌──────────────────┐                     │
  │     USER_DMS     │                     │
  ├──────────────────┤                     │
  │ PK,FK1  user_id  │                     │
  │ PK,FK2  peer_id  │                     │
  │         created_at│                    │
  └──────────────────┘                     │
                                           │
  ┌──────────────────┐                     │
  │  CONVERSATIONS   │                     │
  ├──────────────────┤                     │
  │ PK  id           │                     │
  │     members_json │                     │
  └───────┬──────────┘                     │
          │                                │
          │ 3. conversations.id            │
          │    -> messages.conversation_id │
          ▼                                │
  ┌──────────────────────┐                 │
  │       MESSAGES       │                 │
  ├──────────────────────┤                 │
  │ PK  id ◄─────────────┼────────┐        │
  │ FK1 conversation_id  │        │        │
  │ FK2 sender_id ───────┼────────┘        │
  │     content          │   5. messages.id│
  │     sent_at          │      -> reactions.message_id
  └──────────────────────┘        ▼
                          ┌──────────────────────┐
                          │  MESSAGE_REACTIONS   │
                          ├──────────────────────┤
                          │ PK,FK1  message_id   │
                          │ PK,FK2  user_id ─────┘
                          │ PK      emoji        │
                          └──────────────────────┘
```

---

## 3. Tập các Ràng buộc Khóa ngoại (Foreign Key Constraints)

1. **`USER_DMS(user_id)`** $\longrightarrow$ **`USERS(id)`**
   - Người sở hữu danh bạ phải là một người dùng hợp lệ trong bảng `users`.
2. **`USER_DMS(peer_id)`** $\longrightarrow$ **`USERS(id)`**
   - Người được trò chuyện cùng phải là một tài khoản tồn tại trong bảng `users`.
3. **`MESSAGES(sender_id)`** $\longrightarrow$ **`USERS(id)`**
   - Người gửi tin nhắn phải là một thành viên hợp lệ trong hệ thống.
4. **`MESSAGES(conversation_id)`** $\longrightarrow$ **`CONVERSATIONS(id)`**
   - Áp dụng đối với nhóm chat (`is_group = 1`).
   - *Đối với tin nhắn trực tiếp (1-1)*: `conversation_id` mang giá trị logic chuẩn hóa `dm:<min_user_id>:<max_user_id>` (`canonicalDmKey`).
5. **`MESSAGE_REACTIONS(message_id)`** $\longrightarrow$ **`MESSAGES(id)`**
   - Biểu cảm cảm xúc phải thuộc về một tin nhắn cụ thể đã tồn tại.
6. **`MESSAGE_REACTIONS(user_id)`** $\longrightarrow$ **`USERS(id)`**
   - Người thả biểu tượng cảm xúc phải là một tài khoản người dùng trong hệ thống.

---

## 4. Các Ràng buộc Toàn vẹn (Integrity Constraints)

### 4.1. Ràng buộc Toàn vẹn Thực thể (Entity Integrity)
- **`USERS.id`**, **`CONVERSATIONS.id`**, **`MESSAGES.id`**: Khóa chính đơn, giá trị bắt buộc duy nhất và không được phép `NULL`.
- **`USER_DMS.(user_id, peer_id)`**: Khóa chính kết hợp 2 cột, ngăn chặn việc tạo trùng lặp mối liên hệ giữa hai người dùng trong danh bạ.
- **`MESSAGE_REACTIONS.(message_id, user_id, emoji)`**: Khóa chính kết hợp 3 cột, đảm bảo mỗi người dùng chỉ có thể thả đúng 1 lần cho cùng 1 biểu tượng cảm xúc trên một tin nhắn cụ thể.

### 4.2. Ràng buộc Miền giá trị & Định danh (Domain & Uniqueness)
- **`USERS.email`**: Ràng buộc `UNIQUE NOT NULL` (mỗi email chỉ đăng ký được 1 tài khoản duy nhất).
- **`MESSAGES.edited`**, **`MESSAGES.recalled`**: Miền giá trị boolean (`0` hoặc `1`).
- **`CONVERSATIONS.is_group`**: Miền giá trị boolean (`1` là nhóm chat đa thành viên, `0` là nhóm thường).

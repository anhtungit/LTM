package vn.edu.ltm.chat.presentation;

import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Image;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Asset provider for full-color Twemoji icons and Discord stickers. */
public final class EmojiAssets {
    private EmojiAssets() {}

    public record StickerItem(String id, String name, String hex, Color accent, String tag) {}

    private static final Map<String, String> EMOJI_TO_HEX = new HashMap<>();
    private static final Map<String, ImageIcon> CACHE = new ConcurrentHashMap<>();

    public static final List<StickerItem> STICKERS = List.of(
            new StickerItem("party-cat", "Party Cat", "1f389", new Color(0xEB, 0x45, 0x9E), "Tiệc tùng"),
            new StickerItem("fire-dev", "Fire Dev", "1f525", new Color(0xED, 0x42, 0x45), "Nhiệt huyết"),
            new StickerItem("lgtm-ship", "LGTM Ship It", "1f680", new Color(0x57, 0xF2, 0x87), "Đã duyệt"),
            new StickerItem("bug-hunter", "Bug Hunter", "1f50d", new Color(0x58, 0x65, 0xF2), "Tìm lỗi"),
            new StickerItem("mind-blown", "Mind Blown", "1f92f", new Color(0xFE, 0xE7, 0x5C), "Kinh ngạc"),
            new StickerItem("coffee-first", "Coffee First", "2615", new Color(0xA8, 0x64, 0x32), "Nạp cafe"),
            new StickerItem("super-like", "Super Like", "1f44d", new Color(0x57, 0xF2, 0x87), "Tuyệt vời"),
            new StickerItem("victory-gg", "Victory GG", "1f3c6", new Color(0xFE, 0xE7, 0x5C), "Chiến thắng"),
            new StickerItem("thinking-pepe", "Thinking Pepe", "1f914", new Color(0x58, 0x65, 0xF2), "Suy ngẫm"),
            new StickerItem("sleepy-mood", "Sleepy Mood", "1f634", new Color(0x72, 0x89, 0xDA), "Buồn ngủ"),
            new StickerItem("popcorn-time", "Popcorn Time", "1f37f", new Color(0xEB, 0x45, 0x9E), "Hóng biến"),
            new StickerItem("game-master", "Game Master", "1f3ae", new Color(0x58, 0x65, 0xF2), "Chơi game"),
            new StickerItem("heart-eyes", "Love Heart", "1f60d", new Color(0xEB, 0x45, 0x9E), "Yêu thích"),
            new StickerItem("cool-shades", "Cool Shades", "1f60e", new Color(0x58, 0x65, 0xF2), "Cực ngầu"),
            new StickerItem("star-power", "Star Power", "2b50", new Color(0xFE, 0xE7, 0x5C), "Ngôi sao"),
            new StickerItem("rich-flex", "Money Rich", "1f911", new Color(0x57, 0xF2, 0x87), "Phát tài")
    );

    static {
        for (EmojiData.EmojiItem item : EmojiData.EMOTION) {
            EMOJI_TO_HEX.put(item.emoji(), item.hex());
        }
        for (EmojiData.EmojiItem item : EmojiData.GESTURE) {
            EMOJI_TO_HEX.put(item.emoji(), item.hex());
        }
        for (EmojiData.EmojiItem item : EmojiData.HEART) {
            EMOJI_TO_HEX.put(item.emoji(), item.hex());
        }
        for (EmojiData.EmojiItem item : EmojiData.OBJECT) {
            EMOJI_TO_HEX.put(item.emoji(), item.hex());
        }
    }

    public static String getHex(String emoji) {
        return EMOJI_TO_HEX.get(emoji);
    }

    public static boolean hasEmoji(String emoji) {
        return EMOJI_TO_HEX.containsKey(emoji);
    }

    public static java.util.Set<String> allEmojis() {
        return EMOJI_TO_HEX.keySet();
    }

    public static ImageIcon getIconByHex(String hex, int size) {
        if (hex == null || hex.isEmpty()) {
            return null;
        }
        String cacheKey = hex + "@" + size;
        return CACHE.computeIfAbsent(cacheKey, key -> {
            try {
                URL resource = EmojiAssets.class.getResource("/emojis/" + hex + ".png");
                if (resource == null) {
                    return null;
                }
                ImageIcon raw = new ImageIcon(resource);
                if (raw.getIconWidth() <= 0) {
                    return null;
                }
                Image scaled = raw.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
                return new ImageIcon(scaled);
            } catch (Exception e) {
                return null;
            }
        });
    }

    public static ImageIcon getIcon(String emoji, int size) {
        String hex = getHex(emoji);
        if (hex == null) {
            return null;
        }
        return getIconByHex(hex, size);
    }

    public static URL getUrl(String hex) {
        if (hex == null || hex.isEmpty()) {
            return null;
        }
        return EmojiAssets.class.getResource("/emojis/" + hex + ".png");
    }

    public static URL getEmojiUrl(String emoji) {
        return getUrl(getHex(emoji));
    }

    public static boolean containsEmoji(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (String emo : EMOJI_TO_HEX.keySet()) {
            if (text.contains(emo)) {
                return true;
            }
        }
        return false;
    }

    public static String toHtml(String text, int wrapWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String escaped = text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>");

        for (Map.Entry<String, String> entry : EMOJI_TO_HEX.entrySet()) {
            String emoji = entry.getKey();
            if (escaped.contains(emoji)) {
                URL url = getUrl(entry.getValue());
                if (url != null) {
                    String imgTag = "<img src='" + url.toExternalForm() + "' width='20' height='20' align='middle'>";
                    escaped = escaped.replace(emoji, imgTag);
                }
            }
        }
        return "<html><div style='width:" + wrapWidth + "px;font-family:sans-serif;color:#D9DADE;font-size:13px;line-height:1.4;'>"
                + escaped + "</div></html>";
    }

    public static StickerItem findSticker(String query) {
        if (query == null) {
            return null;
        }
        String q = query.trim().toLowerCase();
        for (StickerItem s : STICKERS) {
            if (s.name().equalsIgnoreCase(query.trim()) || s.id().equalsIgnoreCase(query.trim())
                    || q.contains(s.name().toLowerCase()) || q.contains(s.id())) {
                return s;
            }
        }
        return null;
    }
}


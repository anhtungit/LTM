package vn.edu.ltm.chat.server.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Standard application-layer Packet transmitted over TCP newline-delimited JSON stream.
 */
public final class Packet {
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private String type;
    private String requestId;
    private boolean ok = true;
    private String error;
    private JsonObject payload;

    public Packet() {
        this.payload = new JsonObject();
    }

    public Packet(String type, String requestId) {
        this.type = type;
        this.requestId = requestId;
        this.payload = new JsonObject();
    }

    public static Packet response(String type, String requestId) {
        return new Packet(type, requestId);
    }

    public static Packet error(String type, String requestId, String errorMsg) {
        Packet p = new Packet(type, requestId);
        p.ok = false;
        p.error = errorMsg;
        return p;
    }

    public static Packet broadcast(String type) {
        return new Packet(type, null);
    }

    public Packet put(String key, String value) {
        payload.addProperty(key, value);
        return this;
    }

    public Packet put(String key, Number value) {
        payload.addProperty(key, value);
        return this;
    }

    public Packet put(String key, Boolean value) {
        payload.addProperty(key, value);
        return this;
    }

    public Packet putObj(String key, Object obj) {
        payload.add(key, GSON.toJsonTree(obj));
        return this;
    }

    public String getString(String key) {
        if (payload == null || !payload.has(key) || payload.get(key).isJsonNull()) {
            return null;
        }
        return payload.get(key).getAsString();
    }

    public String getString(String key, String defaultValue) {
        String val = getString(key);
        return val != null ? val : defaultValue;
    }

    public long getLong(String key, long defaultValue) {
        if (payload == null || !payload.has(key) || payload.get(key).isJsonNull()) {
            return defaultValue;
        }
        return payload.get(key).getAsLong();
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        if (payload == null || !payload.has(key) || payload.get(key).isJsonNull()) {
            return defaultValue;
        }
        return payload.get(key).getAsBoolean();
    }

    public <T> T getObj(String key, Class<T> clazz) {
        if (payload == null || !payload.has(key) || payload.get(key).isJsonNull()) {
            return null;
        }
        JsonElement el = payload.get(key);
        return GSON.fromJson(el, clazz);
    }

    public String getType() {
        return type;
    }

    public String getRequestId() {
        return requestId;
    }

    public boolean isOk() {
        return ok;
    }

    public String getError() {
        return error;
    }

    public JsonObject getPayload() {
        return payload;
    }

    public String toJsonLine() {
        return GSON.toJson(this);
    }

    public static Packet fromJsonLine(String line) {
        return GSON.fromJson(line, Packet.class);
    }
}


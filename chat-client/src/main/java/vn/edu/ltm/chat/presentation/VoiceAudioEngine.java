package vn.edu.ltm.chat.presentation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Real-time PCM 16kHz 16-bit mono voice capture & playback using JDK javax.sound.sampled.
 */
public final class VoiceAudioEngine {
    // shortcut: 16kHz 16-bit mono PCM over Base64 TCP (~32KB/s), upgrade to UDP Opus when scaling > 5 peers
    public static final AudioFormat FORMAT = new AudioFormat(16000.0f, 16, 1, true, false);
    public static final int CHUNK_BYTES = 1280; // 40ms frame at 16kHz 16-bit mono (25 fps for low-jitter streaming)
    private static final int SPEAKER_BUFFER_BYTES = CHUNK_BYTES * 8; // 320ms hardware buffer
    private static final int MIC_BUFFER_BYTES = CHUNK_BYTES * 4;     // 160ms hardware buffer
    private static final int JITTER_QUEUE_CAPACITY = 16;             // Max 640ms jitter buffer
    private static final int PREBUFFER_FRAMES = 3;                   // 120ms pre-buffer before talkspurt playback

    public record AudioDevice(String id, String displayName) {
        @Override
        public String toString() {
            return displayName;
        }
    }

    private static volatile String selectedMicId = "default";
    private static volatile String selectedSpeakerId = "default";

    private volatile boolean running = false;
    private volatile boolean muted = false;
    private volatile boolean micAvailable = false;
    private volatile boolean speakerAvailable = false;
    private volatile double currentMicRms = 0.0;

    private volatile TargetDataLine micLine;
    private volatile SourceDataLine speakerLine;
    private final ArrayBlockingQueue<byte[]> playbackQueue = new ArrayBlockingQueue<>(JITTER_QUEUE_CAPACITY);
    private Thread captureThread;
    private Thread playbackThread;
    private Consumer<String> activeEncodedChunkConsumer;
    private Consumer<Boolean> activeSpeakingStateConsumer;

    public static List<AudioDevice> getAvailableInputDevices() {
        List<AudioDevice> list = new ArrayList<>();
        list.add(new AudioDevice("default", "Mặc định hệ thống"));

        List<AudioDevice> pwDevices = queryPipeWireDevices(true);
        if (!pwDevices.isEmpty()) {
            list.addAll(pwDevices);
            return list;
        }

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        for (Mixer.Info mInfo : AudioSystem.getMixerInfo()) {
            String name = mInfo.getName();
            if (name == null || name.startsWith("Port") || name.contains("[default]")) continue;
            try {
                Mixer mixer = AudioSystem.getMixer(mInfo);
                if (mixer.isLineSupported(info)) {
                    list.add(new AudioDevice(name, formatDeviceName(name, mInfo.getDescription(), true)));
                }
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    public static List<AudioDevice> getAvailableOutputDevices() {
        List<AudioDevice> list = new ArrayList<>();
        list.add(new AudioDevice("default", "Mặc định hệ thống"));

        List<AudioDevice> pwDevices = queryPipeWireDevices(false);
        if (!pwDevices.isEmpty()) {
            list.addAll(pwDevices);
            return list;
        }

        DataLine.Info info = new DataLine.Info(SourceDataLine.class, FORMAT);
        for (Mixer.Info mInfo : AudioSystem.getMixerInfo()) {
            String name = mInfo.getName();
            String desc = mInfo.getDescription();
            if (name == null || name.startsWith("Port") || name.contains("[default]")) continue;
            if (isUnpluggedLinuxHdmi(name, desc)) continue;
            try {
                Mixer mixer = AudioSystem.getMixer(mInfo);
                if (mixer.isLineSupported(info)) {
                    list.add(new AudioDevice(name, formatDeviceName(name, desc, false)));
                }
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    private static List<AudioDevice> queryPipeWireDevices(boolean isInput) {
        String json = runPwDump();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return parsePipeWireDevices(json, isInput);
    }

    static List<AudioDevice> parsePipeWireDevices(String pwDumpJson, boolean isInput) {
        List<AudioDevice> result = new ArrayList<>();
        if (pwDumpJson == null || pwDumpJson.isBlank()) return result;

        try {
            JsonElement rootEl = JsonParser.parseString(pwDumpJson);
            if (!rootEl.isJsonArray()) return result;
            JsonArray arr = rootEl.getAsJsonArray();

            Set<Integer> cardsWithRoutes = new HashSet<>();
            Set<String> seenIds = new HashSet<>();
            String targetDir = isInput ? "Input" : "Output";

            // 1. Extract available EnumRoutes from Audio/Device cards (matches GNOME Sound panel 1:1)
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                JsonObject info = obj.has("info") && obj.get("info").isJsonObject() ? obj.getAsJsonObject("info") : null;
                if (info == null) continue;
                JsonObject props = info.has("props") && info.get("props").isJsonObject() ? info.getAsJsonObject("props") : null;
                if (props == null) continue;

                if (!"Audio/Device".equals(getJsonString(props, "media.class"))) continue;

                int cardId = getJsonInt(obj, "id", -1);
                String cardName = getJsonString(props, "device.name");
                String cardDesc = firstNonBlank(
                        getJsonString(props, "device.description"),
                        getJsonString(props, "device.nick"),
                        getJsonString(props, "device.alias"),
                        cardName
                );
                if (cardName == null || cardDesc == null) continue;

                JsonObject params = info.has("params") && info.get("params").isJsonObject() ? info.getAsJsonObject("params") : null;
                JsonArray enumRoutes = (params != null && params.has("EnumRoute") && params.get("EnumRoute").isJsonArray())
                        ? params.getAsJsonArray("EnumRoute") : null;
                if (enumRoutes == null) continue;

                for (JsonElement rEl : enumRoutes) {
                    if (!rEl.isJsonObject()) continue;
                    JsonObject route = rEl.getAsJsonObject();
                    String dir = getJsonString(route, "direction");
                    if (!targetDir.equalsIgnoreCase(dir)) continue;

                    String avail = getJsonString(route, "available");
                    if ("no".equalsIgnoreCase(avail)) continue;

                    String routeName = getJsonString(route, "name");
                    String routeDesc = getJsonString(route, "description");
                    if (routeName == null || routeDesc == null) continue;

                    String id = "pw:route:" + cardName + ":" + targetDir + ":" + routeName;
                    if (seenIds.add(id)) {
                        result.add(new AudioDevice(id, routeDesc + " – " + cardDesc));
                        if (cardId >= 0) {
                            cardsWithRoutes.add(cardId);
                        }
                    }
                }
            }

            // 2. Include standalone Audio/Sink or Audio/Source nodes whose card had no EnumRoutes
            String targetClass = isInput ? "Audio/Source" : "Audio/Sink";
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                JsonObject info = obj.has("info") && obj.get("info").isJsonObject() ? obj.getAsJsonObject("info") : null;
                if (info == null) continue;
                JsonObject props = info.has("props") && info.get("props").isJsonObject() ? info.getAsJsonObject("props") : null;
                if (props == null) continue;

                if (!targetClass.equals(getJsonString(props, "media.class"))) continue;

                int devId = getJsonInt(props, "device.id", -1);
                if (devId >= 0 && cardsWithRoutes.contains(devId)) continue;

                String nodeName = getJsonString(props, "node.name");
                String nodeDesc = firstNonBlank(
                        getJsonString(props, "node.description"),
                        getJsonString(props, "node.nick"),
                        nodeName
                );
                if (nodeName == null || nodeDesc == null) continue;

                String id = "pw:node:" + nodeName;
                if (seenIds.add(id)) {
                    result.add(new AudioDevice(id, nodeDesc));
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static void activatePipeWireDevice(String deviceId, boolean isInput) {
        if (deviceId == null || !deviceId.startsWith("pw:")) return;
        try {
            String json = runPwDump();
            if (json == null || json.isBlank()) return;
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            String targetClass = isInput ? "Audio/Source" : "Audio/Sink";

            if (deviceId.startsWith("pw:node:")) {
                String nodeName = deviceId.substring("pw:node:".length());
                int nodeId = findPipeWireNodeByName(arr, targetClass, nodeName);
                if (nodeId >= 0) {
                    runWpctl("set-default", String.valueOf(nodeId));
                }
                return;
            }

            if (deviceId.startsWith("pw:route:")) {
                String rest = deviceId.substring("pw:route:".length());
                String[] parts = rest.split(":", 3);
                if (parts.length < 3) return;
                String cardName = parts[0];
                String dir = parts[1];
                String routeName = parts[2];

                JsonObject cardObj = findPipeWireCard(arr, cardName);
                if (cardObj == null) return;
                int cardId = getJsonInt(cardObj, "id", -1);
                if (cardId < 0) return;

                JsonObject info = cardObj.getAsJsonObject("info");
                JsonObject params = info != null && info.has("params") ? info.getAsJsonObject("params") : null;
                if (params == null) return;

                JsonObject targetRoute = null;
                JsonArray enumRoutes = params.has("EnumRoute") && params.get("EnumRoute").isJsonArray()
                        ? params.getAsJsonArray("EnumRoute") : null;
                if (enumRoutes != null) {
                    for (JsonElement rEl : enumRoutes) {
                        if (!rEl.isJsonObject()) continue;
                        JsonObject r = rEl.getAsJsonObject();
                        if (dir.equalsIgnoreCase(getJsonString(r, "direction"))
                                && routeName.equals(getJsonString(r, "name"))) {
                            targetRoute = r;
                            break;
                        }
                    }
                }
                if (targetRoute == null) return;

                int routeIndex = getJsonInt(targetRoute, "index", -1);
                Set<Integer> routeProfiles = getJsonIntSet(targetRoute, "profiles");
                Set<Integer> routeDevices = getJsonIntSet(targetRoute, "devices");

                int activeProfile = -1;
                if (params.has("Profile") && params.get("Profile").isJsonArray()) {
                    JsonArray profArr = params.getAsJsonArray("Profile");
                    if (!profArr.isEmpty() && profArr.get(0).isJsonObject()) {
                        activeProfile = getJsonInt(profArr.get(0).getAsJsonObject(), "index", -1);
                    }
                }

                // Switch card profile if the current profile does not support the chosen route
                if (!routeProfiles.isEmpty() && !routeProfiles.contains(activeProfile)) {
                    int bestProfile = selectBestProfile(params, routeProfiles);
                    if (bestProfile >= 0) {
                        runWpctl("set-profile", String.valueOf(cardId), String.valueOf(bestProfile));
                        Thread.sleep(120);
                        String refreshed = runPwDump();
                        if (refreshed != null && !refreshed.isBlank()) {
                            arr = JsonParser.parseString(refreshed).getAsJsonArray();
                        }
                    }
                }

                if (routeIndex >= 0) {
                    runWpctl("set-route", String.valueOf(cardId), String.valueOf(routeIndex));
                }

                int nodeId = findPipeWireNodeForCard(arr, targetClass, cardId, routeDevices);
                if (nodeId >= 0) {
                    runWpctl("set-default", String.valueOf(nodeId));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static int selectBestProfile(JsonObject params, Set<Integer> allowedProfiles) {
        int bestIdx = -1;
        int bestPriority = Integer.MIN_VALUE;
        if (params.has("EnumProfile") && params.get("EnumProfile").isJsonArray()) {
            for (JsonElement pEl : params.getAsJsonArray("EnumProfile")) {
                if (!pEl.isJsonObject()) continue;
                JsonObject p = pEl.getAsJsonObject();
                int idx = getJsonInt(p, "index", -1);
                if (!allowedProfiles.contains(idx)) continue;
                String avail = getJsonString(p, "available");
                if ("no".equalsIgnoreCase(avail)) continue;
                int prio = getJsonInt(p, "priority", 0);
                if (prio >= bestPriority) {
                    bestPriority = prio;
                    bestIdx = idx;
                }
            }
        }
        return bestIdx;
    }

    private static JsonObject findPipeWireCard(JsonArray arr, String cardName) {
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            JsonObject info = obj.has("info") && obj.get("info").isJsonObject() ? obj.getAsJsonObject("info") : null;
            if (info == null) continue;
            JsonObject props = info.has("props") && info.get("props").isJsonObject() ? info.getAsJsonObject("props") : null;
            if (props == null) continue;
            if ("Audio/Device".equals(getJsonString(props, "media.class"))
                    && cardName.equals(getJsonString(props, "device.name"))) {
                return obj;
            }
        }
        return null;
    }

    private static int findPipeWireNodeByName(JsonArray arr, String targetClass, String nodeName) {
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            JsonObject info = obj.has("info") && obj.get("info").isJsonObject() ? obj.getAsJsonObject("info") : null;
            if (info == null) continue;
            JsonObject props = info.has("props") && info.get("props").isJsonObject() ? info.getAsJsonObject("props") : null;
            if (props == null) continue;
            if (targetClass.equals(getJsonString(props, "media.class"))
                    && nodeName.equals(getJsonString(props, "node.name"))) {
                return getJsonInt(obj, "id", -1);
            }
        }
        return -1;
    }

    private static int findPipeWireNodeForCard(JsonArray arr, String targetClass, int cardId, Set<Integer> preferredProfileDevices) {
        int fallbackId = -1;
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            JsonObject info = obj.has("info") && obj.get("info").isJsonObject() ? obj.getAsJsonObject("info") : null;
            if (info == null) continue;
            JsonObject props = info.has("props") && info.get("props").isJsonObject() ? info.getAsJsonObject("props") : null;
            if (props == null) continue;
            if (!targetClass.equals(getJsonString(props, "media.class"))) continue;
            if (getJsonInt(props, "device.id", -1) != cardId) continue;

            int nodeId = getJsonInt(obj, "id", -1);
            int profDev = getJsonInt(props, "card.profile.device", -1);
            if (!preferredProfileDevices.isEmpty() && preferredProfileDevices.contains(profDev)) {
                return nodeId;
            }
            if (fallbackId < 0) {
                fallbackId = nodeId;
            }
        }
        return fallbackId;
    }

    private static String runPwDump() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("linux")) return null;
        try {
            Process proc = new ProcessBuilder("pw-dump")
                    .redirectErrorStream(false)
                    .start();
            byte[] bytes = proc.getInputStream().readAllBytes();
            boolean finished = proc.waitFor(1500, TimeUnit.MILLISECONDS);
            if (!finished) {
                proc.destroyForcibly();
                return null;
            }
            if (proc.exitValue() != 0) return null;
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static void runWpctl(String... args) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("wpctl");
            for (String a : args) cmd.add(a);
            Process proc = new ProcessBuilder(cmd).start();
            if (!proc.waitFor(1000, TimeUnit.MILLISECONDS)) {
                proc.destroyForcibly();
            }
        } catch (Exception ignored) {
        }
    }

    private static String getJsonString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return null;
        JsonElement el = obj.get(key);
        return (el != null && el.isJsonPrimitive()) ? el.getAsString() : null;
    }

    private static int getJsonInt(JsonObject obj, String key, int defaultValue) {
        if (obj == null || !obj.has(key)) return defaultValue;
        JsonElement el = obj.get(key);
        if (el != null && el.isJsonPrimitive()) {
            try {
                return el.getAsInt();
            } catch (Exception ignored) {
            }
        }
        return defaultValue;
    }

    private static Set<Integer> getJsonIntSet(JsonObject obj, String key) {
        Set<Integer> set = new HashSet<>();
        if (obj != null && obj.has(key) && obj.get(key).isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray(key)) {
                if (el.isJsonPrimitive()) {
                    try {
                        set.add(el.getAsInt());
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return set;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    static boolean isUnpluggedLinuxHdmi(String name, String desc) {
        if (name == null || desc == null || (!name.contains("[plughw:") && !name.contains("[hw:"))) {
            return false;
        }
        String[] parts = parseAlsaDescription(desc);
        if (parts == null) return false;
        String pcmName = parts[1];
        String subName = parts[2];
        // On Linux ALSA, an HDMI PCM whose subdevice name merely repeats "HDMI x" has no EDID monitor connected
        return pcmName.toUpperCase().startsWith("HDMI") && pcmName.equalsIgnoreCase(subName);
    }

    static String formatDeviceName(String name, String desc, boolean isInput) {
        if (name == null || name.isBlank()) {
            return isInput ? "Microphone" : "Speakers";
        }

        // Only transform raw Linux ALSA identifiers of the form "<id> [plughw:<card>,<dev>]".
        // On Windows, macOS, or native sound servers, return the OS-provided name directly.
        if (!name.contains("[plughw:") && !name.contains("[hw:")) {
            return name;
        }

        String[] parts = parseAlsaDescription(desc);
        if (parts == null) {
            return name;
        }

        String cardName = parts[0];
        String pcmName = parts[1];
        String subName = parts[2];

        int cardIdx = parseCardIndex(name);
        if (cardIdx >= 0 && isLinuxInternalCard(cardIdx)) {
            cardName = "Built-in Audio";
        }

        if (isInput) {
            return "Microphone – " + cardName;
        }

        String pcmUpper = pcmName.toUpperCase();
        if (pcmUpper.contains("HDMI") || pcmUpper.contains("DISPLAYPORT")) {
            if (!subName.isEmpty() && !subName.equalsIgnoreCase(pcmName)) {
                return "HDMI / DisplayPort (" + subName + ") – " + cardName;
            }
            return "HDMI / DisplayPort – " + cardName;
        }

        return "Speakers – " + cardName;
    }

    private static String[] parseAlsaDescription(String desc) {
        if (desc == null) return null;
        String clean = desc.startsWith("Direct Audio Device:")
                ? desc.substring("Direct Audio Device:".length()).trim()
                : desc.trim();
        String[] tokens = clean.split(",", 3);
        if (tokens.length < 3) return null;
        return new String[]{tokens[0].trim(), tokens[1].trim(), tokens[2].trim()};
    }

    private static int parseCardIndex(String mixerName) {
        int idx = mixerName.indexOf("hw:");
        if (idx < 0) return -1;
        int start = idx + 3;
        int comma = mixerName.indexOf(',', start);
        if (comma < 0) return -1;
        try {
            return Integer.parseInt(mixerName.substring(start, comma).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean isLinuxInternalCard(int cardIdx) {
        java.nio.file.Path udevPath = java.nio.file.Paths.get("/run/udev/data/+sound:card" + cardIdx);
        if (java.nio.file.Files.isReadable(udevPath)) {
            try {
                for (String line : java.nio.file.Files.readAllLines(udevPath)) {
                    if ("E:SOUND_FORM_FACTOR=internal".equals(line.trim())) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public static String getSelectedMicId() {
        return selectedMicId;
    }

    public static String getSelectedSpeakerId() {
        return selectedSpeakerId;
    }

    public double getCurrentMicRms() {
        return currentMicRms;
    }

    public static void testSpeaker(String deviceId) {
        new Thread(() -> {
            try {
                SourceDataLine line = openSpeakerLine(deviceId);
                if (line == null) return;
                // Play a brief 2-tone melodic chime (587Hz -> 880Hz, 150ms each)
                byte[] buf = new byte[9600];
                for (int i = 0; i < buf.length / 2; i++) {
                    double freq = (i < buf.length / 4) ? 587.33 : 880.00;
                    short s = (short) (Math.sin(2 * Math.PI * freq * i / 16000.0) * 10000);
                    buf[i * 2] = (byte) (s & 0xFF);
                    buf[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
                }
                line.write(buf, 0, buf.length);
                line.drain();
                line.close();
            } catch (Exception ignored) {
            }
        }, "audio-test-speaker").start();
    }

    public synchronized void switchMicDevice(String deviceId) {
        selectedMicId = (deviceId != null && !deviceId.isBlank()) ? deviceId : "default";
        if (selectedMicId.startsWith("pw:")) {
            activatePipeWireDevice(selectedMicId, true);
        }
        if (running) {
            closeCurrentMicLineAndWait();
            TargetDataLine newLine = openMicLine(selectedMicId);
            this.micLine = newLine;
            this.micAvailable = (newLine != null);
            if (newLine != null) {
                Consumer<String> chunkCb = this.activeEncodedChunkConsumer;
                Consumer<Boolean> speakCb = this.activeSpeakingStateConsumer;
                captureThread = new Thread(() -> captureLoop(newLine, chunkCb, speakCb), "voice-mic-capture");
                captureThread.setDaemon(true);
                captureThread.setPriority(Thread.MAX_PRIORITY);
                captureThread.start();
            }
        }
    }

    public synchronized void switchSpeakerDevice(String deviceId) {
        selectedSpeakerId = (deviceId != null && !deviceId.isBlank()) ? deviceId : "default";
        if (selectedSpeakerId.startsWith("pw:")) {
            activatePipeWireDevice(selectedSpeakerId, false);
        }
        if (running) {
            if (speakerLine != null) {
                try {
                    speakerLine.stop();
                    speakerLine.close();
                } catch (Exception ignored) {
                }
                speakerLine = null;
            }
            speakerLine = openSpeakerLine(selectedSpeakerId);
            speakerAvailable = (speakerLine != null);
        }
    }

    private void closeCurrentMicLineAndWait() {
        TargetDataLine oldLine = this.micLine;
        this.micLine = null;
        if (oldLine != null) {
            try {
                oldLine.stop();
                oldLine.close();
            } catch (Exception ignored) {
            }
        }
        Thread oldThread = this.captureThread;
        this.captureThread = null;
        if (oldThread != null && oldThread != Thread.currentThread()) {
            try {
                oldThread.join(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    synchronized void start(Consumer<String> onEncodedChunk) {
        start(onEncodedChunk, null);
    }

    synchronized void start(Consumer<String> onEncodedChunk, Consumer<Boolean> onSpeakingState) {
        stop();
        running = true;
        playbackQueue.clear();
        this.activeEncodedChunkConsumer = onEncodedChunk;
        this.activeSpeakingStateConsumer = onSpeakingState;

        speakerLine = openSpeakerLine(selectedSpeakerId);
        speakerAvailable = (speakerLine != null);

        playbackThread = new Thread(this::playbackLoop, "voice-speaker-playback");
        playbackThread.setDaemon(true);
        playbackThread.setPriority(Thread.MAX_PRIORITY);
        playbackThread.start();

        TargetDataLine newMicLine = openMicLine(selectedMicId);
        this.micLine = newMicLine;
        this.micAvailable = (newMicLine != null);

        if (newMicLine != null) {
            captureThread = new Thread(() -> captureLoop(newMicLine, onEncodedChunk, onSpeakingState), "voice-mic-capture");
            captureThread.setDaemon(true);
            captureThread.setPriority(Thread.MAX_PRIORITY);
            captureThread.start();
        }
    }

    static SourceDataLine openSpeakerLine(String deviceId) {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, FORMAT);

        if (deviceId != null && !"default".equalsIgnoreCase(deviceId)) {
            if (deviceId.startsWith("pw:")) {
                activatePipeWireDevice(deviceId, false);
            } else {
                for (Mixer.Info mInfo : AudioSystem.getMixerInfo()) {
                    if (deviceId.equals(mInfo.getName())) {
                        try {
                            Mixer mixer = AudioSystem.getMixer(mInfo);
                            if (mixer.isLineSupported(info)) {
                                SourceDataLine line = (SourceDataLine) mixer.getLine(info);
                                line.open(FORMAT, SPEAKER_BUFFER_BYTES);
                                line.start();
                                System.out.println("[VoiceAudioEngine] Speaker opened on selected mixer: " + mInfo.getName());
                                return line;
                            }
                        } catch (Exception ex) {
                            System.err.println("[VoiceAudioEngine] Failed opening selected speaker [" + deviceId + "]: " + ex.getMessage());
                        }
                    }
                }
            }
        }

        // Prefer [default] PipeWire/PulseAudio mixer so OS routing is honored without locking raw ALSA hw
        for (Mixer.Info mInfo : AudioSystem.getMixerInfo()) {
            String name = mInfo.getName();
            if (name != null && name.contains("[default]")) {
                try {
                    Mixer mixer = AudioSystem.getMixer(mInfo);
                    if (mixer.isLineSupported(info)) {
                        SourceDataLine line = (SourceDataLine) mixer.getLine(info);
                        line.open(FORMAT, SPEAKER_BUFFER_BYTES);
                        line.start();
                        System.out.println("[VoiceAudioEngine] Speaker opened via PipeWire/OS [default] mixer: " + name);
                        return line;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // Try system default mixer
        try {
            if (AudioSystem.isLineSupported(info)) {
                SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(FORMAT, SPEAKER_BUFFER_BYTES);
                line.start();
                System.out.println("[VoiceAudioEngine] Speaker opened via default system mixer: " + line.getLineInfo());
                return line;
            }
        } catch (Exception ex) {
            System.err.println("[VoiceAudioEngine] Default speaker line unavailable: " + ex.getMessage());
        }

        // Fallback to any supported hardware mixer
        for (Mixer.Info mInfo : AudioSystem.getMixerInfo()) {
            String name = mInfo.getName();
            if (name == null || name.startsWith("Port")) continue;
            try {
                Mixer mixer = AudioSystem.getMixer(mInfo);
                if (mixer.isLineSupported(info)) {
                    SourceDataLine line = (SourceDataLine) mixer.getLine(info);
                    line.open(FORMAT, SPEAKER_BUFFER_BYTES);
                    line.start();
                    System.out.println("[VoiceAudioEngine] Speaker fallback opened on: " + mInfo.getName());
                    return line;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    static TargetDataLine openMicLine(String deviceId) {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);

        if (deviceId != null && !"default".equalsIgnoreCase(deviceId)) {
            if (deviceId.startsWith("pw:")) {
                activatePipeWireDevice(deviceId, true);
            } else {
                for (Mixer.Info mInfo : AudioSystem.getMixerInfo()) {
                    if (deviceId.equals(mInfo.getName())) {
                        try {
                            Mixer mixer = AudioSystem.getMixer(mInfo);
                            if (mixer.isLineSupported(info)) {
                                TargetDataLine line = (TargetDataLine) mixer.getLine(info);
                                line.open(FORMAT, MIC_BUFFER_BYTES);
                                line.start();
                                System.out.println("[VoiceAudioEngine] Mic opened on selected mixer: " + mInfo.getName());
                                return line;
                            }
                        } catch (Exception ex) {
                            System.err.println("[VoiceAudioEngine] Failed opening selected mic [" + deviceId + "]: " + ex.getMessage());
                        }
                    }
                }
            }
        }

        // Prefer [default] PipeWire/PulseAudio mixer so OS routing is honored without locking raw ALSA hw
        for (Mixer.Info mInfo : AudioSystem.getMixerInfo()) {
            String name = mInfo.getName();
            if (name != null && name.contains("[default]")) {
                try {
                    Mixer mixer = AudioSystem.getMixer(mInfo);
                    if (mixer.isLineSupported(info)) {
                        TargetDataLine line = (TargetDataLine) mixer.getLine(info);
                        line.open(FORMAT, MIC_BUFFER_BYTES);
                        line.start();
                        System.out.println("[VoiceAudioEngine] Mic opened via PipeWire/OS [default] mixer: " + name);
                        return line;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // Try system default mixer
        try {
            if (AudioSystem.isLineSupported(info)) {
                TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
                line.open(FORMAT, MIC_BUFFER_BYTES);
                line.start();
                System.out.println("[VoiceAudioEngine] Mic opened via default system mixer: " + line.getLineInfo());
                return line;
            }
        } catch (Exception ex) {
            System.err.println("[VoiceAudioEngine] Default mic line unavailable: " + ex.getMessage());
        }

        // Fallback to any supported hardware mixer
        for (Mixer.Info mInfo : AudioSystem.getMixerInfo()) {
            String name = mInfo.getName();
            if (name == null || name.startsWith("Port")) continue;
            try {
                Mixer mixer = AudioSystem.getMixer(mInfo);
                if (mixer.isLineSupported(info)) {
                    TargetDataLine line = (TargetDataLine) mixer.getLine(info);
                    line.open(FORMAT, MIC_BUFFER_BYTES);
                    line.start();
                    System.out.println("[VoiceAudioEngine] Mic fallback opened on: " + mInfo.getName());
                    return line;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private void captureLoop(TargetDataLine line, Consumer<String> onEncodedChunk, Consumer<Boolean> onSpeakingState) {
        byte[] buffer = new byte[CHUNK_BYTES];
        boolean wasSpeaking = false;
        long lastSoundTime = 0;

        while (running && this.micLine == line && line.isOpen()) {
            try {
                int read = line.read(buffer, 0, buffer.length);
                if (read > 0) {
                    double rms = computeRms(buffer, read);
                    this.currentMicRms = rms;

                    if (!muted) {
                        boolean isSound = rms > 150.0; // Sensitive AC-RMS threshold so soft syllables are not clipped
                        long now = System.currentTimeMillis();

                        if (isSound) {
                            lastSoundTime = now;
                        }

                        boolean speaking = (now - lastSoundTime) < 700; // 700ms hangover hold keeps inter-word pauses continuous
                        if (speaking != wasSpeaking) {
                            wasSpeaking = speaking;
                            if (onSpeakingState != null) {
                                onSpeakingState.accept(speaking);
                            }
                        }

                        // Voice Activity Gate (DTX): transmit continuously throughout the entire talkspurt
                        if (speaking && onEncodedChunk != null) {
                            byte[] slice = (read == buffer.length) ? buffer : java.util.Arrays.copyOf(buffer, read);
                            onEncodedChunk.accept(Base64.getEncoder().encodeToString(slice));
                        }
                    } else if (wasSpeaking) {
                        wasSpeaking = false;
                        lastSoundTime = 0;
                        if (onSpeakingState != null) {
                            onSpeakingState.accept(false);
                        }
                    }
                }
            } catch (Exception e) {
                break;
            }
        }

        this.currentMicRms = 0.0;
        if (wasSpeaking && onSpeakingState != null) {
            onSpeakingState.accept(false);
        }
    }

    private void playbackLoop() {
        boolean prebuffering = true;
        while (running) {
            try {
                if (prebuffering) {
                    // Pre-buffer 3 frames (120ms) before starting a talkspurt to absorb network jitter
                    if (playbackQueue.size() < PREBUFFER_FRAMES) {
                        Thread.sleep(15);
                        continue;
                    }
                    prebuffering = false;
                }
                byte[] pcm = playbackQueue.poll(140, TimeUnit.MILLISECONDS);
                if (pcm == null) {
                    prebuffering = true;
                    continue;
                }
                SourceDataLine out = this.speakerLine;
                if (out != null && out.isOpen()) {
                    out.write(pcm, 0, pcm.length);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ignored) {
            }
        }
    }

    public static double computeRms(byte[] pcmData, int length) {
        if (pcmData == null || length < 2) return 0;
        int samples = length / 2;
        long sum = 0;
        for (int i = 0; i < samples; i++) {
            int low = pcmData[i * 2] & 0xFF;
            int high = pcmData[i * 2 + 1];
            short sample = (short) ((high << 8) | low);
            sum += sample;
        }
        double mean = (double) sum / samples;

        double varSum = 0;
        for (int i = 0; i < samples; i++) {
            int low = pcmData[i * 2] & 0xFF;
            int high = pcmData[i * 2 + 1];
            short sample = (short) ((high << 8) | low);
            double diff = sample - mean;
            varSum += diff * diff;
        }
        return Math.sqrt(varSum / samples);
    }

    void playChunk(String base64Pcm) {
        if (!running || base64Pcm == null || base64Pcm.isEmpty()) return;
        try {
            byte[] pcm = Base64.getDecoder().decode(base64Pcm);
            while (!playbackQueue.offer(pcm)) {
                playbackQueue.poll(); // Drop only the single oldest 40ms frame on overflow instead of flushing line
            }
        } catch (Exception ex) {
            System.err.println("[VoiceAudioEngine] Error queueing audio chunk: " + ex.getMessage());
        }
    }

    void flushSpeaker() {
        playbackQueue.clear();
        SourceDataLine out = this.speakerLine;
        if (out != null && out.isOpen()) {
            try {
                out.flush();
            } catch (Exception ignored) {
            }
        }
    }

    void setMuted(boolean muted) {
        this.muted = muted;
        if (muted) {
            TargetDataLine in = this.micLine;
            if (in != null && in.isOpen()) {
                try {
                    in.flush();
                } catch (Exception ignored) {
                }
            }
            Consumer<Boolean> cb = this.activeSpeakingStateConsumer;
            if (cb != null) {
                cb.accept(false);
            }
        }
    }

    boolean isMuted() {
        return muted;
    }

    boolean isMicAvailable() {
        return micAvailable;
    }

    boolean isSpeakerAvailable() {
        return speakerAvailable;
    }

    boolean isRunning() {
        return running;
    }

    synchronized void stop() {
        running = false;
        playbackQueue.clear();
        closeCurrentMicLineAndWait();
        Thread oldPlay = this.playbackThread;
        this.playbackThread = null;
        if (oldPlay != null && oldPlay != Thread.currentThread()) {
            oldPlay.interrupt();
        }
        if (speakerLine != null) {
            try {
                speakerLine.flush();
                speakerLine.stop();
                speakerLine.close();
            } catch (Exception ignored) {
            }
            speakerLine = null;
        }
        micAvailable = false;
        speakerAvailable = false;
        currentMicRms = 0.0;
    }
}

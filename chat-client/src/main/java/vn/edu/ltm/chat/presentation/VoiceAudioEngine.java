package vn.edu.ltm.chat.presentation;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

/**
 * Real-time PCM 16kHz 16-bit mono voice capture & playback using JDK javax.sound.sampled.
 */
public final class VoiceAudioEngine {
    // shortcut: 16kHz 16-bit mono PCM over Base64 TCP (~32KB/s), upgrade to UDP Opus when scaling > 5 peers
    public static final AudioFormat FORMAT = new AudioFormat(16000.0f, 16, 1, true, false);
    public static final int CHUNK_BYTES = 3200; // 100ms frame at 16kHz 16-bit mono

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

    private TargetDataLine micLine;
    private SourceDataLine speakerLine;
    private Thread captureThread;
    private Consumer<String> activeEncodedChunkConsumer;
    private Consumer<Boolean> activeSpeakingStateConsumer;

    public static List<AudioDevice> getAvailableInputDevices() {
        List<AudioDevice> list = new ArrayList<>();
        list.add(new AudioDevice("default", "Mặc định hệ thống"));
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

    public static void testSpeaker(String deviceId) {
        new Thread(() -> {
            try {
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, FORMAT);
                SourceDataLine line = openSpeakerLine(deviceId);
                if (line == null) return;
                // Play a brief 2-tone melodic chime (587Hz -> 880Hz, 150ms each)
                byte[] buf = new byte[CHUNK_BYTES * 3];
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
        if (running) {
            if (micLine != null) {
                try {
                    micLine.stop();
                    micLine.close();
                } catch (Exception ignored) {
                }
                micLine = null;
            }
            micLine = openMicLine(selectedMicId);
            micAvailable = (micLine != null);
            if (micLine != null) {
                captureThread = new Thread(() -> captureLoop(activeEncodedChunkConsumer, activeSpeakingStateConsumer), "voice-mic-capture");
                captureThread.setDaemon(true);
                captureThread.start();
            }
        }
    }

    public synchronized void switchSpeakerDevice(String deviceId) {
        selectedSpeakerId = (deviceId != null && !deviceId.isBlank()) ? deviceId : "default";
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

    synchronized void start(Consumer<String> onEncodedChunk) {
        start(onEncodedChunk, null);
    }

    synchronized void start(Consumer<String> onEncodedChunk, Consumer<Boolean> onSpeakingState) {
        stop();
        running = true;
        this.activeEncodedChunkConsumer = onEncodedChunk;
        this.activeSpeakingStateConsumer = onSpeakingState;

        speakerLine = openSpeakerLine(selectedSpeakerId);
        speakerAvailable = (speakerLine != null);

        micLine = openMicLine(selectedMicId);
        micAvailable = (micLine != null);

        if (micLine != null) {
            captureThread = new Thread(() -> captureLoop(onEncodedChunk, onSpeakingState), "voice-mic-capture");
            captureThread.setDaemon(true);
            captureThread.start();
        }
    }

    private static SourceDataLine openSpeakerLine(String deviceId) {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, FORMAT);

        if (deviceId != null && !"default".equalsIgnoreCase(deviceId)) {
            for (Mixer.Info mInfo : AudioSystem.getMixerInfo()) {
                if (deviceId.equals(mInfo.getName())) {
                    try {
                        Mixer mixer = AudioSystem.getMixer(mInfo);
                        if (mixer.isLineSupported(info)) {
                            SourceDataLine line = (SourceDataLine) mixer.getLine(info);
                            line.open(FORMAT, CHUNK_BYTES * 4);
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

        // Try system default mixer
        try {
            if (AudioSystem.isLineSupported(info)) {
                SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(FORMAT, CHUNK_BYTES * 4);
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
                    line.open(FORMAT, CHUNK_BYTES * 4);
                    line.start();
                    System.out.println("[VoiceAudioEngine] Speaker fallback opened on: " + mInfo.getName());
                    return line;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static TargetDataLine openMicLine(String deviceId) {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);

        if (deviceId != null && !"default".equalsIgnoreCase(deviceId)) {
            for (Mixer.Info mInfo : AudioSystem.getMixerInfo()) {
                if (deviceId.equals(mInfo.getName())) {
                    try {
                        Mixer mixer = AudioSystem.getMixer(mInfo);
                        if (mixer.isLineSupported(info)) {
                            TargetDataLine line = (TargetDataLine) mixer.getLine(info);
                            line.open(FORMAT, CHUNK_BYTES * 4);
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

        // Try system default mixer
        try {
            if (AudioSystem.isLineSupported(info)) {
                TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
                line.open(FORMAT, CHUNK_BYTES * 4);
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
                    line.open(FORMAT, CHUNK_BYTES * 4);
                    line.start();
                    System.out.println("[VoiceAudioEngine] Mic fallback opened on: " + mInfo.getName());
                    return line;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private void captureLoop(Consumer<String> onEncodedChunk, Consumer<Boolean> onSpeakingState) {
        byte[] buffer = new byte[CHUNK_BYTES];
        boolean wasSpeaking = false;
        long lastSoundTime = 0;

        while (running && micLine != null && micLine.isOpen()) {
            try {
                int read = micLine.read(buffer, 0, buffer.length);
                if (read > 0 && !muted) {
                    double rms = computeRms(buffer, read);
                    boolean isSound = rms > 350.0; // Voice activity threshold with AC-RMS
                    long now = System.currentTimeMillis();

                    if (isSound) {
                        lastSoundTime = now;
                    }

                    boolean speaking = (now - lastSoundTime) < 400; // Hold speaking state for 400ms to avoid flicker
                    if (speaking != wasSpeaking) {
                        wasSpeaking = speaking;
                        if (onSpeakingState != null) {
                            onSpeakingState.accept(speaking);
                        }
                    }

                    if (onEncodedChunk != null) {
                        byte[] slice = (read == buffer.length) ? buffer : java.util.Arrays.copyOf(buffer, read);
                        onEncodedChunk.accept(Base64.getEncoder().encodeToString(slice));
                    }
                } else if (muted && wasSpeaking) {
                    wasSpeaking = false;
                    if (onSpeakingState != null) {
                        onSpeakingState.accept(false);
                    }
                }
            } catch (Exception e) {
                break;
            }
        }

        if (wasSpeaking && onSpeakingState != null) {
            onSpeakingState.accept(false);
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
        SourceDataLine out = this.speakerLine;
        if (out == null || !out.isOpen()) return;
        try {
            byte[] pcm = Base64.getDecoder().decode(base64Pcm);
            out.write(pcm, 0, pcm.length);
        } catch (Exception ex) {
            System.err.println("[VoiceAudioEngine] Error playing audio chunk: " + ex.getMessage());
        }
    }

    void setMuted(boolean muted) {
        this.muted = muted;
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
        if (micLine != null) {
            try {
                micLine.stop();
                micLine.close();
            } catch (Exception ignored) {
            }
            micLine = null;
        }
        if (speakerLine != null) {
            try {
                speakerLine.stop();
                speakerLine.close();
            } catch (Exception ignored) {
            }
            speakerLine = null;
        }
        micAvailable = false;
        speakerAvailable = false;
    }
}


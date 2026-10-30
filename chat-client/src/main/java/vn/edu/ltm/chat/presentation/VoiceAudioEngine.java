package vn.edu.ltm.chat.presentation;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.Base64;
import java.util.function.Consumer;

/**
 * Real-time PCM 8kHz 16-bit mono voice capture & playback using JDK javax.sound.sampled.
 */
final class VoiceAudioEngine {
    // shortcut: 8kHz 16-bit mono PCM over Base64 TCP (~16KB/s), upgrade to UDP Opus when scaling > 5 peers
    private static final AudioFormat FORMAT = new AudioFormat(8000.0f, 16, 1, true, false);
    private static final int CHUNK_BYTES = 1600; // 100ms frame

    private volatile boolean running = false;
    private volatile boolean muted = false;
    private volatile boolean micAvailable = false;
    private volatile boolean speakerAvailable = false;

    private TargetDataLine micLine;
    private SourceDataLine speakerLine;
    private Thread captureThread;

    synchronized void start(Consumer<String> onEncodedChunk) {
        start(onEncodedChunk, null);
    }

    synchronized void start(Consumer<String> onEncodedChunk, Consumer<Boolean> onSpeakingState) {
        stop();
        running = true;

        try {
            DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, FORMAT);
            if (AudioSystem.isLineSupported(speakerInfo)) {
                speakerLine = (SourceDataLine) AudioSystem.getLine(speakerInfo);
                speakerLine.open(FORMAT, CHUNK_BYTES * 4);
                speakerLine.start();
                speakerAvailable = true;
            }
        } catch (Exception ignored) {
            speakerAvailable = false;
        }

        try {
            DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, FORMAT);
            if (AudioSystem.isLineSupported(micInfo)) {
                micLine = (TargetDataLine) AudioSystem.getLine(micInfo);
                micLine.open(FORMAT, CHUNK_BYTES * 4);
                micLine.start();
                micAvailable = true;

                captureThread = new Thread(() -> captureLoop(onEncodedChunk, onSpeakingState), "voice-mic-capture");
                captureThread.setDaemon(true);
                captureThread.start();
            }
        } catch (Exception ignored) {
            micAvailable = false;
        }
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
                    boolean isSound = rms > 300.0; // Voice activity threshold for 16-bit PCM
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
        long sum = 0;
        int samples = length / 2;
        for (int i = 0; i < samples; i++) {
            // Little-endian 16-bit PCM
            int low = pcmData[i * 2] & 0xFF;
            int high = pcmData[i * 2 + 1];
            short sample = (short) ((high << 8) | low);
            sum += (long) sample * sample;
        }
        return Math.sqrt((double) sum / samples);
    }

    void playChunk(String base64Pcm) {
        if (!running || base64Pcm == null || base64Pcm.isEmpty()) return;
        SourceDataLine out = this.speakerLine;
        if (out == null || !out.isOpen()) return;
        try {
            byte[] pcm = Base64.getDecoder().decode(base64Pcm);
            out.write(pcm, 0, pcm.length);
        } catch (Exception ignored) {
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


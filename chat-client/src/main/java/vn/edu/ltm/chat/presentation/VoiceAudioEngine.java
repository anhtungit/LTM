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

                captureThread = new Thread(() -> captureLoop(onEncodedChunk), "voice-mic-capture");
                captureThread.setDaemon(true);
                captureThread.start();
            }
        } catch (Exception ignored) {
            micAvailable = false;
        }
    }

    private void captureLoop(Consumer<String> onEncodedChunk) {
        byte[] buffer = new byte[CHUNK_BYTES];
        while (running && micLine != null && micLine.isOpen()) {
            try {
                int read = micLine.read(buffer, 0, buffer.length);
                if (read > 0 && !muted && onEncodedChunk != null) {
                    byte[] slice = (read == buffer.length) ? buffer : java.util.Arrays.copyOf(buffer, read);
                    onEncodedChunk.accept(Base64.getEncoder().encodeToString(slice));
                }
            } catch (Exception e) {
                break;
            }
        }
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

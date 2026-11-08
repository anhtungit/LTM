package vn.edu.ltm.chat.media;

import com.github.sarxos.webcam.Webcam;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Real-time MJPEG webcam capture engine (HD 1280x720 @ 30fps) with multi-instance shared frame support.
 * Uses native DirectShow/V4L2 via Sarxos Webcam Capture (zero external install) with FFmpeg fallback.
 */
public final class VideoCaptureEngine {
    // HD 720p @ 30fps (~30-38KB/frame over UDP, within 65507 byte IP payload)
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int FPS = 30;
    private static final int FRAME_INTERVAL_MS = 1000 / FPS; // ~33ms
    private static final Path SHARED_FRAME_PATH = Path.of(System.getProperty("java.io.tmpdir"), "ltm_shared_webcam.jpg");
    private static volatile String selectedCameraId = "default";

    public record CameraDevice(String id, String displayName) {
        @Override
        public String toString() {
            return displayName;
        }
    }

    private volatile boolean running = false;
    private volatile Process captureProcess;
    private volatile Webcam activeWebcam;
    private Thread captureThread;

    public static List<CameraDevice> getAvailableCameras() {
        List<CameraDevice> list = new ArrayList<>();
        list.add(new CameraDevice("default", "Mặc định hệ thống"));

        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("linux")) {
            File sysClass = new File("/sys/class/video4linux");
            File[] nodes = sysClass.listFiles();
            if (nodes != null) {
                java.util.Arrays.sort(nodes, java.util.Comparator.comparing(File::getName));
                Set<String> seenNames = new LinkedHashSet<>();
                for (File node : nodes) {
                    String devName = node.getName();
                    File devFile = new File("/dev/" + devName);
                    if (!devFile.exists()) continue;
                    File idxFile = new File(node, "index");
                    if (idxFile.isFile()) {
                        try {
                            String idx = Files.readString(idxFile.toPath()).trim();
                            if (!"0".equals(idx)) continue;
                        } catch (Exception ignored) {
                        }
                    }
                    String friendly = devName;
                    File nameFile = new File(node, "name");
                    if (nameFile.isFile()) {
                        try {
                            String raw = Files.readString(nameFile.toPath()).trim();
                            if (!raw.isEmpty()) {
                                friendly = raw.split(":")[0].trim().replace('_', ' ');
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    String devPath = "/dev/" + devName;
                    if (seenNames.add(friendly)) {
                        list.add(new CameraDevice(devPath, friendly + " (" + devPath + ")"));
                    }
                }
            }
            return list;
        }

        try {
            List<Webcam> webcams = Webcam.getWebcams();
            if (webcams != null && !webcams.isEmpty()) {
                Set<String> seen = new LinkedHashSet<>();
                for (Webcam w : webcams) {
                    String name = w.getName();
                    if (name != null && seen.add(name)) {
                        list.add(new CameraDevice(name, name));
                    }
                }
                return list;
            }
        } catch (Throwable ignored) {
        }
        return list;
    }

    public static String getSelectedCameraId() {
        return selectedCameraId;
    }

    public static void setSelectedCameraId(String cameraId) {
        selectedCameraId = (cameraId != null && !cameraId.isBlank()) ? cameraId : "default";
    }

    public synchronized void start(Consumer<byte[]> onJpegFrame, Consumer<BufferedImage> onLocalPreview) {
        stop();
        running = true;
        captureThread = new Thread(() -> captureLoop(onJpegFrame, onLocalPreview), "video-webcam-capture");
        captureThread.setDaemon(true);
        captureThread.start();
    }

    public synchronized void stop() {
        running = false;
        Webcam wc = this.activeWebcam;
        this.activeWebcam = null;
        if (wc != null) {
            try {
                wc.close();
            } catch (Throwable ignored) {
            }
        }
        Process proc = this.captureProcess;
        this.captureProcess = null;
        if (proc != null) {
            proc.destroyForcibly();
        }
        Thread t = this.captureThread;
        this.captureThread = null;
        if (t != null && t != Thread.currentThread()) {
            t.interrupt();
            try {
                t.join(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        cleanSharedFrame();
    }

    private static void cleanSharedFrame() {
        try {
            Files.deleteIfExists(SHARED_FRAME_PATH);
            Files.deleteIfExists(Path.of(SHARED_FRAME_PATH.toString() + ".tmp"));
        } catch (Exception ignored) {
        }
    }

    public boolean isRunning() {
        return running;
    }

    private void captureLoop(Consumer<byte[]> onJpegFrame, Consumer<BufferedImage> onLocalPreview) {
        String os = System.getProperty("os.name", "").toLowerCase();
        boolean isLinux = os.contains("linux");

        if (isLinux) {
            // On Linux, use FFmpeg direct V4L2 capture (avoids BridJ native hangs & /dev/video lock issues)
            if (captureViaFfmpeg(onJpegFrame, onLocalPreview)) {
                return;
            }
        } else {
            // On Windows / macOS, use native Sarxos (DirectShow)
            if (captureViaSarxos(onJpegFrame, onLocalPreview)) {
                return;
            }
            if (captureViaFfmpeg(onJpegFrame, onLocalPreview)) {
                return;
            }
        }

        // Fallback when camera device is busy or when 2nd client runs on same machine
        if (running) {
            fallbackLoop(onJpegFrame, onLocalPreview);
        }
    }

    private boolean captureViaSarxos(Consumer<byte[]> onJpegFrame, Consumer<BufferedImage> onLocalPreview) {
        Webcam webcam = null;
        try {
            String sel = selectedCameraId;
            if (sel != null && !sel.equalsIgnoreCase("default")) {
                for (Webcam w : Webcam.getWebcams()) {
                    if (sel.equalsIgnoreCase(w.getName())) {
                        webcam = w;
                        break;
                    }
                }
            }
            if (webcam == null) {
                webcam = Webcam.getDefault();
            }
            if (webcam == null) {
                return false;
            }

            webcam.setCustomViewSizes(new Dimension[] { new Dimension(WIDTH, HEIGHT) });
            webcam.setViewSize(new Dimension(WIDTH, HEIGHT));
            if (!webcam.open()) {
                return false;
            }
            this.activeWebcam = webcam;

            boolean capturedAny = false;
            while (running && webcam.isOpen()) {
                BufferedImage raw = webcam.getImage();
                if (!running) break;
                if (raw != null) {
                    capturedAny = true;
                    // Scale if driver returned different resolution
                    BufferedImage frame = (raw.getWidth() == WIDTH && raw.getHeight() == HEIGHT) ? raw : scaleFrame(raw);
                    byte[] jpeg = encodeToJpeg(frame);
                    if (running && jpeg != null) {
                        publishSharedFrame(jpeg);
                        if (onLocalPreview != null) onLocalPreview.accept(frame);
                        if (onJpegFrame != null) onJpegFrame.accept(jpeg);
                    }
                }
                try {
                    Thread.sleep(FRAME_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return capturedAny;
        } catch (Throwable t) {
            return false;
        } finally {
            if (webcam != null) {
                try {
                    webcam.close();
                } catch (Throwable ignored) {
                }
            }
            this.activeWebcam = null;
        }
    }

    private boolean captureViaFfmpeg(Consumer<byte[]> onJpegFrame, Consumer<BufferedImage> onLocalPreview) {
        Process proc = startFfmpegProcess();
        this.captureProcess = proc;
        boolean capturedAnyHardwareFrame = false;

        if (proc != null) {
            try (InputStream in = proc.getInputStream()) {
                ByteArrayOutputStream frameBuf = new ByteArrayOutputStream(16384);
                byte[] readBuf = new byte[4096];
                boolean inFrame = false;
                int prevByte = -1;

                while (running && proc.isAlive()) {
                    int n = in.read(readBuf);
                    if (n < 0) break;
                    for (int i = 0; i < n && running; i++) {
                        int b = readBuf[i] & 0xFF;
                        if (!inFrame) {
                            if (prevByte == 0xFF && b == 0xD8) {
                                inFrame = true;
                                frameBuf.reset();
                                frameBuf.write(0xFF);
                                frameBuf.write(0xD8);
                            }
                        } else {
                            frameBuf.write(b);
                            if (prevByte == 0xFF && b == 0xD9) {
                                inFrame = false;
                                if (!running) break;
                                byte[] jpeg = frameBuf.toByteArray();
                                capturedAnyHardwareFrame = true;
                                publishSharedFrame(jpeg);
                                dispatchFrame(jpeg, onJpegFrame, onLocalPreview);
                            } else if (frameBuf.size() > 500_000) {
                                inFrame = false;
                                frameBuf.reset();
                            }
                        }
                        prevByte = b;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                proc.destroyForcibly();
            }
        }
        return capturedAnyHardwareFrame;
    }

    private static BufferedImage scaleFrame(BufferedImage src) {
        BufferedImage dest = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dest.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, WIDTH, HEIGHT, null);
        g.dispose();
        return dest;
    }

    private static byte[] encodeToJpeg(BufferedImage img) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(8192);
            ImageIO.write(img, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private void fallbackLoop(Consumer<byte[]> onJpegFrame, Consumer<BufferedImage> onLocalPreview) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        int tick = 0;
        while (running) {
            try {
                // 1. If another local client instance is actively writing /tmp/ltm_shared_webcam.jpg, mirror it
                if (Files.isRegularFile(SHARED_FRAME_PATH)) {
                    long ageMs = System.currentTimeMillis() - Files.getLastModifiedTime(SHARED_FRAME_PATH).toMillis();
                    if (ageMs >= 0 && ageMs < 2000) {
                        byte[] sharedJpeg = Files.readAllBytes(SHARED_FRAME_PATH);
                        if (sharedJpeg.length > 100) {
                            dispatchFrame(sharedJpeg, onJpegFrame, onLocalPreview);
                            Thread.sleep(FRAME_INTERVAL_MS);
                            continue;
                        }
                    }
                }

                // 2. Synthetic camera preview card when no physical camera is available
                BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setPaint(new GradientPaint(0, 0, new Color(0x1E, 0x22, 0x2D), WIDTH, HEIGHT, new Color(0x2B, 0x31, 0x42)));
                g.fillRect(0, 0, WIDTH, HEIGHT);

                int pulse = (int) (Math.sin(tick * 0.35) * 16);
                g.setColor(new Color(0x58, 0x65, 0xF2));
                g.fillOval(WIDTH / 2 - 72 - pulse / 2, HEIGHT / 2 - 96 - pulse / 2, 144 + pulse, 144 + pulse);

                g.setColor(Color.WHITE);
                g.setFont(new Font("SansSerif", Font.BOLD, 26));
                g.drawString("LTM HD 720p LIVE CAMERA", WIDTH / 2 - 180, HEIGHT / 2 + 80);
                g.setFont(new Font("Monospaced", Font.PLAIN, 18));
                g.setColor(new Color(0x57, 0xF2, 0x87));
                g.drawString("● REC 30FPS " + LocalTime.now().format(fmt), WIDTH / 2 - 120, HEIGHT / 2 + 115);
                g.dispose();

                ByteArrayOutputStream baos = new ByteArrayOutputStream(32768);
                ImageIO.write(img, "jpg", baos);
                if (!running) break;
                byte[] jpeg = baos.toByteArray();
                if (onLocalPreview != null) {
                    onLocalPreview.accept(img);
                }
                if (onJpegFrame != null) {
                    onJpegFrame.accept(jpeg);
                }
                tick++;
                Thread.sleep(FRAME_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ignored) {
            }
        }
    }

    private static void publishSharedFrame(byte[] jpeg) {
        try {
            Path tmp = Path.of(SHARED_FRAME_PATH.toString() + ".tmp");
            Files.write(tmp, jpeg);
            Files.move(tmp, SHARED_FRAME_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ignored) {
        }
    }

    private void dispatchFrame(byte[] jpeg, Consumer<byte[]> onJpegFrame, Consumer<BufferedImage> onLocalPreview) {
        if (!running) return;
        if (onLocalPreview != null) {
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpeg));
                if (running && img != null) {
                    onLocalPreview.accept(img);
                }
            } catch (Exception ignored) {
            }
        }
        if (running && onJpegFrame != null) {
            onJpegFrame.accept(jpeg);
        }
    }

    private static Process startFfmpegProcess() {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("linux")) {
                String dev = resolveLinuxDevice();
                if (dev == null) return null;
                return new ProcessBuilder(
                        "ffmpeg", "-nostdin", "-loglevel", "quiet",
                        "-f", "v4l2", "-video_size", WIDTH + "x" + HEIGHT, "-i", dev,
                        "-vf", "fps=" + FPS + ",scale=" + WIDTH + ":" + HEIGHT + ",hflip",
                        "-f", "image2pipe", "-vcodec", "mjpeg", "-q:v", "6", "-"
                ).redirectErrorStream(false).start();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String resolveLinuxDevice() {
        String sel = selectedCameraId;
        if (sel != null && !sel.equalsIgnoreCase("default") && new File(sel).exists()) {
            return sel;
        }
        for (int i = 0; i < 4; i++) {
            String candidate = "/dev/video" + i;
            if (new File(candidate).exists()) {
                return candidate;
            }
        }
        return null;
    }
}

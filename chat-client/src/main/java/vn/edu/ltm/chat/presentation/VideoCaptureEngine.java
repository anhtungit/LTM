package vn.edu.ltm.chat.presentation;

import javax.imageio.ImageIO;
import java.awt.Color;
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
 * Real-time MJPEG webcam capture engine (320x240 @ 10fps) with multi-instance shared frame support.
 */
public final class VideoCaptureEngine {
    // shortcut: 320x240 MJPEG @ 10fps (~3.5KB/frame over UDP), upgrade to H.264/VP8 RTP when scaling > 4 video peers
    private static final int WIDTH = 320;
    private static final int HEIGHT = 240;
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
                    String devName = node.getName(); // e.g. video0
                    File devFile = new File("/dev/" + devName);
                    if (!devFile.exists()) continue;
                    // Skip metadata secondary nodes (index > 0) when index file is present
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
    }

    public boolean isRunning() {
        return running;
    }

    private void captureLoop(Consumer<byte[]> onJpegFrame, Consumer<BufferedImage> onLocalPreview) {
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
                                byte[] jpeg = frameBuf.toByteArray();
                                capturedAnyHardwareFrame = true;
                                publishSharedFrame(jpeg);
                                dispatchFrame(jpeg, onJpegFrame, onLocalPreview);
                            } else if (frameBuf.size() > 60000) {
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

        // Fallback when 2nd client runs on same machine (/dev/video0 busy) or no hardware webcam
        if (running && !capturedAnyHardwareFrame) {
            fallbackLoop(onJpegFrame, onLocalPreview);
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
                            Thread.sleep(100);
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

                int pulse = (int) (Math.sin(tick * 0.35) * 8);
                g.setColor(new Color(0x58, 0x65, 0xF2));
                g.fillOval(WIDTH / 2 - 36 - pulse / 2, HEIGHT / 2 - 48 - pulse / 2, 72 + pulse, 72 + pulse);

                g.setColor(Color.WHITE);
                g.setFont(new Font("SansSerif", Font.BOLD, 14));
                g.drawString("LTM LIVE CAMERA", WIDTH / 2 - 66, HEIGHT / 2 + 48);
                g.setFont(new Font("Monospaced", Font.PLAIN, 12));
                g.setColor(new Color(0x57, 0xF2, 0x87));
                g.drawString("● REC " + LocalTime.now().format(fmt), WIDTH / 2 - 54, HEIGHT / 2 + 68);
                g.dispose();

                ByteArrayOutputStream baos = new ByteArrayOutputStream(8192);
                ImageIO.write(img, "jpg", baos);
                byte[] jpeg = baos.toByteArray();
                if (onLocalPreview != null) {
                    onLocalPreview.accept(img);
                }
                if (onJpegFrame != null) {
                    onJpegFrame.accept(jpeg);
                }
                tick++;
                Thread.sleep(100);
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

    private static void dispatchFrame(byte[] jpeg, Consumer<byte[]> onJpegFrame, Consumer<BufferedImage> onLocalPreview) {
        if (onLocalPreview != null) {
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpeg));
                if (img != null) {
                    onLocalPreview.accept(img);
                }
            } catch (Exception ignored) {
            }
        }
        if (onJpegFrame != null) {
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
                        "-f", "v4l2", "-i", dev,
                        "-vf", "fps=10,scale=" + WIDTH + ":" + HEIGHT + ",hflip",
                        "-f", "image2pipe", "-vcodec", "mjpeg", "-q:v", "14", "-"
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

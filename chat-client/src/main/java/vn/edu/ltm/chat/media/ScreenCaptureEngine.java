package vn.edu.ltm.chat.media;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.function.Consumer;

/**
 * Thu hình màn hình thời gian thực (Screen Capture) bằng Robot và nén JPEG gửi qua kênh cuộc gọi.
 */
public final class ScreenCaptureEngine {

    private volatile boolean running = false;
    private Thread captureThread;
    private int targetFps = 15;
    private int maxWidth = 1280;
    private int maxHeight = 720;
    private float jpegQuality = 0.65f;

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized void setConfig(int fps, int maxWidth, int maxHeight) {
        this.targetFps = Math.max(5, Math.min(30, fps));
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
    }

    public synchronized void start(Consumer<byte[]> onJpegFrame, Consumer<BufferedImage> onLocalPreview) {
        stop();
        this.running = true;

        captureThread = new Thread(() -> {
            Robot robot;
            try {
                robot = new Robot();
            } catch (Throwable t) {
                System.err.println("[ScreenCaptureEngine] Không khởi tạo được Robot capture: " + t.getMessage());
                running = false;
                return;
            }

            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            Rectangle screenRect = new Rectangle(0, 0, screenSize.width, screenSize.height);
            long frameIntervalMs = 1000L / targetFps;

            ImageWriter writer = null;
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (writers.hasNext()) {
                writer = writers.next();
            }

            while (running) {
                long start = System.currentTimeMillis();
                try {
                    BufferedImage raw = robot.createScreenCapture(screenRect);
                    if (raw != null) {
                        BufferedImage scaled = scaleIfNeeded(raw, maxWidth, maxHeight);
                        if (onLocalPreview != null) {
                            onLocalPreview.accept(scaled);
                        }
                        if (onJpegFrame != null) {
                            byte[] jpeg = compressJpeg(scaled, writer, jpegQuality);
                            if (jpeg != null && jpeg.length > 0) {
                                onJpegFrame.accept(jpeg);
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }

                long elapsed = System.currentTimeMillis() - start;
                long sleep = frameIntervalMs - elapsed;
                if (sleep > 0) {
                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            if (writer != null) {
                writer.dispose();
            }
        }, "screen-capture-thread");

        captureThread.setDaemon(true);
        captureThread.setPriority(Thread.NORM_PRIORITY);
        captureThread.start();
    }

    public synchronized void stop() {
        this.running = false;
        Thread t = this.captureThread;
        this.captureThread = null;
        if (t != null && t != Thread.currentThread()) {
            t.interrupt();
            try {
                t.join(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static BufferedImage scaleIfNeeded(BufferedImage src, int maxW, int maxH) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxW && h <= maxH) {
            return src;
        }
        double scale = Math.min((double) maxW / w, (double) maxH / h);
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));

        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    private static byte[] compressJpeg(BufferedImage img, ImageWriter writer, float quality) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if (writer != null) {
                try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(baos)) {
                    writer.setOutput(ios);
                    ImageWriteParam param = writer.getDefaultWriteParam();
                    if (param.canWriteCompressed()) {
                        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        param.setCompressionQuality(quality);
                    }
                    writer.write(null, new IIOImage(img, null, null), param);
                }
            } else {
                ImageIO.write(img, "jpg", baos);
            }
            return baos.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }
}

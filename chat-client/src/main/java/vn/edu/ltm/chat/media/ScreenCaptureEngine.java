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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.util.Iterator;
import java.util.function.Consumer;

/**
 * Thu hình màn hình / cửa sổ thời gian thực (Screen & Window Capture).
 * - Trên Linux Wayland: Sử dụng XDG Desktop Portal (org.freedesktop.portal.ScreenCast) + PipeWire + GStreamer
 *   để chọn Màn hình hoặc Cửa sổ ứng dụng cụ thể chỉ với 1 lần cấp quyền duy nhất (giống Discord), không bị màn hình đen.
 * - Trên Windows / macOS / X11: Sử dụng java.awt.Robot.
 */
public final class ScreenCaptureEngine {

    public static final int SOURCE_SCREEN = 1;
    public static final int SOURCE_WINDOW = 2;

    private volatile boolean running = false;
    private Thread captureThread;
    private volatile Process portalProcess;
    private int sourceType = SOURCE_SCREEN;
    private int targetFps = 30;
    private int maxWidth = 1280;
    private int maxHeight = 720;
    private float jpegQuality = 0.55f;

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized void setSourceType(int sourceType) {
        this.sourceType = (sourceType == SOURCE_WINDOW) ? SOURCE_WINDOW : SOURCE_SCREEN;
    }

    public synchronized int getSourceType() {
        return sourceType;
    }

    public synchronized int getTargetFps() {
        return targetFps;
    }

    public synchronized int getMaxHeight() {
        return maxHeight;
    }

    public synchronized void setConfig(int fps, int maxWidth, int maxHeight) {
        this.targetFps = Math.max(5, Math.min(30, fps));
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
    }

    public synchronized void start(Consumer<byte[]> onJpegFrame, Consumer<BufferedImage> onLocalPreview) {
        start(onJpegFrame, onLocalPreview, null);
    }

    public synchronized void start(Consumer<byte[]> onJpegFrame, Consumer<BufferedImage> onLocalPreview, Runnable onStopped) {
        stop();
        this.running = true;
        final int srcType = this.sourceType;
        final int fps = this.targetFps;
        final int maxW = this.maxWidth;
        final int maxH = this.maxHeight;
        final float qual = this.jpegQuality;

        captureThread = new Thread(() -> {
            try {
                if (isWaylandSession()) {
                    runWaylandPortalCapture(srcType, fps, maxW, maxH, qual, onJpegFrame, onLocalPreview);
                } else {
                    runRobotCapture(fps, maxW, maxH, qual, onJpegFrame, onLocalPreview);
                }
            } finally {
                boolean wasRunning = this.running;
                this.running = false;
                destroyPortalProcess();
                if (wasRunning && onStopped != null) {
                    onStopped.run();
                }
            }
        }, "screen-capture-thread");

        captureThread.setDaemon(true);
        captureThread.setPriority(Thread.NORM_PRIORITY);
        captureThread.start();
    }

    public synchronized void stop() {
        this.running = false;
        destroyPortalProcess();
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

    private void destroyPortalProcess() {
        Process p = this.portalProcess;
        this.portalProcess = null;
        if (p != null) {
            try {
                p.getOutputStream().close();
            } catch (Throwable ignored) {
            }
            p.destroy();
            try {
                p.destroyForcibly();
            } catch (Throwable ignored) {
            }
        }
    }

    static {
        if (isLinux()) {
            ensureDesktopEntry();
        }
    }

    private static boolean isLinux() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("linux");
    }

    private static void ensureDesktopEntry() {
        try {
            String home = System.getProperty("user.home");
            if (home == null || home.isBlank()) return;
            java.nio.file.Path appDir = java.nio.file.Path.of(home, ".local", "share", "applications");
            java.nio.file.Files.createDirectories(appDir);
            java.nio.file.Path desktopFile = appDir.resolve("vn.edu.ltm.chat.desktop");
            if (!java.nio.file.Files.exists(desktopFile)) {
                String content = """
                        [Desktop Entry]
                        Name=LTM Chat
                        Comment=LTM Multi-threaded Chat
                        Exec=java
                        Terminal=false
                        Type=Application
                        Categories=Network;InstantMessaging;
                        """;
                java.nio.file.Files.writeString(desktopFile, content);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isWaylandSession() {
        String wayland = System.getenv("WAYLAND_DISPLAY");
        String session = System.getenv("XDG_SESSION_TYPE");
        return (wayland != null && !wayland.isBlank()) || "wayland".equalsIgnoreCase(session);
    }

    private void runWaylandPortalCapture(int srcType, int fps, int maxW, int maxH, float qual,
                                         Consumer<byte[]> onJpegFrame, Consumer<BufferedImage> onLocalPreview) {
        int qPercent = Math.max(30, Math.min(90, Math.round(qual * 100f)));
        java.util.List<String> cmd = new java.util.ArrayList<>();
        if (isLinux() && new java.io.File("/usr/bin/systemd-run").exists()) {
            cmd.add("systemd-run");
            cmd.add("--user");
            cmd.add("--scope");
            cmd.add("--quiet");
            cmd.add("--unit=app-vn.edu.ltm.chat-" + System.currentTimeMillis());
        }
        cmd.add("python3");
        cmd.add("-u");
        cmd.add("-c");
        cmd.add(WAYLAND_PORTAL_PY);
        cmd.add(Integer.toString(srcType));
        cmd.add(Integer.toString(fps));
        cmd.add(Integer.toString(qPercent));
        cmd.add(Integer.toString(maxW));
        cmd.add(Integer.toString(maxH));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        try {
            Process proc = pb.start();
            this.portalProcess = proc;
            try (DataInputStream dis = new DataInputStream(proc.getInputStream())) {
                while (running && proc.isAlive()) {
                    int len;
                    try {
                        len = dis.readInt();
                    } catch (EOFException eof) {
                        break;
                    }
                    if (len <= 0 || len > 10_000_000) {
                        break;
                    }
                    byte[] rawJpeg = new byte[len];
                    dis.readFully(rawJpeg);
                    if (!running) break;

                    if (onJpegFrame != null) {
                        onJpegFrame.accept(rawJpeg);
                    }
                    if (onLocalPreview != null && running) {
                        try {
                            BufferedImage img = ImageIO.read(new ByteArrayInputStream(rawJpeg));
                            if (img != null && running) {
                                onLocalPreview.accept(img);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        } catch (Throwable t) {
            System.err.println("[ScreenCaptureEngine] Lỗi thu hình Wayland Portal: " + t.getMessage());
        }
    }

    private void runRobotCapture(int fps, int maxW, int maxH, float qual,
                                Consumer<byte[]> onJpegFrame, Consumer<BufferedImage> onLocalPreview) {
        Robot robot;
        try {
            robot = new Robot();
        } catch (Throwable t) {
            System.err.println("[ScreenCaptureEngine] Không khởi tạo được Robot capture: " + t.getMessage());
            return;
        }

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Rectangle screenRect = new Rectangle(0, 0, screenSize.width, screenSize.height);
        long frameIntervalMs = 1000L / fps;
        ImageWriter writer = createJpegWriter();

        try {
            while (running) {
                long start = System.currentTimeMillis();
                try {
                    BufferedImage raw = robot.createScreenCapture(screenRect);
                    if (raw == null) break;
                    BufferedImage scaled = scaleIfNeeded(raw, maxW, maxH);
                    if (onLocalPreview != null) {
                        onLocalPreview.accept(scaled);
                    }
                    if (onJpegFrame != null) {
                        byte[] jpeg = compressJpeg(scaled, writer, qual);
                        if (jpeg != null && jpeg.length > 0) {
                            onJpegFrame.accept(jpeg);
                        }
                    }
                } catch (Throwable t) {
                    break;
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
        } finally {
            if (writer != null) {
                writer.dispose();
            }
        }
    }

    private static ImageWriter createJpegWriter() {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        return writers.hasNext() ? writers.next() : null;
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

    private static final String WAYLAND_PORTAL_PY = """
            import sys, os, struct, random, threading
            import gi
            gi.require_version('Gst', '1.0')
            from gi.repository import Gst, Gio, GLib

            try:
                import ctypes
                libc = ctypes.CDLL('libc.so.6')
                libc.prctl(15, b'LTM Chat', 0, 0, 0)
            except Exception:
                pass
            GLib.set_prgname('LTM Chat')
            GLib.set_application_name('LTM Chat')

            Gst.init(None)
            src_type = int(sys.argv[1]) if len(sys.argv) > 1 else 1
            fps = int(sys.argv[2]) if len(sys.argv) > 2 else 30
            quality = int(sys.argv[3]) if len(sys.argv) > 3 else 55
            max_w = int(sys.argv[4]) if len(sys.argv) > 4 else 1280
            max_h = int(sys.argv[5]) if len(sys.argv) > 5 else 720

            import signal, time
            try:
                import ctypes
                libc = ctypes.CDLL('libc.so.6')
                libc.prctl(1, signal.SIGTERM, 0, 0, 0)
            except Exception:
                pass

            ppid = os.getppid()
            def watch_parent():
                while True:
                    time.sleep(1)
                    if os.getppid() != ppid:
                        os._exit(0)

            threading.Thread(target=watch_parent, daemon=True).start()

            bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
            sender = bus.get_unique_name().lstrip(':').replace('.', '_')

            def call_portal(method, args_variant, token):
                req_path = f'/org/freedesktop/portal/desktop/request/{sender}/{token}'
                loop = GLib.MainLoop()
                res_holder = {}
                def on_resp(conn, s, path, iface, sig, params):
                    code, out = params.unpack()
                    res_holder['code'] = code
                    res_holder['out'] = out
                    loop.quit()
                sub = bus.signal_subscribe(
                    'org.freedesktop.portal.Desktop',
                    'org.freedesktop.portal.Request',
                    'Response',
                    req_path,
                    None,
                    Gio.DBusSignalFlags.NO_MATCH_RULE,
                    on_resp
                )
                bus.call_sync(
                    'org.freedesktop.portal.Desktop',
                    '/org/freedesktop/portal/desktop',
                    'org.freedesktop.portal.ScreenCast',
                    method,
                    args_variant,
                    None,
                    Gio.DBusCallFlags.NONE,
                    -1,
                    None
                )
                loop.run()
                bus.signal_unsubscribe(sub)
                return res_holder.get('code', 1), res_holder.get('out', {})

            t1 = f'ltm_{random.randint(10000, 99999)}'
            c1, r1 = call_portal('CreateSession', GLib.Variant('(a{sv})', ({
                'handle_token': GLib.Variant('s', t1),
                'session_handle_token': GLib.Variant('s', f'sess_{t1}'),
                'app_id': GLib.Variant('s', 'vn.edu.ltm.chat')
            },)), t1)
            if c1 != 0 or 'session_handle' not in r1:
                sys.exit(2)
            sess = r1['session_handle']

            t2 = f'ltm_{random.randint(10000, 99999)}'
            c2, _ = call_portal('SelectSources', GLib.Variant('(oa{sv})', (sess, {
                'handle_token': GLib.Variant('s', t2),
                'types': GLib.Variant('u', src_type),
                'multiple': GLib.Variant('b', False),
                'cursor_mode': GLib.Variant('u', 2)
            })), t2)
            if c2 != 0:
                sys.exit(2)

            t3 = f'ltm_{random.randint(10000, 99999)}'
            c3, r3 = call_portal('Start', GLib.Variant('(osa{sv})', (sess, '', {
                'handle_token': GLib.Variant('s', t3)
            })), t3)
            if c3 != 0 or not r3.get('streams'):
                sys.exit(2)

            node_id = r3['streams'][0][0]
            ret, fd_list = bus.call_with_unix_fd_list_sync(
                'org.freedesktop.portal.Desktop',
                '/org/freedesktop/portal/desktop',
                'org.freedesktop.portal.ScreenCast',
                'OpenPipeWireRemote',
                GLib.Variant('(oa{sv})', (sess, {})),
                GLib.VariantType.new('(h)'),
                Gio.DBusCallFlags.NONE,
                -1,
                None,
                None
            )
            pw_fd = fd_list.get(ret.unpack()[0])

            pipeline_desc = (
                f'pipewiresrc fd={pw_fd} path={node_id} do-timestamp=true keepalive-time=1000 ! '
                f'videoconvert ! videoscale method=1 ! '
                f'video/x-raw,width=[1,{max_w}],height=[1,{max_h}],pixel-aspect-ratio=1/1 ! '
                f'videorate drop-only=true max-rate={fps} ! '
                f'jpegenc quality={quality} ! '
                f'appsink name=sink emit-signals=true max-buffers=1 drop=true sync=false'
            )
            pipeline = Gst.parse_launch(pipeline_desc)
            sink = pipeline.get_by_name('sink')
            main_loop = GLib.MainLoop()

            def on_new_sample(appsink):
                sample = appsink.emit('pull-sample')
                if not sample:
                    return Gst.FlowReturn.OK
                buf = sample.get_buffer()
                ok, mapinfo = buf.map(Gst.MapFlags.READ)
                if ok:
                    try:
                        data = bytes(mapinfo.data)
                        sys.stdout.buffer.write(struct.pack('>I', len(data)))
                        sys.stdout.buffer.write(data)
                        sys.stdout.buffer.flush()
                    except Exception:
                        main_loop.quit()
                    finally:
                        buf.unmap(mapinfo)
                return Gst.FlowReturn.OK

            sink.connect('new-sample', on_new_sample)
            def on_bus_error(bus, msg):
                err, dbg = msg.parse_error()
                sys.stderr.write(f'[WAYLAND_PORTAL_PY] Error: {err}, debug: {dbg}\\n')
                sys.stderr.flush()
                main_loop.quit()

            gst_bus = pipeline.get_bus()
            gst_bus.add_signal_watch()
            gst_bus.connect('message::eos', lambda b, m: main_loop.quit())
            gst_bus.connect('message::error', on_bus_error)

            pipeline.set_state(Gst.State.PLAYING)
            try:
                main_loop.run()
            finally:
                pipeline.set_state(Gst.State.NULL)
            """;
}

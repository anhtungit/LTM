package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.media.VoiceAudioEngine;
import vn.edu.ltm.chat.media.VideoCaptureEngine;
import vn.edu.ltm.chat.theme.DiscordTheme;
import vn.edu.ltm.chat.theme.IconFactory;

import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class VoiceSettingsDialog extends JDialog {

    private final VoiceAudioEngine voiceEngine;
    private final JComboBox<VoiceAudioEngine.AudioDevice> micCombo = new JComboBox<>();
    private final JComboBox<VoiceAudioEngine.AudioDevice> spkCombo = new JComboBox<>();
    private final JComboBox<VideoCaptureEngine.CameraDevice> camCombo = new JComboBox<>();
    private final JProgressBar micLevelBar = new JProgressBar(0, 100) {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int arc = 6;
            // Track background (Discord dark)
            g2.setColor(new Color(0x1E, 0x1F, 0x22));
            g2.fillRoundRect(0, 0, w, h, arc, arc);

            int val = getValue();
            if (val > 0) {
                int fillW = Math.max(arc, (int) (w * (val / 100.0)));
                fillW = Math.min(w, fillW);
                // Discord green (#23A55A)
                g2.setColor(DiscordTheme.SUCCESS);
                g2.fillRoundRect(0, 0, fillW, h, arc, arc);
            }
            g2.dispose();
        }
    };
    private JButton testMicBtn;
    private volatile boolean testingMic = false;
    private volatile boolean isTestingLoopback = false;
    private volatile boolean wasMutedBeforeTest = false;
    private volatile SourceDataLine testLoopbackLine;
    private boolean updatingCombos = false;
    private Thread micMonitorThread;
    private TargetDataLine testMicLine;

    public VoiceSettingsDialog(JFrame parent, VoiceAudioEngine voiceEngine) {
        super(parent, "Cài đặt Âm thanh & Giọng nói", true);
        this.voiceEngine = voiceEngine;
        initUI();
    }

    private void initUI() {
        setUndecorated(true);
        setBackground(DiscordTheme.SIDEBAR);
        getRootPane().setOpaque(true);
        getRootPane().setBackground(DiscordTheme.SIDEBAR);

        JPanel container = new JPanel(new BorderLayout(0, 16));
        container.setOpaque(true);
        container.setBackground(DiscordTheme.SIDEBAR);
        container.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x3B, 0x3D, 0x44), 1),
                BorderFactory.createEmptyBorder(20, 24, 20, 24)
        ));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(true);
        header.setBackground(DiscordTheme.SIDEBAR);
        JLabel title = new JLabel("Cài đặt Âm thanh & Giọng nói", IconFactory.phosphor("headset.svg", 20, DiscordTheme.SUCCESS), JLabel.LEFT);
        title.setIconTextGap(10);
        title.setFont(DiscordTheme.bold(16f));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);

        JButton closeBtn = new JButton(IconFactory.phosphor("x.svg", 16, DiscordTheme.TEXT_MUTED));
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dispose());
        header.add(closeBtn, BorderLayout.EAST);
        container.add(header, BorderLayout.NORTH);

        // Content
        JPanel body = new JPanel();
        body.setOpaque(true);
        body.setBackground(DiscordTheme.SIDEBAR);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        // 1. Microphone section
        body.add(createSectionLabel("THIẾT BỊ ĐẦU VÀO (MICROPHONE)"));
        body.add(Box.createVerticalStrut(6));

        populateMicCombo();
        styleComboBox(micCombo);
        micCombo.addActionListener(e -> {
            if (updatingCombos) return;
            VoiceAudioEngine.AudioDevice sel = (VoiceAudioEngine.AudioDevice) micCombo.getSelectedItem();
            if (sel != null) {
                String id = sel.id();
                VoiceAudioEngine.setSelectedMicId(id);
                if (voiceEngine != null) {
                    CompletableFuture.runAsync(() -> voiceEngine.switchMicDevice(id));
                }
                if (isTestingLoopback) {
                    startMicLoopback();
                }
            }
        });
        body.add(micCombo);
        body.add(Box.createVerticalStrut(10));

        // Mic Input Volume (Discord-style Input Volume Slider)
        int currentVolPct = (int) Math.round(VoiceAudioEngine.getMicVolume() * 100.0);
        JLabel volLabel = new JLabel("ÂM LƯỢNG ĐẦU VÀO: " + currentVolPct + "%");
        volLabel.setFont(DiscordTheme.bold(10f));
        volLabel.setForeground(DiscordTheme.TEXT_MUTED);
        volLabel.setAlignmentX(LEFT_ALIGNMENT);
        body.add(volLabel);
        body.add(Box.createVerticalStrut(4));

        JSlider volSlider = new JSlider(0, 100, currentVolPct);
        volSlider.setOpaque(false);
        volSlider.setFocusable(false);
        volSlider.setMaximumSize(new Dimension(Short.MAX_VALUE, 24));
        volSlider.setAlignmentX(LEFT_ALIGNMENT);
        volSlider.addChangeListener(e -> {
            int val = volSlider.getValue();
            volLabel.setText("ÂM LƯỢNG ĐẦU VÀO: " + val + "%");
            VoiceAudioEngine.setMicVolume(val / 100.0);
        });
        body.add(volSlider);
        body.add(Box.createVerticalStrut(8));

        // Noise suppression toggle (Discord Krisp-like)
        JCheckBox noiseBox = new JCheckBox("Khử tiếng ồn môi trường (quạt, điều hòa, tiếng ù)");
        noiseBox.setSelected(VoiceAudioEngine.isNoiseSuppressionEnabled());
        noiseBox.setFont(DiscordTheme.font(12f));
        noiseBox.setForeground(Color.WHITE);
        noiseBox.setOpaque(false);
        noiseBox.setFocusPainted(false);
        noiseBox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        noiseBox.addActionListener(e -> VoiceAudioEngine.setNoiseSuppressionEnabled(noiseBox.isSelected()));
        body.add(noiseBox);
        body.add(Box.createVerticalStrut(10));

        // Mic Test section (Discord-style: Title + Subtitle + [Hãy kiểm tra] [Level Bar])
        JLabel testMicLabel = new JLabel("KIỂM TRA MIC");
        testMicLabel.setFont(DiscordTheme.bold(10f));
        testMicLabel.setForeground(DiscordTheme.TEXT_MUTED);
        testMicLabel.setAlignmentX(LEFT_ALIGNMENT);
        body.add(testMicLabel);
        body.add(Box.createVerticalStrut(3));

        JLabel testMicDesc = new JLabel("Gặp sự cố với mic? Bấm Hãy kiểm tra và nói vào mic để nghe lại âm thanh.");
        testMicDesc.setFont(DiscordTheme.font(11f));
        testMicDesc.setForeground(DiscordTheme.TEXT_MUTED);
        testMicDesc.setAlignmentX(LEFT_ALIGNMENT);
        body.add(testMicDesc);
        body.add(Box.createVerticalStrut(8));

        JPanel micTestRow = new JPanel(new BorderLayout(10, 0));
        micTestRow.setOpaque(false);
        micTestRow.setMaximumSize(new Dimension(Short.MAX_VALUE, 32));
        micTestRow.setAlignmentX(LEFT_ALIGNMENT);

        testMicBtn = new JButton("Hãy kiểm tra");
        testMicBtn.setFont(DiscordTheme.bold(12f));
        testMicBtn.setForeground(Color.WHITE);
        testMicBtn.setBackground(DiscordTheme.ACCENT);
        testMicBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        testMicBtn.setFocusPainted(false);
        testMicBtn.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
        testMicBtn.setPreferredSize(new Dimension(125, 32));
        testMicBtn.addActionListener(e -> toggleMicLoopback());

        micLevelBar.setPreferredSize(new Dimension(220, 32));
        micLevelBar.setOpaque(false);
        micLevelBar.setBorder(BorderFactory.createEmptyBorder());

        micTestRow.add(testMicBtn, BorderLayout.WEST);
        micTestRow.add(micLevelBar, BorderLayout.CENTER);
        body.add(micTestRow);
        body.add(Box.createVerticalStrut(14));

        // 2. Speaker section
        body.add(createSectionLabel("THIẾT BỊ ĐẦU RA (LOA / TAI NGHE)"));
        body.add(Box.createVerticalStrut(6));

        populateSpkCombo();
        styleComboBox(spkCombo);
        spkCombo.addActionListener(e -> {
            if (updatingCombos) return;
            VoiceAudioEngine.AudioDevice sel = (VoiceAudioEngine.AudioDevice) spkCombo.getSelectedItem();
            if (sel != null) {
                String id = sel.id();
                VoiceAudioEngine.setSelectedSpeakerId(id);
                if (voiceEngine != null) {
                    CompletableFuture.runAsync(() -> voiceEngine.switchSpeakerDevice(id));
                }
                if (isTestingLoopback) {
                    startMicLoopback();
                }
            }
        });
        body.add(spkCombo);
        body.add(Box.createVerticalStrut(10));

        // Test speaker button
        JButton testSpkBtn = new JButton("Thử loa / tai nghe", IconFactory.phosphor("headset.svg", 14, Color.WHITE));
        testSpkBtn.setIconTextGap(8);
        testSpkBtn.setFont(DiscordTheme.semibold(12f));
        testSpkBtn.setForeground(Color.WHITE);
        testSpkBtn.setBackground(new Color(0x3B, 0x3D, 0x44));
        testSpkBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        testSpkBtn.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
        testSpkBtn.addActionListener(e -> {
            VoiceAudioEngine.AudioDevice sel = (VoiceAudioEngine.AudioDevice) spkCombo.getSelectedItem();
            String devId = sel != null ? sel.id() : "default";
            VoiceAudioEngine.testSpeaker(devId);
        });
        JPanel btnHolder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        btnHolder.setOpaque(true);
        btnHolder.setBackground(DiscordTheme.SIDEBAR);
        btnHolder.add(testSpkBtn);
        body.add(btnHolder);
        body.add(Box.createVerticalStrut(14));

        // 3. Camera section
        body.add(createSectionLabel("THIẾT BỊ HÌNH ẢNH (CAMERA / WEBCAM)"));
        body.add(Box.createVerticalStrut(6));

        populateCamCombo();
        styleComboBox(camCombo);
        camCombo.addActionListener(e -> {
            if (updatingCombos) return;
            VideoCaptureEngine.CameraDevice sel = (VideoCaptureEngine.CameraDevice) camCombo.getSelectedItem();
            if (sel != null) {
                VideoCaptureEngine.setSelectedCameraId(sel.id());
            }
        });
        body.add(camCombo);

        JPanel bodyWrap = new JPanel(new BorderLayout());
        bodyWrap.setOpaque(false);
        bodyWrap.add(body, BorderLayout.NORTH);
        container.add(bodyWrap, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setOpaque(true);
        footer.setBackground(DiscordTheme.SIDEBAR);

        JButton doneBtn = new JButton("Hoàn tất");
        doneBtn.setFont(DiscordTheme.bold(12f));
        doneBtn.setForeground(Color.WHITE);
        doneBtn.setBackground(DiscordTheme.ACCENT);
        doneBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        doneBtn.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
        doneBtn.addActionListener(e -> dispose());
        footer.add(doneBtn);
        container.add(footer, BorderLayout.SOUTH);

        setContentPane(container);
        pack();
        setResizable(false);
        setLocationRelativeTo(getOwner());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopMicLoopback();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                stopMicLoopback();
            }
        });
    }

    @Override
    public void dispose() {
        stopMicLoopback();
        super.dispose();
    }

    private void populateMicCombo() {
        updatingCombos = true;
        try {
            String targetId = VoiceAudioEngine.getSelectedMicId();
            micCombo.removeAllItems();
            List<VoiceAudioEngine.AudioDevice> inputDevices = VoiceAudioEngine.getAvailableInputDevices();
            for (VoiceAudioEngine.AudioDevice dev : inputDevices) {
                micCombo.addItem(dev);
                if (dev.id().equals(targetId)) {
                    micCombo.setSelectedItem(dev);
                }
            }
        } finally {
            updatingCombos = false;
        }
    }

    private void populateSpkCombo() {
        updatingCombos = true;
        try {
            String targetId = VoiceAudioEngine.getSelectedSpeakerId();
            spkCombo.removeAllItems();
            List<VoiceAudioEngine.AudioDevice> outputDevices = VoiceAudioEngine.getAvailableOutputDevices();
            for (VoiceAudioEngine.AudioDevice dev : outputDevices) {
                spkCombo.addItem(dev);
                if (dev.id().equals(targetId)) {
                    spkCombo.setSelectedItem(dev);
                }
            }
        } finally {
            updatingCombos = false;
        }
    }

    private void populateCamCombo() {
        updatingCombos = true;
        try {
            String targetId = VideoCaptureEngine.getSelectedCameraId();
            camCombo.removeAllItems();
            List<VideoCaptureEngine.CameraDevice> camDevices = VideoCaptureEngine.getAvailableCameras();
            for (VideoCaptureEngine.CameraDevice dev : camDevices) {
                camCombo.addItem(dev);
                if (dev.id().equals(targetId)) {
                    camCombo.setSelectedItem(dev);
                }
            }
        } finally {
            updatingCombos = false;
        }
    }

    private JLabel createSectionLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(DiscordTheme.bold(11f));
        lbl.setForeground(DiscordTheme.TEXT_MUTED);
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        return lbl;
    }

    private void styleComboBox(JComboBox<?> box) {
        box.setFont(DiscordTheme.font(12f));
        box.setForeground(Color.WHITE);
        box.setBackground(new Color(0x2B, 0x2D, 0x31));
        box.setOpaque(true);
        box.setLightWeightPopupEnabled(false);
        box.setMaximumSize(new Dimension(Short.MAX_VALUE, 36));
        box.setAlignmentX(LEFT_ALIGNMENT);
    }

    private static int mapRmsToLevel(double rms) {
        if (rms < 50.0) return 0;
        double norm = (rms - 50.0) / 10000.0;
        double curved = Math.sqrt(Math.max(0.0, Math.min(1.0, norm)));
        return (int) Math.round(curved * 100.0);
    }

    private void updateLevelBarSafe(int level) {
        SwingUtilities.invokeLater(() -> {
            if (micCombo.isPopupVisible() || spkCombo.isPopupVisible() || camCombo.isPopupVisible()) {
                return;
            }
            int current = micLevelBar.getValue();
            int display = level > current ? level : Math.max(0, (int) (current * 0.7 + level * 0.3));
            if (current != display) {
                micLevelBar.setValue(display);
            }
        });
    }

    private synchronized void startMicLevelMonitor(String micId) {
        stopMicLevelMonitor();
        testingMic = true;
        micMonitorThread = new Thread(() -> {
            try {
                // If VoiceAudioEngine is already capturing in an active call, read its live RMS without opening a 2nd line
                if (voiceEngine != null && voiceEngine.isRunning()) {
                    while (testingMic && voiceEngine.isRunning()) {
                        double rms = voiceEngine.getCurrentMicRms();
                        int level = mapRmsToLevel(rms);
                        updateLevelBarSafe(level);
                        Thread.sleep(80);
                    }
                    return;
                }

                TargetDataLine line = VoiceAudioEngine.openMicLine(micId);
                if (line == null) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, "Không thể mở thiết bị microphone để kiểm tra.", "Lỗi microphone", JOptionPane.ERROR_MESSAGE);
                        stopMicLoopback();
                    });
                    return;
                }
                this.testMicLine = line;
                byte[] buf = new byte[VoiceAudioEngine.CHUNK_BYTES];
                VoiceAudioEngine.NoiseSuppressor suppressor = new VoiceAudioEngine.NoiseSuppressor();

                while (testingMic && line.isOpen()) {
                    int read = line.read(buf, 0, buf.length);
                    if (read > 0) {
                        byte[] processed = buf;
                        if (VoiceAudioEngine.isNoiseSuppressionEnabled() && read == VoiceAudioEngine.CHUNK_BYTES) {
                            processed = suppressor.process(buf);
                        }
                        if (VoiceAudioEngine.getMicVolume() != 1.0) {
                            VoiceAudioEngine.applyGain(processed, read, VoiceAudioEngine.getMicVolume());
                        }
                        double rms = VoiceAudioEngine.computeRms(processed, read);
                        int level = mapRmsToLevel(rms);
                        updateLevelBarSafe(level);

                        SourceDataLine loopback = testLoopbackLine;
                        if (isTestingLoopback && loopback != null && loopback.isOpen()) {
                            loopback.write(processed, 0, read);
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (testingMic) {
                    stopMicLevelMonitor();
                }
            }
        }, "mic-level-monitor");
        micMonitorThread.setDaemon(true);
        micMonitorThread.start();
    }

    private synchronized void toggleMicLoopback() {
        if (isTestingLoopback) {
            stopMicLoopback();
        } else {
            startMicLoopback();
        }
    }

    private synchronized void startMicLoopback() {
        stopMicLoopback();
        VoiceAudioEngine.AudioDevice spk = (VoiceAudioEngine.AudioDevice) spkCombo.getSelectedItem();
        String spkId = (spk != null) ? spk.id() : VoiceAudioEngine.getSelectedSpeakerId();
        SourceDataLine line = VoiceAudioEngine.openSpeakerLine(spkId);
        if (line == null) {
            JOptionPane.showMessageDialog(this, "Không thể mở thiết bị loa / tai nghe để nghe lại.", "Lỗi âm thanh", JOptionPane.ERROR_MESSAGE);
            return;
        }
        testLoopbackLine = line;
        isTestingLoopback = true;

        if (voiceEngine != null && voiceEngine.isRunning()) {
            wasMutedBeforeTest = voiceEngine.isMuted();
            voiceEngine.setMuted(true);
            voiceEngine.setMicLoopback(buf -> {
                SourceDataLine l = testLoopbackLine;
                if (l != null && l.isOpen()) {
                    l.write(buf, 0, buf.length);
                }
            });
            startMicLevelMonitor(null);
        } else {
            VoiceAudioEngine.AudioDevice mic = (VoiceAudioEngine.AudioDevice) micCombo.getSelectedItem();
            String micId = (mic != null) ? mic.id() : VoiceAudioEngine.getSelectedMicId();
            startMicLevelMonitor(micId);
        }

        if (testMicBtn != null) {
            testMicBtn.setText("Dừng kiểm tra");
            testMicBtn.setBackground(new Color(0xDA, 0x37, 0x3C));
        }
    }

    private synchronized void stopMicLoopback() {
        isTestingLoopback = false;
        stopMicLevelMonitor();

        if (voiceEngine != null) {
            voiceEngine.setMicLoopback(null);
            if (voiceEngine.isRunning()) {
                voiceEngine.setMuted(wasMutedBeforeTest);
            }
        }
        if (testLoopbackLine != null) {
            try {
                testLoopbackLine.stop();
                testLoopbackLine.flush();
                testLoopbackLine.close();
            } catch (Exception ignored) {
            }
            testLoopbackLine = null;
        }
        updateLevelBarSafe(0);
        if (testMicBtn != null) {
            testMicBtn.setText("Hãy kiểm tra");
            testMicBtn.setBackground(DiscordTheme.ACCENT);
        }
    }

    private synchronized void stopMicLevelMonitor() {
        testingMic = false;
        if (testMicLine != null) {
            try {
                testMicLine.stop();
                testMicLine.close();
            } catch (Exception ignored) {
            }
            testMicLine = null;
        }
    }
}

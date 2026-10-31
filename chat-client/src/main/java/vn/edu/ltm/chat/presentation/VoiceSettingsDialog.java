package vn.edu.ltm.chat.presentation;

import vn.edu.ltm.chat.theme.DiscordTheme;
import vn.edu.ltm.chat.theme.IconFactory;

import javax.sound.sampled.TargetDataLine;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class VoiceSettingsDialog extends JDialog {

    private final VoiceAudioEngine voiceEngine;
    private final JComboBox<VoiceAudioEngine.AudioDevice> micCombo = new JComboBox<>();
    private final JComboBox<VoiceAudioEngine.AudioDevice> spkCombo = new JComboBox<>();
    private final JProgressBar micLevelBar = new JProgressBar(0, 100);
    private volatile boolean testingMic = false;
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
        setBackground(new Color(0, 0, 0, 0));

        RoundedPanel container = new RoundedPanel(12);
        container.setBackground(DiscordTheme.SIDEBAR);
        container.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x3B, 0x3D, 0x44), 1),
                BorderFactory.createEmptyBorder(20, 24, 20, 24)
        ));
        container.setLayout(new BorderLayout(0, 16));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Cài đặt Âm thanh & Giọng nói", IconFactory.phosphor("headset.svg", 20, DiscordTheme.SUCCESS), JLabel.LEFT);
        title.setIconTextGap(10);
        title.setFont(DiscordTheme.bold(16f));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);

        JButton closeBtn = new JButton("✕");
        closeBtn.setFont(DiscordTheme.bold(14f));
        closeBtn.setForeground(DiscordTheme.TEXT_MUTED);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dispose());
        header.add(closeBtn, BorderLayout.EAST);
        container.add(header, BorderLayout.NORTH);

        // Content
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        // 1. Microphone section
        body.add(createSectionLabel("THIẾT BỊ ĐẦU VÀO (MICROPHONE)"));
        body.add(Box.createVerticalStrut(6));

        populateMicCombo();
        styleComboBox(micCombo);
        micCombo.addPopupMenuListener(new SimplePopupRefreshListener(this::populateMicCombo));
        micCombo.addActionListener(e -> {
            if (updatingCombos) return;
            VoiceAudioEngine.AudioDevice sel = (VoiceAudioEngine.AudioDevice) micCombo.getSelectedItem();
            if (sel != null && voiceEngine != null) {
                String id = sel.id();
                CompletableFuture.runAsync(() -> {
                    voiceEngine.switchMicDevice(id);
                    SwingUtilities.invokeLater(() -> restartMicLevelMonitor(id));
                });
            }
        });
        body.add(micCombo);
        body.add(Box.createVerticalStrut(10));

        // Mic volume meter
        JLabel testMicLabel = new JLabel("KIỂM TRA ÂM LƯỢNG MIC (NÓI VÀO MIC ĐỂ THỬ)");
        testMicLabel.setFont(DiscordTheme.bold(10f));
        testMicLabel.setForeground(DiscordTheme.TEXT_MUTED);
        body.add(testMicLabel);
        body.add(Box.createVerticalStrut(4));

        micLevelBar.setPreferredSize(new Dimension(360, 8));
        micLevelBar.setMaximumSize(new Dimension(Short.MAX_VALUE, 8));
        micLevelBar.setForeground(DiscordTheme.SUCCESS);
        micLevelBar.setBackground(new Color(0x2B, 0x2D, 0x31));
        micLevelBar.setBorder(BorderFactory.createEmptyBorder());
        body.add(micLevelBar);
        body.add(Box.createVerticalStrut(16));

        // 2. Speaker section
        body.add(createSectionLabel("THIẾT BỊ ĐẦU RA (LOA / TAI NGHE)"));
        body.add(Box.createVerticalStrut(6));

        populateSpkCombo();
        styleComboBox(spkCombo);
        spkCombo.addPopupMenuListener(new SimplePopupRefreshListener(this::populateSpkCombo));
        spkCombo.addActionListener(e -> {
            if (updatingCombos) return;
            VoiceAudioEngine.AudioDevice sel = (VoiceAudioEngine.AudioDevice) spkCombo.getSelectedItem();
            if (sel != null && voiceEngine != null) {
                String id = sel.id();
                CompletableFuture.runAsync(() -> voiceEngine.switchSpeakerDevice(id));
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
        btnHolder.setOpaque(false);
        btnHolder.add(testSpkBtn);
        body.add(btnHolder);

        container.add(body, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setOpaque(false);

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
        setSize(460, 380);
        setLocationRelativeTo(getOwner());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                VoiceAudioEngine.AudioDevice sel = (VoiceAudioEngine.AudioDevice) micCombo.getSelectedItem();
                startMicLevelMonitor(sel != null ? sel.id() : "default");
            }

            @Override
            public void windowClosing(WindowEvent e) {
                stopMicLevelMonitor();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                stopMicLevelMonitor();
            }
        });
    }

    @Override
    public void dispose() {
        stopMicLevelMonitor();
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
        box.setMaximumSize(new Dimension(Short.MAX_VALUE, 36));
        box.setAlignmentX(LEFT_ALIGNMENT);
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
                        int level = Math.min(100, (int) (rms / 40.0));
                        SwingUtilities.invokeLater(() -> micLevelBar.setValue(level));
                        Thread.sleep(80);
                    }
                    return;
                }

                TargetDataLine line = VoiceAudioEngine.openMicLine(micId);
                if (line == null) return;
                this.testMicLine = line;
                byte[] buf = new byte[VoiceAudioEngine.CHUNK_BYTES];

                while (testingMic && line.isOpen()) {
                    int read = line.read(buf, 0, buf.length);
                    if (read > 0) {
                        double rms = VoiceAudioEngine.computeRms(buf, read);
                        int level = Math.min(100, (int) (rms / 40.0));
                        SwingUtilities.invokeLater(() -> micLevelBar.setValue(level));
                    }
                }
            } catch (Exception ignored) {
            } finally {
                stopMicLevelMonitor();
            }
        }, "mic-level-monitor");
        micMonitorThread.setDaemon(true);
        micMonitorThread.start();
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

    private synchronized void restartMicLevelMonitor(String micId) {
        stopMicLevelMonitor();
        Timer t = new Timer(150, e -> startMicLevelMonitor(micId));
        t.setRepeats(false);
        t.start();
    }

    private static final class SimplePopupRefreshListener implements PopupMenuListener {
        private final Runnable onOpen;

        SimplePopupRefreshListener(Runnable onOpen) {
            this.onOpen = onOpen;
        }

        @Override
        public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            onOpen.run();
        }

        @Override
        public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        }

        @Override
        public void popupMenuCanceled(PopupMenuEvent e) {
        }
    }
}

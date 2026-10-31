package vn.edu.ltm.chat.presentation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceAudioEngineTest {

    @Test
    void testComputeRmsRejectsDcOffsetSilence() {
        // 16-bit little-endian samples with DC offset ~500 and slight jitter +-5
        byte[] silence = new byte[3200];
        for (int i = 0; i < silence.length / 2; i++) {
            short sample = (short) (500 + (i % 5));
            silence[i * 2] = (byte) (sample & 0xFF);
            silence[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        double rms = VoiceAudioEngine.computeRms(silence, silence.length);
        // AC-RMS should be very low (close to 0), well below 350.0 threshold
        assertTrue(rms < 50.0, "AC-RMS for DC-offset silence should be minimal, got: " + rms);
    }

    @Test
    void testComputeRmsDetectsVoiceSignal() {
        // 16-bit little-endian samples with voice sine wave amplitude 6000
        byte[] voice = new byte[3200];
        for (int i = 0; i < voice.length / 2; i++) {
            short sample = (short) (500 + Math.sin(2 * Math.PI * 400 * i / 16000.0) * 6000);
            voice[i * 2] = (byte) (sample & 0xFF);
            voice[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        double rms = VoiceAudioEngine.computeRms(voice, voice.length);
        // AC-RMS for voice should easily exceed 350.0 threshold (~4200)
        assertTrue(rms > 2000.0, "AC-RMS for voice signal should be high, got: " + rms);
    }

    @Test
    void testAvailableDevicesHaveFriendlyNames() {
        var inputs = VoiceAudioEngine.getAvailableInputDevices();
        assertFalse(inputs.isEmpty());
        assertTrue(inputs.stream().anyMatch(d -> "default".equals(d.id())));

        var outputs = VoiceAudioEngine.getAvailableOutputDevices();
        assertFalse(outputs.isEmpty());
        assertTrue(outputs.stream().anyMatch(d -> "default".equals(d.id())));

        for (var out : outputs) {
            assertFalse(out.displayName().contains("[plughw:"),
                    "Output device display name should be user-friendly: " + out.displayName());
        }
        for (var in : inputs) {
            assertFalse(in.displayName().contains("[plughw:"),
                    "Input device display name should be user-friendly: " + in.displayName());
        }
    }

    @Test
    void testDynamicDeviceFormattingForNewHardwareAndWindows() {
        // 1. Windows & macOS native names must be preserved verbatim
        assertEquals(
                "Microphone (2- Realtek(R) Audio)",
                VoiceAudioEngine.formatDeviceName("Microphone (2- Realtek(R) Audio)", "DirectSound Capture", true)
        );
        assertEquals(
                "Headphones (Razer BlackShark V2)",
                VoiceAudioEngine.formatDeviceName("Headphones (Razer BlackShark V2)", "DirectSound Playback", false)
        );
        assertEquals(
                "MacBook Pro Microphone",
                VoiceAudioEngine.formatDeviceName("MacBook Pro Microphone", "CoreAudio", true)
        );

        // 2. Arbitrary new external devices plugged into Linux ALSA must dynamically use their hardware card name
        assertEquals(
                "Microphone – HyperX QuadCast",
                VoiceAudioEngine.formatDeviceName(
                        "QuadCast [plughw:2,0]",
                        "Direct Audio Device: HyperX QuadCast, USB Audio, USB Audio",
                        true
                )
        );
        assertEquals(
                "Speakers – Sony WH-1000XM5",
                VoiceAudioEngine.formatDeviceName(
                        "WH1000XM5 [plughw:3,0]",
                        "Direct Audio Device: Sony WH-1000XM5, USB Audio, USB Audio",
                        false
                )
        );

        // 3. Connected vs unplugged HDMI detection on Linux ALSA
        assertFalse(VoiceAudioEngine.isUnpluggedLinuxHdmi(
                "PCH [plughw:0,3]",
                "Direct Audio Device: HDA Intel PCH, HDMI 0, LG HD"
        ));
        assertTrue(VoiceAudioEngine.isUnpluggedLinuxHdmi(
                "PCH [plughw:0,7]",
                "Direct Audio Device: HDA Intel PCH, HDMI 1, HDMI 1"
        ));
    }
}


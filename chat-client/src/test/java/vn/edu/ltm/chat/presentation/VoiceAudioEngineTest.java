package vn.edu.ltm.chat.presentation;

import org.junit.jupiter.api.Test;

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
}

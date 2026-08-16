package com.jozef.speech.mic;

import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/**
 * Captures mono 16 kHz / 16-bit little-endian PCM from the default microphone —
 * the exact format Vosk expects. Wraps {@link TargetDataLine}.
 */
@Slf4j
public class MicrophoneAudioSource implements AutoCloseable {

    public static final int SAMPLE_RATE = 16_000;
    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

    private volatile TargetDataLine line;

    public synchronized void open() {
        if (line != null) {
            return;
        }
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        if (!AudioSystem.isLineSupported(info)) {
            throw new IllegalStateException("No microphone supports " + FORMAT);
        }
        try {
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(FORMAT, SAMPLE_RATE * 2); // ~1s buffer
            line.start();
            log.info("Microphone opened: {}", FORMAT);
        } catch (LineUnavailableException e) {
            throw new IllegalStateException("Microphone unavailable: " + e.getMessage(), e);
        }
    }

    /** Blocks until up to {@code buffer.length} bytes are captured. */
    public int read(byte[] buffer) {
        TargetDataLine current = line;
        if (current == null) {
            throw new IllegalStateException("Microphone is not open");
        }
        return current.read(buffer, 0, buffer.length);
    }

    @Override
    public synchronized void close() {
        if (line != null) {
            try {
                line.stop();
                line.close();
            } catch (Exception ignored) {
                // ignore close errors
            }
            line = null;
            log.info("Microphone closed.");
        }
    }
}

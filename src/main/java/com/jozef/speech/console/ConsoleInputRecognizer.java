package com.jozef.speech.console;

import com.jozef.speech.SpeechRecognizer;
import lombok.extern.slf4j.Slf4j;

import java.util.Scanner;

/**
 * Temporary: reads text from console instead of microphone.
 * For development until a real STT implementation is integrated.
 *
 * EOF-safe: if no console input is available (background/tray mode) it
 * waits instead of throwing or busy-spinning.
 */
@Slf4j
public class ConsoleInputRecognizer implements SpeechRecognizer {

    private final Scanner scanner = new Scanner(System.in);

    @Override
    public String name() {
        return "console-input";
    }

    @Override
    public String listen() {
        while (true) {
            if (scanner.hasNextLine()) {
                String text = scanner.nextLine().trim();
                log.debug("Console input: {}", text);
                return text;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "";
            }
        }
    }
}

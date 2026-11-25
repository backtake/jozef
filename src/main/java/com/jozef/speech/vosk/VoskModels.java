package com.jozef.speech.vosk;

import lombok.extern.slf4j.Slf4j;
import org.vosk.Model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads each Vosk model once and shares it. Loading takes a second or more and
 * hundreds of MB; doing it on every listen (twice per command: wake word, then
 * the command) added that delay after every "hej Jozef". Models are read-only
 * and safe to share between recognizers.
 */
@Slf4j
final class VoskModels {

    private static final Map<String, Model> MODELS = new ConcurrentHashMap<>();

    private VoskModels() {}

    static Model get(String path) {
        return MODELS.computeIfAbsent(path, p -> {
            VoskNativeLibrary.prepare();
            long start = System.currentTimeMillis();
            try {
                Model model = new Model(p);
                log.info("Vosk model loaded from {} in {} ms", p, System.currentTimeMillis() - start);
                return model;
            } catch (Exception e) {
                throw new IllegalStateException("Cannot load Vosk model " + p + ": " + e.getMessage(), e);
            }
        });
    }
}

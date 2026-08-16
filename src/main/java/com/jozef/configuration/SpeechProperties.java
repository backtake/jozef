package com.jozef.configuration;

public record SpeechProperties(
        SttProperties stt,
        TtsProperties tts,
        VadProperties vad
) {
    public record SttProperties(String provider, String model, boolean autoDownload) {}
    public record TtsProperties(String provider, String voice) {}
    public record VadProperties(boolean enabled, String sensitivity) {}
}

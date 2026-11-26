package com.jozef.shortcuts;

import com.jozef.core.agent.FastPath;
import com.jozef.core.util.TextNormalizer;
import com.jozef.llm.ToolCall;

import java.util.Map;
import java.util.Optional;

/**
 * One- or two-word media commands answered instantly: "głośniej", "ciszej",
 * "pauza", "następna", "poprzednia", "wycisz". Exact phrases only — anything
 * longer ("puść coś spokojnego") goes to the model.
 */
public class MediaCommands implements FastPath {

    private static final Map<String, String> PHRASES = Map.ofEntries(
            Map.entry("glosniej", "volume_up"), Map.entry("podglos", "volume_up"), Map.entry("zrob glosniej", "volume_up"),
            Map.entry("ciszej", "volume_down"), Map.entry("scisz", "volume_down"), Map.entry("zrob ciszej", "volume_down"),
            // Play/pause and mute are toggles: only neutral words, never "puść"/"włącz",
            // which would do the opposite when music is already playing / sound is on.
            Map.entry("pauza", "play_pause"), Map.entry("play pauza", "play_pause"),
            // Bare "następny"/"poprzedni" are left to the model — they are also answers
            // ("przeczytać następny mail?" — "następny").
            Map.entry("nastepna piosenka", "next"), Map.entry("nastepny utwor", "next"), Map.entry("nastepny kawalek", "next"),
            Map.entry("poprzednia piosenka", "previous"), Map.entry("poprzedni utwor", "previous"),
            Map.entry("wycisz", "mute"), Map.entry("przelacz wyciszenie", "mute"));

    private static final Map<String, String> REPLIES = Map.of(
            "volume_up", "Głośniej.", "volume_down", "Ciszej.", "play_pause", "Przełączam odtwarzanie.",
            "next", "Następny utwór.", "previous", "Poprzedni utwór.", "mute", "Przełączam wyciszenie.");

    @Override
    public Optional<Resolved> resolve(String utterance) {
        String n = TextNormalizer.normalize(utterance)
                .replaceFirst("^(?:hej )?(?:jozefie|jozef) ", "")
                .replaceFirst(" prosze$", "");
        String action = PHRASES.get(n);
        if (action == null) return Optional.empty();
        return Optional.of(new Resolved(ToolCall.of("media_control", Map.of("action", action)), REPLIES.get(action)));
    }
}

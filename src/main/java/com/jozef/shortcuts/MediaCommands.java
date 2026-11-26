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
            Map.entry("pauza", "play_pause"), Map.entry("wstrzymaj muzyke", "play_pause"), Map.entry("zatrzymaj muzyke", "play_pause"),
            Map.entry("wznow muzyke", "play_pause"), Map.entry("graj dalej", "play_pause"), Map.entry("pusc muzyke", "play_pause"),
            Map.entry("nastepna", "next"), Map.entry("nastepny", "next"), Map.entry("nastepna piosenka", "next"),
            Map.entry("nastepny utwor", "next"), Map.entry("przewin dalej", "next"),
            Map.entry("poprzednia", "previous"), Map.entry("poprzedni", "previous"), Map.entry("poprzednia piosenka", "previous"),
            Map.entry("poprzedni utwor", "previous"),
            Map.entry("wycisz", "mute"), Map.entry("wylacz dzwiek", "mute"), Map.entry("wlacz dzwiek", "mute"));

    private static final Map<String, String> REPLIES = Map.of(
            "volume_up", "Głośniej.", "volume_down", "Ciszej.", "play_pause", "Dobrze.",
            "next", "Następny utwór.", "previous", "Poprzedni utwór.", "mute", "Dobrze.");

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

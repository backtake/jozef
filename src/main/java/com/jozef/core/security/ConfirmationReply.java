package com.jozef.core.security;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic classification of the user's answer to "should I do it?".
 *
 * Deliberately conservative: only short, unambiguous replies count. Anything
 * else is {@link Kind#OTHER} — the pending action is then dropped and the
 * utterance handled as a new request. The model never decides this.
 */
public final class ConfirmationReply {

    public enum Kind { YES, NO, OTHER }

    private static final Pattern NON_LETTERS = Pattern.compile("[^a-z0-9 ]");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");

    private static final Set<String> YES = Set.of(
            "tak", "t", "ta", "no tak", "tak tak", "jasne", "pewnie", "ok", "okej", "okay", "dobrze",
            "potwierdzam", "zatwierdzam", "zgoda", "zgadzam sie", "dawaj", "rob", "zrob to", "zrob",
            "wykonaj", "wyslij", "usun", "tak wyslij", "tak usun", "tak zrob to", "tak potwierdzam",
            "tak prosze", "prosze", "mozesz", "dalej", "yes", "y", "yep", "sure", "do it", "confirm", "go");

    private static final Set<String> NO = Set.of(
            "nie", "n", "nie nie", "nie rob", "nie rob tego", "nie wysylaj", "nie usuwaj", "anuluj",
            "stop", "przestan", "zostaw", "odwolaj", "rezygnuje", "nie trzeba", "nie dziekuje",
            // "no" is deliberately absent: in Polish it colloquially means "yeah".
            "nope", "cancel", "abort");

    private ConfirmationReply() {}

    public static Kind classify(String utterance) {
        String n = normalize(utterance);
        if (n.isEmpty()) return Kind.OTHER;
        if (YES.contains(n)) return Kind.YES;
        if (NO.contains(n)) return Kind.NO;
        return Kind.OTHER;
    }

    static String normalize(String text) {
        if (text == null) return "";
        String s = text.toLowerCase(Locale.ROOT).replace('ł', 'l');
        s = DIACRITICS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
        s = NON_LETTERS.matcher(s).replaceAll(" ").trim().replaceAll(" +", " ");
        // Drop the wake-word/name prefix the recogniser may keep ("jozef tak").
        for (String prefix : new String[]{"hej jozef ", "jozef ", "jozefie "}) {
            if (s.startsWith(prefix)) s = s.substring(prefix.length());
        }
        return s;
    }
}

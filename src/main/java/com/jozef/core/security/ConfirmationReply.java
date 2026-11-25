package com.jozef.core.security;

import com.jozef.core.util.TextNormalizer;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Deterministic classification of the user's answer to "Mam to zrobić?".
 *
 * <ul>
 *   <li>NO wins: any veto word anywhere ("nie", "anuluj", "zamiast", "poczekaj"…) means NO
 *       when the reply is short, otherwise OTHER — never YES.</li>
 *   <li>YES: the reply starts with an affirmative ("tak…", "jasne…", "oczywiście…",
 *       "no tak", "dawaj") and is short; or it is the action verb of the parked
 *       tool ("wyślij" only approves sending, "usuń" only deleting).</li>
 *   <li>Everything else is OTHER — the pending action is dropped.</li>
 * </ul>
 * The model never takes part in this decision.
 */
public final class ConfirmationReply {

    public enum Kind { YES, NO, OTHER }

    private static final int MAX_WORDS = 6;

    private static final List<String> YES_STARTS = List.of(
            "tak", "jasne", "oczywiscie", "pewnie", "potwierdzam", "zatwierdzam", "zgoda", "zgadzam sie",
            "dawaj", "smialo", "no tak", "no jasne", "ok", "okej", "okay", "dobrze", "dobra", "zrob to",
            "rob", "wykonaj", "prosze bardzo", "yes", "yep", "sure", "do it", "confirm");

    /** Words that always veto. "no" is deliberately absent: in Polish it means "yeah". */
    private static final Set<String> VETO = Set.of(
            "nie", "anuluj", "anulowac", "stop", "przestan", "zostaw", "odwolaj", "rezygnuje",
            "zamiast", "ale", "poczekaj", "czekaj", "zaczekaj", "zmien", "popraw", "inaczej", "wstrzymaj",
            "cancel", "abort", "nope", "dont", "wait");

    private static final Set<String> NO_ONLY = Set.of(
            "nie", "n", "nie nie", "nie rob tego", "nie rob", "anuluj", "stop", "przestan", "zostaw",
            "odwolaj", "rezygnuje", "nie trzeba", "nie dziekuje", "nie wysylaj", "nie usuwaj", "cancel",
            "abort", "nope", "nie teraz", "jednak nie", "lepiej nie");

    private ConfirmationReply() {}

    public static Kind classify(String utterance) {
        return classify(utterance, List.of());
    }

    public static Kind classify(String utterance, Collection<String> pendingTools) {
        String n = strip(TextNormalizer.normalize(utterance));
        if (n.isEmpty()) return Kind.OTHER;
        String[] words = n.split(" ");

        if (NO_ONLY.contains(n)) return Kind.NO;
        for (String w : words) {
            if (VETO.contains(w)) {
                return words.length <= 3 ? Kind.NO : Kind.OTHER;
            }
        }
        if (words.length > MAX_WORDS) return Kind.OTHER;

        // "tak, wyślij do Basi" must not approve sending to Ania: after the affirmative only
        // filler (or the parked tool's own verb) may follow — anything else is a correction.
        for (String start : YES_STARTS) {
            if (n.equals(start) || n.startsWith(start + " ")) {
                String rest = n.substring(start.length()).trim();
                return onlyFiller(rest, pendingTools) ? Kind.YES : Kind.OTHER;
            }
        }
        if (matchesActionVerb(words[0], pendingTools)
                && onlyFiller(n.substring(words[0].length()).trim(), pendingTools)) {
            return Kind.YES;
        }
        return Kind.OTHER;
    }

    private static final Set<String> FILLER = Set.of(
            "tak", "jasne", "oczywiscie", "pewnie", "potwierdzam", "prosze", "dziekuje", "dzieki", "jest",
            "to", "go", "ja", "ok", "okej", "dobrze", "dobra", "smialo", "mozesz", "dawaj", "zrob", "rob",
            "jak", "najbardziej", "bardzo", "teraz", "juz", "please", "yes", "sure");

    private static boolean onlyFiller(String rest, Collection<String> pendingTools) {
        if (rest.isEmpty()) return true;
        for (String w : rest.split(" ")) {
            if (!FILLER.contains(w) && !matchesActionVerb(w, pendingTools)) return false;
        }
        return true;
    }

    private static boolean matchesActionVerb(String verb, Collection<String> tools) {
        if (tools == null || tools.isEmpty()) return false;
        for (String tool : tools) {
            String t = tool.toLowerCase();
            if ((t.contains("send") || t.contains("reply")) && verb.matches("wyslij|wysylaj|wyslac|odpowiedz")) return true;
            if (t.contains("delete") && verb.matches("usun|usuwaj|skasuj|kasuj")) return true;
            if ((t.contains("create") || t.contains("add")) && verb.matches("dodaj|utworz|stworz|zapisz|wpisz")) return true;
            if (t.contains("update") && verb.matches("zaktualizuj|przesun|przesunac")) return true;
        }
        return false;
    }

    /** Drop the name/wake-word prefix the recogniser may keep ("jozef tak"). */
    private static String strip(String s) {
        for (String prefix : new String[]{"hej jozef ", "jozefie ", "jozef "}) {
            if (s.startsWith(prefix)) return s.substring(prefix.length());
        }
        return s;
    }
}

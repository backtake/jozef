package com.jozef.core.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalises spoken/typed Polish text for matching: lower case, no diacritics
 * ("Włącz Spotify!" → "wlacz spotify"), punctuation collapsed to single spaces.
 */
public final class TextNormalizer {

    private static final Pattern MARKS = Pattern.compile("\\p{M}");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9.]+");

    private TextNormalizer() {}

    public static String normalize(String text) {
        if (text == null) return "";
        String s = text.toLowerCase(Locale.ROOT).replace('ł', 'l');
        s = MARKS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
        s = NON_ALNUM.matcher(s).replaceAll(" ").trim();
        // Dots only matter inside tokens (youtube.com); strip trailing/leading ones.
        s = s.replaceAll("(^|\\s)\\.+|\\.+(\\s|$)", " ").trim();
        return s.replaceAll(" +", " ");
    }
}

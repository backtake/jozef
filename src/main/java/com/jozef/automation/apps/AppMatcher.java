package com.jozef.automation.apps;

import com.jozef.core.util.TextNormalizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Finds the installed app the user meant ("discord", "worda", "eksplorator").
 *
 * Scores, best first: exact name → name starts with the query → whole-word
 * match → substring. Helper entries (uninstallers, documentation, help) are
 * ignored unless asked for explicitly.
 */
public final class AppMatcher {

    private static final Set<String> NOISE = Set.of(
            "uninstall", "odinstaluj", "help", "pomoc", "documentation", "readme", "license", "licencja",
            "release notes", "website", "manual");

    private AppMatcher() {}

    public record Match(InstalledApp app, int score) {}

    /** Best match, if it is unambiguous enough to launch without asking. */
    public static Optional<InstalledApp> best(List<InstalledApp> apps, String query) {
        List<Match> ranked = rank(apps, query);
        if (ranked.isEmpty()) return Optional.empty();
        Match top = ranked.get(0);
        if (top.score() == 100) return Optional.of(top.app()); // exact name always wins
        // Otherwise only when no other app matches equally well ("visual studio" → Code or 2022?).
        if (ranked.size() == 1 || ranked.get(1).score() < top.score()) return Optional.of(top.app());
        return Optional.empty();
    }

    public static List<Match> rank(List<InstalledApp> apps, String query) {
        String q = TextNormalizer.normalize(query);
        if (q.isEmpty()) return List.of();
        String stem = stripPolishInflection(q);
        boolean wantsNoise = NOISE.stream().anyMatch(q::contains);
        List<Match> matches = new ArrayList<>();
        for (InstalledApp app : apps) {
            String name = TextNormalizer.normalize(app.name());
            if (!wantsNoise && NOISE.stream().anyMatch(name::contains)) continue;
            int score = Math.max(score(name, q), stem.equals(q) ? 0 : score(name, stem) - 5);
            if (score > 0) matches.add(new Match(app, score));
        }
        matches.sort(Comparator.comparingInt(Match::score).reversed()
                .thenComparingInt(m -> m.app().name().length()));
        return matches;
    }

    private static int score(String name, String q) {
        if (name.equals(q)) return 100;
        if (name.startsWith(q + " ") || name.startsWith(q)) return 85;
        for (String word : name.split(" ")) {
            if (word.equals(q)) return 70;
        }
        if (q.length() >= 4 && name.contains(q)) return 50;
        // All query words present ("visual studio" in "Microsoft Visual Studio Code").
        String[] words = q.split(" ");
        if (words.length > 1) {
            for (String w : words) {
                if (!name.contains(w)) return 0;
            }
            return 60;
        }
        return 0;
    }

    /** "worda", "spotifaja", "discorda" → crude accusative stripping for single words. */
    static String stripPolishInflection(String q) {
        if (q.contains(" ") || q.length() < 5) return q;
        if (q.endsWith("aja")) return q.substring(0, q.length() - 3) + "y";
        if (q.endsWith("a")) return q.substring(0, q.length() - 1);
        return q;
    }
}

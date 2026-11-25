package com.jozef.shortcuts;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.jozef.core.util.AtomicFiles;
import com.jozef.core.util.TextNormalizer;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The user's own shortcuts, kept in an editable YAML file ({@code data/shortcuts.yml}).
 *
 * "Otwórz pocztę", "włącz YouTube", "odpal Discorda" resolve here first — instantly
 * and without the language model. The file is seeded with common sites on first
 * run; the user (or Jozef, via {@code save_shortcut}) adds their own.
 */
@Slf4j
public class ShortcutStore {

    private final Path file;
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES))
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final List<Shortcut> shortcuts = new ArrayList<>();

    public ShortcutStore(String file) {
        this.file = Path.of(file == null || file.isBlank() ? "data/shortcuts.yml" : file);
        load();
    }

    /** File layout: {@code shortcuts: [ {name, aliases, url|app}, ... ]}. */
    record FileModel(List<Shortcut> shortcuts) {}

    private synchronized void load() {
        shortcuts.clear();
        if (!Files.exists(file)) {
            shortcuts.addAll(defaults());
            save();
            log.info("Created default shortcuts file {}", file.toAbsolutePath());
            return;
        }
        try {
            FileModel model = yaml.readValue(file.toFile(), FileModel.class);
            if (model != null && model.shortcuts() != null) {
                model.shortcuts().stream()
                        .filter(s -> s != null && s.name() != null && s.target() != null && !s.target().isBlank())
                        .forEach(shortcuts::add);
            }
            log.info("Loaded {} shortcuts from {}", shortcuts.size(), file);
        } catch (Exception e) {
            // Never overwrite a file the user may have hand-edited with a typo.
            log.warn("Cannot read {} ({}) — shortcuts disabled until the file is fixed.", file, e.getMessage());
        }
    }

    public synchronized void reload() {
        load();
    }

    private synchronized void save() {
        try {
            AtomicFiles.write(file, new FileModel(List.copyOf(shortcuts)), yaml);
        } catch (Exception e) {
            log.warn("Cannot save shortcuts to {}: {}", file, e.getMessage());
        }
    }

    public synchronized List<Shortcut> all() {
        return List.copyOf(shortcuts);
    }

    /** Finds a shortcut by its name or any alias (case/diacritics-insensitive). */
    public synchronized Optional<Shortcut> find(String spoken) {
        String q = TextNormalizer.normalize(spoken);
        if (q.isEmpty()) return Optional.empty();
        for (Shortcut s : shortcuts) {
            if (keys(s).contains(q)) return Optional.of(s);
        }
        // Polish inflection: "otwórz pocztę firmową" must find "poczta firmowa".
        String qStem = stem(q);
        for (Shortcut s : shortcuts) {
            for (String key : keys(s)) {
                if (stem(key).equals(qStem)) return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    /** Crude Polish stemming: drop trailing vowels of each word ("pocztę"→"poczt", "youtuba"→"youtub"). */
    static String stem(String normalized) {
        StringBuilder sb = new StringBuilder();
        for (String word : normalized.split(" ")) {
            String w = word.length() > 3 ? word.replaceAll("[aeiouy]+$", "") : word;
            if (sb.length() > 0) sb.append(' ');
            sb.append(w);
        }
        return sb.toString();
    }

    /** Adds or replaces (same name) a shortcut and persists the file. */
    public synchronized Shortcut put(Shortcut shortcut) {
        String key = TextNormalizer.normalize(shortcut.name());
        shortcuts.removeIf(s -> TextNormalizer.normalize(s.name()).equals(key));
        shortcuts.add(shortcut);
        save();
        return shortcut;
    }

    public synchronized boolean remove(String spoken) {
        Optional<Shortcut> found = find(spoken);
        found.ifPresent(s -> {
            shortcuts.remove(s);
            save();
        });
        return found.isPresent();
    }

    private static List<String> keys(Shortcut s) {
        List<String> keys = new ArrayList<>();
        keys.add(TextNormalizer.normalize(s.name()));
        s.aliases().forEach(a -> keys.add(TextNormalizer.normalize(a)));
        return keys;
    }

    static List<Shortcut> defaults() {
        return List.of(
                Shortcut.website("YouTube", "https://www.youtube.com", "yt", "jutub", "jutuba", "youtuba"),
                Shortcut.website("Gmail", "https://mail.google.com", "poczta", "pocztę", "maile", "skrzynka", "skrzynkę", "gmaila"),
                Shortcut.website("Kalendarz Google", "https://calendar.google.com", "kalendarz", "mój kalendarz"),
                Shortcut.website("Dysk Google", "https://drive.google.com", "dysk", "google drive"),
                Shortcut.website("Mapy Google", "https://maps.google.com", "mapy", "mapa", "mapę"),
                Shortcut.website("ChatGPT", "https://chatgpt.com", "czat gpt", "chat gpt"),
                Shortcut.website("Claude", "https://claude.ai", "klod"),
                Shortcut.website("GitHub", "https://github.com", "githuba", "gita"),
                Shortcut.website("Netflix", "https://www.netflix.com", "netflixa"),
                Shortcut.website("Facebook", "https://www.facebook.com", "fejsa", "fejsbuka", "facebooka"),
                Shortcut.website("Messenger", "https://www.messenger.com", "mesendżer", "messengera"),
                Shortcut.website("Allegro", "https://allegro.pl"),
                Shortcut.website("OLX", "https://www.olx.pl"),
                Shortcut.website("Wiadomości", "https://www.onet.pl", "onet", "newsy"),
                Shortcut.website("Pogoda", "https://www.google.com/search?q=pogoda", "prognoza pogody"),
                Shortcut.website("Twitch", "https://www.twitch.tv", "twicza", "twitcha")
        );
    }
}

package com.jozef.planning;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * File-backed {@link PlanStore}: plans are persisted to a JSON file (default
 * {@code data/plans.json}) and reloaded on startup, so multi-step plans
 * survive restarts.
 */
@Slf4j
public class FilePlanStore implements PlanStore {

    private final Path path;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Object lock = new Object();
    private final Map<String, Plan> plans = new LinkedHashMap<>();

    public FilePlanStore(String filePath) {
        String resolved = (filePath == null || filePath.isBlank())
                ? "data/plans.json"
                : filePath;
        this.path = Path.of(resolved);
        load();
    }

    private void load() {
        if (!Files.exists(path)) {
            return;
        }
        try {
            Plan[] array = mapper.readValue(path.toFile(), Plan[].class);
            synchronized (lock) {
                for (Plan plan : array) {
                    plans.put(plan.getId(), plan);
                }
            }
            log.info("Loaded {} plans from {}", plans.size(), path);
        } catch (Exception e) {
            log.warn("Could not load plans from {}: {}", path, e.getMessage());
        }
    }

    @Override
    public void put(Plan plan) {
        synchronized (lock) {
            plans.put(plan.getId(), plan);
            persist();
        }
    }

    @Override
    public Optional<Plan> get(String id) {
        synchronized (lock) {
            return Optional.ofNullable(plans.get(id));
        }
    }

    @Override
    public List<Plan> all() {
        synchronized (lock) {
            return List.copyOf(plans.values());
        }
    }

    private void persist() {
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), new ArrayList<>(plans.values()));
        } catch (IOException e) {
            log.error("Could not persist plans to {}: {}", path, e.getMessage(), e);
        }
    }

    public Path path() {
        return path;
    }
}

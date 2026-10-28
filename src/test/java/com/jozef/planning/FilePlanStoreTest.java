package com.jozef.planning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FilePlanStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsPlansAcrossInstances() {
        Path file = tempDir.resolve("plans.json");
        Plan plan = new Plan("p1", "Uporządkować biurko",
                List.of(new PlanStep("s1", "Otwórz notatnik", "open_application", Map.of("name", "notepad"))));
        plan.setStatus(PlanStatus.RUNNING);

        new FilePlanStore(file.toString()).put(plan);

        FilePlanStore reloaded = new FilePlanStore(file.toString());
        assertThat(reloaded.get("p1")).isPresent();
        assertThat(reloaded.get("p1").orElseThrow().getGoal()).isEqualTo("Uporządkować biurko");
        assertThat(reloaded.get("p1").orElseThrow().getStatus()).isEqualTo(PlanStatus.RUNNING);
        assertThat(reloaded.get("p1").orElseThrow().getSteps()).hasSize(1);
        assertThat(reloaded.get("p1").orElseThrow().getSteps().get(0).getTool()).isEqualTo("open_application");
    }

    @Test
    void returnsEmptyForUnknownId() {
        FilePlanStore store = new FilePlanStore(tempDir.resolve("plans.json").toString());
        assertThat(store.get("missing")).isEmpty();
    }
}

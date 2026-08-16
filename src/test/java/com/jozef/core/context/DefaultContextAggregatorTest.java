package com.jozef.core.context;

import com.jozef.core.memory.FileLongTermMemory;
import com.jozef.core.session.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultContextAggregatorTest {

    @TempDir
    Path tempDir;

    @Test
    void aggregatesNotesAndActions() {
        FileLongTermMemory memory = new FileLongTermMemory(tempDir.resolve("memory.json").toString());
        memory.remember("Lubię kawę", List.of("preferencje"));

        Session session = new Session();
        session.addAction("open_application {name=Chrome} → Opened Chrome");

        DefaultContextAggregator aggregator = new DefaultContextAggregator(memory, 5);
        String context = aggregator.aggregate(session);

        assertThat(context).contains("Lubię kawę");
        assertThat(context).contains("open_application");
        assertThat(context).contains("Long-term memory");
        assertThat(context).contains("Recent actions");
    }

    @Test
    void returnsEmptyWhenNothingToAdd() {
        FileLongTermMemory memory = new FileLongTermMemory(tempDir.resolve("empty.json").toString());
        DefaultContextAggregator aggregator = new DefaultContextAggregator(memory, 5);

        assertThat(aggregator.aggregate(new Session())).isEmpty();
    }
}

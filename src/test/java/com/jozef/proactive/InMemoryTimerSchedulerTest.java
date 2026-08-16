package com.jozef.proactive;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTimerSchedulerTest {

    @Test
    void firesTimerWithMessage() throws InterruptedException {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        CountDownLatch latch = new CountDownLatch(1);
        List<String> fired = new CopyOnWriteArrayList<>();
        InMemoryTimerScheduler scheduler = new InMemoryTimerScheduler(executor, msg -> {
            fired.add(msg);
            latch.countDown();
        });

        scheduler.schedule("Przypomnij o mleku", Duration.ofMillis(50));

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(fired).containsExactly("Przypomnij o mleku");
        executor.shutdownNow();
    }

    @Test
    void cancelPreventsFiring() throws InterruptedException {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        List<String> fired = new CopyOnWriteArrayList<>();
        InMemoryTimerScheduler scheduler = new InMemoryTimerScheduler(executor, fired::add);

        String id = scheduler.schedule("nie odpali", Duration.ofMillis(200));
        assertThat(scheduler.cancel(id)).isTrue();
        assertThat(scheduler.cancel(id)).isFalse();

        Thread.sleep(400);
        assertThat(fired).isEmpty();
        executor.shutdownNow();
    }

    @Test
    void listsActiveTimersSortedByFireTime() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        InMemoryTimerScheduler scheduler = new InMemoryTimerScheduler(executor, msg -> {});
        scheduler.schedule("pierwszy", Duration.ofMinutes(1));
        scheduler.schedule("drugi", Duration.ofMinutes(2));

        assertThat(scheduler.active()).hasSize(2);
        assertThat(scheduler.active().get(0).message()).isEqualTo("pierwszy");
        executor.shutdownNow();
    }
}

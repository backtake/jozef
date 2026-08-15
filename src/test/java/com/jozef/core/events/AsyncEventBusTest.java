package com.jozef.core.events;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncEventBusTest {

    private record TestEvent(String payload) implements Event {
        @Override public String description() { return "TestEvent(" + payload + ")"; }
    }

    private record OtherEvent(String payload) implements Event {}

    private AsyncEventBus newBus() {
        ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "test-event");
            t.setDaemon(true);
            return t;
        });
        return new AsyncEventBus(executor);
    }

    @Test
    void listenerReceivesMatchingEvent() throws Exception {
        AsyncEventBus bus = newBus();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean received = new AtomicBoolean(false);

        bus.register(TestEvent.class, e -> {
            received.set(e.payload().equals("hello"));
            latch.countDown();
        });

        bus.publish(new TestEvent("hello"));

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received).isTrue();
        bus.close();
    }

    @Test
    void wildcardListenerReceivesAllEvents() throws Exception {
        AsyncEventBus bus = newBus();
        CountDownLatch latch = new CountDownLatch(2);

        bus.register(Event.class, e -> latch.countDown());

        bus.publish(new TestEvent("a"));
        bus.publish(new OtherEvent("b"));

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        bus.close();
    }

    @Test
    void listenerExceptionDoesNotKillBus() throws Exception {
        AsyncEventBus bus = newBus();
        CountDownLatch latch = new CountDownLatch(1);

        bus.register(TestEvent.class, e -> { throw new RuntimeException("boom"); });
        bus.register(TestEvent.class, e -> latch.countDown());

        bus.publish(new TestEvent("x"));

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        bus.close();
    }

    @Test
    void closeWaitsForInFlightListeners() throws Exception {
        AsyncEventBus bus = newBus();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);

        bus.register(TestEvent.class, e -> {
            started.countDown();
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            finished.countDown();
        });

        bus.publish(new TestEvent("slow"));
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

        bus.close();

        // close() must wait for the slow listener to complete.
        assertThat(finished.getCount()).isZero();
    }
}

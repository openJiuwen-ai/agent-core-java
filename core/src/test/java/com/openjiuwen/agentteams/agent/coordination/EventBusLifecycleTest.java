/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent.coordination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.core.common.concurrent.InterruptSignals;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Protects single-worker ownership across persistent-team pause and resume.
 *
 * @since 2026-11-09
 */
class EventBusLifecycleTest {
    @Test
    void stopReleasesLifecycleMonitorWhileWaitingForCallbackCleanup() throws Exception {
        try (Fixture fixture = new Fixture()) {
            CountDownLatch cleanupFinished = new CountDownLatch(1);
            fixture.start(() -> {
                fixture.bus.pausePolls();
                cleanupFinished.countDown();
            });
            Future<?> stop = fixture.stopAsynchronously();
            fixture.releaseCallback.countDown();

            assertTrue(cleanupFinished.await(1, TimeUnit.SECONDS), "Callback cleanup must acquire the bus monitor");
            stop.get(1, TimeUnit.SECONDS);
            assertThat(fixture.bus.isRunning()).isFalse();
        }
    }

    @Test
    void restartCannotReplaceWorkerUntilItsPreviousCallbackHasFinished() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.start(() -> { });
            Future<?> stop = fixture.stopAsynchronously();

            assertThatThrownBy(() -> fixture.bus.start(event -> { }))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("stopping");
            fixture.releaseCallback.countDown();
            stop.get(1, TimeUnit.SECONDS);
            CountDownLatch resumed = new CountDownLatch(1);
            fixture.bus.start(event -> resumed.countDown());
            fixture.bus.enqueue(message());
            assertTrue(resumed.await(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void stoppedWorkerDoesNotDispatchQueuedEventsIntoNextRound() throws Exception {
        try (Fixture fixture = new Fixture()) {
            AtomicInteger callbacks = new AtomicInteger();
            fixture.start(callbacks::incrementAndGet);
            fixture.bus.enqueue(message());
            Future<?> stop = fixture.stopAsynchronously();
            fixture.releaseCallback.countDown();
            stop.get(1, TimeUnit.SECONDS);

            assertThat(callbacks).hasValue(1);
        }
    }

    @Test
    void callbackCanRequestOwnStopWithoutWaitingForItself() throws Exception {
        try (Fixture fixture = new Fixture()) {
            CountDownLatch callbackStopped = new CountDownLatch(1);
            fixture.bus.start(event -> {
                fixture.bus.stop();
                callbackStopped.countDown();
            });
            fixture.bus.enqueue(message());
            assertTrue(callbackStopped.await(1, TimeUnit.SECONDS));
            fixture.bus.stop();
        }
    }

    @Test
    void timedOutStopRetainsWorkerOwnershipUntilCleanupFinishes() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.start(() -> { });
            Future<?> stop = fixture.stopAsynchronously();

            assertThatThrownBy(() -> stop.get(8, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class).hasRootCauseMessage(
                            "Team coordination loop is still stopping after the shutdown timeout");
            assertThatThrownBy(fixture.bus::start).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("stopping");
            fixture.releaseCallback.countDown();
            fixture.bus.stop();
            fixture.bus.start();
            assertThat(fixture.bus.isRunning()).isTrue();
        }
    }

    @Test
    void interruptedStopPreservesInterruptAndPreventsOverlappingRestart() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.start(() -> { });
            Future<?> stop = fixture.executor.submit(() -> {
                Thread.currentThread().interrupt();
                assertThatThrownBy(fixture.bus::stop).isInstanceOf(IllegalStateException.class)
                        .hasCauseInstanceOf(InterruptedException.class);
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            });
            stop.get(1, TimeUnit.SECONDS);
            assertThatThrownBy(fixture.bus::start).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("stopping");
            fixture.releaseCallback.countDown();
            fixture.bus.stop();
        }
    }

    private static EventMessage message() {
        return EventMessage.builder().eventType("lifecycle-test").build();
    }

    private static final class Fixture implements AutoCloseable {
        private final EventBus bus = new EventBus(TeamRole.HUMAN_AGENT);
        private final CountDownLatch callbackEntered = new CountDownLatch(1);
        private final CountDownLatch releaseCallback = new CountDownLatch(1);
        private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(2), runnable -> new Thread(runnable, "team-lifecycle-stop-test"));

        private void start(Runnable cleanup) throws InterruptedException {
            bus.start(event -> {
                callbackEntered.countDown();
                try {
                    assertTrue(releaseCallback.await(15, TimeUnit.SECONDS));
                } catch (InterruptedException failure) {
                    InterruptSignals.restoreCurrentThread();
                    throw new AssertionError("Interrupted while holding the previous round", failure);
                }
                cleanup.run();
            });
            bus.enqueue(message());
            assertTrue(callbackEntered.await(2, TimeUnit.SECONDS));
        }

        private Future<?> stopAsynchronously() {
            Future<?> stop = executor.submit(bus::stop);
            await().atMost(Duration.ofSeconds(2)).until(() -> !bus.isRunning());
            return stop;
        }

        @Override
        public void close() throws InterruptedException {
            releaseCallback.countDown();
            bus.stop();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(8, TimeUnit.SECONDS));
        }
    }
}

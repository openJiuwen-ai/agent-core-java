/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent.coordination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.core.common.concurrent.InterruptSignals;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

            stop.get(8, TimeUnit.SECONDS);
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
                assertDoesNotThrow(fixture.bus::stop);
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            });
            stop.get(1, TimeUnit.SECONDS);
            assertThatThrownBy(fixture.bus::start).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("stopping");
            fixture.releaseCallback.countDown();
            fixture.bus.stop();
        }
    }

    @Test
    void fatalCallbackRetiresWorkerWithoutPoisoningRestart() throws Exception {
        try (Fixture fixture = new Fixture(); FailureProbe probe = new FailureProbe()) {
            fixture.bus.start(event -> probe.failAfterInstallingDelayedHandler());
            fixture.bus.enqueue(message());
            assertTrue(probe.handlerEntered.await(3, TimeUnit.SECONDS));
            assertThat(fixture.bus.isRunning()).isFalse();

            AtomicInteger resumedCalls = new AtomicInteger();
            fixture.bus.start(event -> resumedCalls.incrementAndGet());
            fixture.bus.enqueue(message());
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(resumedCalls).hasValue(1));
            probe.releaseHandlerAndJoin();
            assertThat(fixture.bus.isRunning()).isTrue();
            fixture.bus.enqueue(message());
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(resumedCalls).hasValue(2));
        }
    }

    @Test
    void fixtureCloseReleasesOwnedExecutorEvenWhenBusStopThrows() throws Exception {
        EventBus failingBus = mock(EventBus.class);
        doThrow(new IllegalStateException("injected stop failure")).when(failingBus).stop();
        Fixture fixture = new Fixture(failingBus);
        try {
            assertThat(fixture.executor.submit(() -> true).get(2, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(fixture::close).isInstanceOf(IllegalStateException.class)
                    .hasMessage("injected stop failure");
            assertThat(fixture.executor.isTerminated()).isTrue();
        } finally {
            fixture.executor.shutdownNow();
            assertTrue(fixture.executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    private static EventMessage message() {
        return EventMessage.builder().eventType("lifecycle-test").build();
    }

    private static final class Fixture implements AutoCloseable {
        private final EventBus bus;
        private final CountDownLatch callbackEntered = new CountDownLatch(1);
        private final CountDownLatch releaseCallback = new CountDownLatch(1);
        private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(2), runnable -> new Thread(runnable, "team-lifecycle-stop-test"));

        private Fixture() {
            this(new EventBus(TeamRole.HUMAN_AGENT));
        }

        private Fixture(EventBus bus) {
            this.bus = bus;
        }

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
            try {
                bus.stop();
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(8, TimeUnit.SECONDS));
            }
        }
    }

    private static final class FailureProbe implements AutoCloseable {
        private final CountDownLatch handlerEntered = new CountDownLatch(1);
        private final CountDownLatch releaseHandler = new CountDownLatch(1);
        private final AtomicReference<Thread> failedWorker = new AtomicReference<>();

        private void failAfterInstallingDelayedHandler() {
            Thread worker = Thread.currentThread();
            failedWorker.set(worker);
            Thread.UncaughtExceptionHandler original = worker.getUncaughtExceptionHandler();
            worker.setUncaughtExceptionHandler((thread, failure) -> {
                handlerEntered.countDown();
                awaitHandlerRelease();
                original.uncaughtException(thread, failure);
            });
            throw new BaseError(StatusCode.WORKFLOW_EXECUTION_ERROR, "injected callback failure", null, null);
        }

        private void awaitHandlerRelease() {
            try {
                assertTrue(releaseHandler.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException failure) {
                InterruptSignals.restoreCurrentThread();
                throw new AssertionError("Interrupted while delaying the failed worker's handler", failure);
            }
        }

        private void releaseHandlerAndJoin() throws InterruptedException {
            releaseHandler.countDown();
            Thread worker = failedWorker.get();
            if (worker != null) {
                worker.join(2_000L);
                assertThat(worker.isAlive()).isFalse();
            }
        }

        @Override
        public void close() throws InterruptedException {
            releaseHandlerAndJoin();
        }
    }
}

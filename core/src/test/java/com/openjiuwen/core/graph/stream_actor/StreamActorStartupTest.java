/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.stream_actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.concurrent.InterruptSignals;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.workflow.component.ComponentAbility;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Verifies that stream dispatch and batch observers wait for the same startup signal.
 *
 * @since 2026-10-30
 */
class StreamActorStartupTest {
    @Test
    void batchObserverWaitsForScheduledConsumerReadiness() throws Exception {
        try (Fixture fixture = new Fixture(5000)) {
            Future<?> send = fixture.executor.submit(fixture::send);
            assertTrue(fixture.consumer.entered.await(5, TimeUnit.SECONDS));
            Future<?> batch = fixture.executor.submit(fixture.actor::awaitStarted);

            assertThrows(TimeoutException.class, () -> batch.get(100, TimeUnit.MILLISECONDS));
            assertThat(send.isDone()).isFalse();
            fixture.consumer.release.countDown();
            batch.get(5, TimeUnit.SECONDS);
            send.get(5, TimeUnit.SECONDS);
            assertThat(fixture.consumer.calls.get()).isEqualTo(1);
        }
    }

    @Test
    void startupTimeoutCancelsConsumerAndRejectsLaterDispatch() throws Exception {
        try (Fixture fixture = new Fixture(1000)) {
            BaseError error = assertThrows(BaseError.class, fixture::send);

            assertThat(error).hasRootCauseInstanceOf(TimeoutException.class);
            assertTrue(fixture.consumer.exited.await(5, TimeUnit.SECONDS));
            assertThat(fixture.consumer.interrupted.get()).isTrue();
            assertThrows(CompletionException.class, fixture::send);
            assertThat(fixture.consumer.calls.get()).isEqualTo(1);
        }
    }

    @Test
    void interruptedStartupDoesNotContinueDispatchAndPreservesInterrupt() throws Exception {
        try (Fixture fixture = new Fixture(5000)) {
            CountDownLatch senderExited = new CountDownLatch(1);
            AtomicBoolean interruptPreserved = new AtomicBoolean();
            AtomicReference<BaseError> failure = new AtomicReference<>();
            Future<?> send = fixture.executor.submit(() -> {
                try {
                    fixture.send();
                } catch (BaseError error) {
                    interruptPreserved.set(Thread.currentThread().isInterrupted());
                    failure.set(error);
                } finally {
                    senderExited.countDown();
                }
            });
            assertTrue(fixture.consumer.entered.await(5, TimeUnit.SECONDS));
            assertTrue(send.cancel(true));
            assertTrue(senderExited.await(5, TimeUnit.SECONDS));
            assertTrue(fixture.consumer.exited.await(5, TimeUnit.SECONDS));
            assertThat(interruptPreserved.get()).isTrue();
            assertThat(failure.get()).hasRootCauseInstanceOf(InterruptedException.class);
            assertThat(fixture.consumer.interrupted.get()).isTrue();
        }
    }

    @Test
    void inactiveConsumerNeedsNoStartupWait() throws Exception {
        try (Fixture fixture = new Fixture(100)) {
            fixture.actor.awaitStarted();
            assertThat(fixture.consumer.calls.get()).isZero();
        }
    }

    @Test
    void completedConsumerFailureDoesNotBecomeANewStartupFailure() throws Exception {
        try (Fixture fixture = new Fixture(5000)) {
            fixture.consumer.failAfterReady = true;
            fixture.consumer.release.countDown();
            fixture.send();
            assertTrue(fixture.consumer.exited.await(5, TimeUnit.SECONDS));
            fixture.actor.awaitCompletion();

            // Late frames retain the completed run's discard behavior instead of rethrowing its old error.
            fixture.send();
            fixture.actor.awaitStarted();
            assertThat(fixture.consumer.calls.get()).isEqualTo(1);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final DelayedConsumer consumer = new DelayedConsumer();
        private final StreamActor actor;
        private final ThreadPoolExecutor executor;

        private Fixture(long timeoutMillis) {
            AtomicInteger threadIds = new AtomicInteger();
            executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(4),
                    work -> new Thread(work, "stream-start-test-" + threadIds.incrementAndGet()));
            actor = new StreamActor("end", consumer, List.of(), List.of(), 1.0d, timeoutMillis);
        }

        private void send() {
            actor.send("frame", ComponentAbility.STREAM, true, "producer");
        }

        @Override
        public void close() throws InterruptedException {
            consumer.release.countDown();
            actor.shutdown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static final class DelayedConsumer implements StreamConsumer {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch exited = new CountDownLatch(1);
        private final AtomicBoolean interrupted = new AtomicBoolean();
        private final AtomicInteger calls = new AtomicInteger();
        private boolean failAfterReady;

        @Override
        public void streamCall(CountDownLatch latch, Consumer<Exception> errorCallback) {
            calls.incrementAndGet();
            entered.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
                latch.countDown();
                if (failAfterReady) {
                    errorCallback.accept(new IllegalStateException("Consumer failed after becoming ready"));
                }
            } catch (InterruptedException exception) {
                interrupted.set(true);
                InterruptSignals.restoreCurrentThread();
                errorCallback.accept(exception);
            } finally {
                exited.countDown();
            }
        }

        @Override
        public boolean shouldHandleMessage() {
            return true;
        }

        @Override
        public boolean isDone() {
            return true;
        }
    }
}

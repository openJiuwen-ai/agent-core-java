/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.messager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openjiuwen.agentteams.messager.TransportHandlerDispatcher.DispatchResult;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Verifies bounded admission, shutdown diagnostics, and cleanup of failed asynchronous handlers.
 *
 * @since 2026-11-06
 */
@Timeout(10)
class TransportHandlerDispatcherTest {
    @Test
    void reportsCapacityAndClosedSeparately() {
        try (TransportHandlerDispatcher dispatcher = new TransportHandlerDispatcher("capacity-test")) {
            CompletableFuture<Void> response = new CompletableFuture<>();
            for (int index = 0; index < 256; index++) {
                assertThat(dispatcher.dispatch(message -> response, event())).isEqualTo(DispatchResult.ACCEPTED);
            }

            assertThat(dispatcher.dispatch(message -> response, event()))
                    .isEqualTo(DispatchResult.CAPACITY_EXHAUSTED);
            dispatcher.close();

            assertThat(dispatcher.dispatch(message -> response, event())).isEqualTo(DispatchResult.CLOSED);
            assertThat(dispatcher.hasPending()).isFalse();
        }
    }

    @Test
    void rejectedExecutorDoesNotLeavePendingWork() {
        ExecutorService executor = OpenJiuwenExecutors.newSingleThreadExecutor("rejected-handler-test", true);
        try (TransportHandlerDispatcher dispatcher = new TransportHandlerDispatcher(executor)) {
            executor.shutdown();

            assertThat(dispatcher.dispatch(message -> CompletableFuture.completedFuture(null), event()))
                    .isEqualTo(DispatchResult.SUBMISSION_REJECTED);
            assertThat(dispatcher.hasPending()).isFalse();
        }
    }

    @Test
    void synchronousAndAsynchronousFailuresReleaseAdmissionSlots() throws Exception {
        ExecutorService executor = OpenJiuwenExecutors.newSingleThreadExecutor("failed-handler-test", true);
        try (TransportHandlerDispatcher dispatcher = new TransportHandlerDispatcher(executor)) {
            assertThat(dispatcher.dispatch(message -> {
                throw new IllegalStateException("synchronous handler failure");
            }, event())).isEqualTo(DispatchResult.ACCEPTED);
            assertThat(dispatcher.dispatch(message -> CompletableFuture.failedFuture(
                    new IllegalArgumentException("asynchronous handler failure")), event()))
                    .isEqualTo(DispatchResult.ACCEPTED);
            assertThat(dispatcher.dispatch(message -> null, event())).isEqualTo(DispatchResult.ACCEPTED);

            // A barrier on the same single-thread executor observes completion of all three handlers.
            executor.submit(() -> Boolean.TRUE).get(3, TimeUnit.SECONDS);

            assertThat(dispatcher.hasPending()).isFalse();
        }
    }

    @Test
    void interruptedShutdownPreservesStatusAndRemainingBudget() throws InterruptedException {
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
        ExecutorService executor = mock(ExecutorService.class);
        when(executor.awaitTermination(anyLong(), eq(TimeUnit.NANOSECONDS)))
                .thenThrow(new InterruptedException("shutdown interrupted"))
                .thenReturn(true);
        try (TransportHandlerDispatcher dispatcher = new TransportHandlerDispatcher(executor)) {
            long budgetNanos = TimeUnit.SECONDS.toNanos(3L);
            dispatcher.close(System.nanoTime() + budgetNanos);

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(dispatcher.hasPending()).isFalse();
            ArgumentCaptor<Long> timeouts = ArgumentCaptor.forClass(Long.class);
            verify(executor).shutdownNow();
            verify(executor, times(2)).awaitTermination(timeouts.capture(), eq(TimeUnit.NANOSECONDS));
            assertThat(timeouts.getAllValues().get(0)).isBetween(0L, budgetNanos);
            assertThat(timeouts.getAllValues().get(1)).isLessThanOrEqualTo(timeouts.getAllValues().get(0));
        } finally {
            // Clear only the interrupt injected by this test before returning to JUnit.
            Thread.interrupted();
        }
    }

    @Test
    void expiredDeadlineDoesNotAddAnotherShutdownBudget() throws InterruptedException {
        ExecutorService executor = mock(ExecutorService.class);
        try (TransportHandlerDispatcher dispatcher = new TransportHandlerDispatcher(executor)) {
            dispatcher.close(System.nanoTime());

            verify(executor).shutdownNow();
            verify(executor).awaitTermination(0L, TimeUnit.NANOSECONDS);
            assertThat(dispatcher.dispatch(message -> CompletableFuture.completedFuture(null), event()))
                    .isEqualTo(DispatchResult.CLOSED);
        }
    }

    private static EventMessage event() {
        return EventMessage.builder().eventType("test").build();
    }
}

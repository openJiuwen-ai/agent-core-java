/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.messager;

import com.openjiuwen.agentteams.messager.TransportHandlerDispatcher.DispatchResult;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static EventMessage event() {
        return EventMessage.builder().eventType("test").build();
    }
}

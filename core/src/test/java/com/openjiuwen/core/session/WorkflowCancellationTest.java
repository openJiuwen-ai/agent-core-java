/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(10)
class WorkflowCancellationTest {
    @Test
    void cancellationEndsPendingModelWaitAndDoesNotLeakAnInterrupt() throws Exception {
        WorkflowCancellation control = new WorkflowCancellation();
        CountDownLatch registered = new CountDownLatch(1);
        CompletableFuture<String> model = new CompletableFuture<>();
        CompletableFuture<Boolean> worker = CompletableFuture.supplyAsync(() -> {
            try (var registration = control.register()) {
                registered.countDown();
                assertThatThrownBy(() -> control.await(model)).isInstanceOf(CancellationException.class);
            }
            return Thread.currentThread().isInterrupted();
        });
        assertThat(registered.await(5, TimeUnit.SECONDS)).isTrue();
        control.cancel();
        assertThat(worker.get(5, TimeUnit.SECONDS)).isFalse();
        assertThat(model).isCancelled();
        control.awaitQuiescence(Duration.ofSeconds(1));
        assertThatThrownBy(control::register).isInstanceOf(CancellationException.class);
    }

    @Test
    void completedModelAndIndependentControlsAreUnaffected() {
        WorkflowCancellation first = new WorkflowCancellation();
        WorkflowCancellation second = new WorkflowCancellation();
        assertThat(first.await(CompletableFuture.completedFuture("done"))).isEqualTo("done");
        first.cancel();
        assertThat(second.await(CompletableFuture.completedFuture("other"))).isEqualTo("other");
    }
}

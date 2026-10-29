/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.common.concurrent.InterruptSignals;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeSession;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeState;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Verifies workflow cancellation, pending model waits, and worker interrupt cleanup.
 *
 * @since 2026-10-28
 */
@Timeout(10)
class WorkflowCancellationTest {
    @Test
    void absentCancellationRemainsNullAndAttachedControlRetainsIdentity() {
        WorkflowSession original = new WorkflowSession("cancellation-contract");
        WorkflowRuntimeSession runtime = new WorkflowRuntimeSession("workflow", null,
                original.getSessionId(), WorkflowRuntimeState.create(), null);
        WorkflowCancellation control = new WorkflowCancellation();
        assertThat(WorkflowCancellation.from(null)).isNull();
        assertThat(WorkflowCancellation.from(runtime)).isNull();
        runtime.config().setEnvs(control.attach(original).getEnvs());
        assertThat(WorkflowCancellation.from(runtime)).isSameAs(control);
        assertThat(original.getEnvs()).isNull();
    }

    @Test
    void nestedRegistrationsClearOnlyInvocationOwnedInterruptsAtTheOutermostExit() {
        WorkflowCancellation control = new WorkflowCancellation();
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
        try (var outer = control.register()) {
            try (var inner = control.register()) {
                control.cancel();
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            }
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        }
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
        control.cancel();
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
        control.awaitQuiescence(Duration.ofSeconds(1));
    }

    @Test
    void cancellationPreservesAnInterruptThatPredatesRegistration() {
        WorkflowCancellation control = new WorkflowCancellation();
        try {
            InterruptSignals.restoreCurrentThread();
            try (var registration = control.register()) {
                control.cancel();
            }
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void interruptedDrainRetainsTheSignalAndItsFailureCause() {
        WorkflowCancellation control = new WorkflowCancellation();
        try (var registration = control.register()) {
            InterruptSignals.restoreCurrentThread();
            assertThatThrownBy(() -> control.awaitQuiescence(Duration.ofSeconds(1)))
                    .isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

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

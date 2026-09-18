/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.spawn;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class InProcessSpawnHandleTest {

    @Test
    void defaultConstructorCreatesShortProcessId() {
        InProcessSpawnHandle handle = new InProcessSpawnHandle();

        assertThat(handle.getProcessId()).hasSize(12);
        assertThat(handle.isAlive()).isFalse();
        assertThat(handle.isHealthy()).isFalse();
    }

    @Test
    void shutdownCancelsActiveTaskAndMarksHandleUnhealthy() {
        CompletableFuture<Void> task = new CompletableFuture<>();
        InProcessSpawnHandle handle = new InProcessSpawnHandle("proc-1", task);

        boolean finished = handle.shutdown(0.1).join();

        assertThat(finished).isTrue();
        assertThat(task.isCancelled()).isTrue();
        assertThat(handle.isHealthy()).isFalse();
        assertThat(handle.isShutdownRequested()).isTrue();
    }

    @Test
    void waitForCompletionReturnsZeroOnSuccessAndMinusOneOnFailure() {
        InProcessSpawnHandle successHandle = new InProcessSpawnHandle("proc-2", CompletableFuture.completedFuture(null));
        CompletableFuture<Void> failedTask = new CompletableFuture<>();
        failedTask.completeExceptionally(new IllegalStateException("boom"));
        InProcessSpawnHandle failureHandle = new InProcessSpawnHandle("proc-3", failedTask);

        assertThat(successHandle.waitForCompletion().join()).isZero();
        assertThat(failureHandle.waitForCompletion().join()).isEqualTo(-1);
    }

    @Test
    void healthCheckMethodsAreNoOps() {
        InProcessSpawnHandle handle = new InProcessSpawnHandle("proc-4");

        assertThat(handle.startHealthCheck(null).join()).isNull();
        assertThat(handle.stopHealthCheck().join()).isNull();
    }
}

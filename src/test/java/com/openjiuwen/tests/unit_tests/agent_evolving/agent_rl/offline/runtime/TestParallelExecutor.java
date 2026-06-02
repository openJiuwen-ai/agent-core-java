/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl.offline.runtime;

import com.openjiuwen.agent_evolving.agent_rl.offline.coordinator.TaskQueue;
import com.openjiuwen.agent_evolving.agent_rl.offline.runtime.ParallelRuntimeExecutor;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RLTask;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ParallelExecutor.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/agent_evolving/agent_rl/offline/runtime/test_parallel_executor.py}.
 */
@DisplayName("ParallelExecutor Tests")
class TestParallelExecutor {

    @Test
    @DisplayName("start then stop flips isRunning")
    void testStartThenStopIsRunningFlip() {
        ParallelRuntimeExecutor executor = new ParallelRuntimeExecutor(new TaskQueue(), 1);

        assertThat(executor.isRunning()).isFalse();

        executor.start();
        assertThat(executor.isRunning()).isTrue();

        executor.stop();
        executor.shutdown();
        assertThat(executor.isRunning()).isFalse();
    }

    @Test
    @DisplayName("setters affect execution")
    void testSettersAffectExecution() throws Exception {
        TaskQueue taskQueue = new TaskQueue();
        RLTask task = new RLTask("tid1", "oid1", Map.of(), 0);
        taskQueue.queueTask(task);
        ParallelRuntimeExecutor executor = new ParallelRuntimeExecutor(taskQueue, 1);

        executor.start();
        RolloutMessage collected = waitForRollout(taskQueue, "tid1");
        executor.stop();
        executor.shutdown();

        assertThat(collected).isNotNull();
        assertThat(collected.getTaskId()).isEqualTo("tid1");
        assertThat(collected.getOriginTaskId()).isEqualTo("oid1");
        assertThat(collected.getRolloutInfo()).isEmpty();
    }

    private static RolloutMessage waitForRollout(TaskQueue taskQueue, String rolloutId) throws InterruptedException {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (System.nanoTime() < deadline) {
            Map<String, RolloutMessage> rollouts = taskQueue.getRollouts();
            if (rollouts.containsKey(rolloutId)) {
                return rollouts.get(rolloutId);
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for rollout " + rolloutId);
    }
}

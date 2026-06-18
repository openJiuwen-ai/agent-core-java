/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.runtime;

import com.openjiuwen.agent_evolving.agent_rl.offline.coordinator.TaskQueue;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RLTask;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code ParallelRuntimeExecutor} in
 * {@code openjiuwen/agent_evolving/agent_rl/offline/runtime/parallel_executor.py}.
 */
class ParallelRuntimeExecutorTest {

    @Test
    void startThenStopIsRunningFlip() {
        TaskQueue queue = new TaskQueue();
        ParallelRuntimeExecutor executor = new ParallelRuntimeExecutor(queue, 1);

        assertFalse(executor.isRunning());
        executor.start();
        assertTrue(executor.isRunning());

        executor.stop();

        assertFalse(executor.isRunning());
        assertEquals(0, executor.getRuntimeTaskCount());
    }

    @Test
    void duplicateStartKeepsExistingWorkerSet() {
        TaskQueue queue = new TaskQueue();
        ParallelRuntimeExecutor executor = new ParallelRuntimeExecutor(queue, 1);

        executor.start();
        executor.start();

        assertEquals(1, executor.getRuntimeTaskCount());
        executor.stop();
    }

    @Test
    void workerLoopExecutesQueuedTaskAndStoresRolloutByTaskId() {
        TaskQueue queue = new TaskQueue();
        RLTask task = sampleTask();
        queue.queueTask(task);
        ParallelRuntimeExecutor executor = new ParallelRuntimeExecutor(queue, 1);

        executor.start();
        assertTrue(waitUntil(queue::isFinished, Duration.ofSeconds(2)));
        executor.stop();

        Map<String, RolloutMessage> rollouts = queue.getRollouts();
        assertEquals(1, rollouts.size());
        assertTrue(rollouts.containsKey("tid1"));
        assertEquals("tid1", rollouts.get("tid1").getTaskId());
        assertEquals("tid1", rollouts.get("tid1").getRolloutId());
    }

    @Test
    void settersStoreExecutorHelpersForFutureWorkers() {
        TaskQueue queue = new TaskQueue();
        ParallelRuntimeExecutor executor = new ParallelRuntimeExecutor(queue, 1);

        executor.setAgentFactory(task -> new Object());
        executor.setTaskDataFn(sample -> Map.of("conversation_id", "custom"));
        executor.setRewardFn(message -> Map.of("reward_list", java.util.List.of(1), "global_reward", 1));

        assertSame(executor.getAgentFactory(), executor.getAgentFactory());
        assertEquals("custom", executor.getTaskDataFn().apply(Map.of()).get("conversation_id"));
        assertEquals(1, executor.getRewardFn().apply(new RolloutMessage()).get("global_reward"));
    }

    private static RLTask sampleTask() {
        RLTask task = new RLTask();
        task.setTaskId("tid1");
        task.setOriginTaskId("oid1");
        task.setTaskSample(Map.of());
        task.setRoundNum(0);
        return task;
    }

    private static boolean waitUntil(BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(20L);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }
}

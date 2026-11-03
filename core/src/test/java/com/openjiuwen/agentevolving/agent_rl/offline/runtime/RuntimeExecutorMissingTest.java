/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.offline.runtime;

import com.openjiuwen.agentevolving.agent_rl.schemas.RLTask;
import com.openjiuwen.agentevolving.agent_rl.schemas.RolloutMessage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Mirrors Python's runtime executor tests in
 * {@code tests/unit_tests/agent_evolving/agent_rl/offline/runtime/test_runtime_executor.py}.
 */
class RuntimeExecutorMissingTest {

    @Test
    void executeAsyncWithoutAgentFactoryReturnsEmptyRollout() {
        RuntimeExecutor executor = new RuntimeExecutor();

        RolloutMessage result = executor.executeAsync(sampleTask()).toCompletableFuture().join();

        assertEquals(0, result.getRolloutInfo().size());
        assertEquals(0, result.getRewardList().size());
        assertEquals(0, result.getTurnCount());
        assertEquals("t1", result.getTaskId());
        assertEquals("o1", result.getOriginTaskId());
    }

    @Test
    void executeAsyncAgentFactoryExceptionReturnsEmptyRollout() {
        RuntimeExecutor executor = new RuntimeExecutor(task -> {
            throw new IllegalArgumentException("fail");
        }, null, null);

        RolloutMessage result = executor.executeAsync(sampleTask()).toCompletableFuture().join();

        assertNotNull(result);
        assertEquals(0, result.getRolloutInfo().size());
        assertEquals(0, result.getRewardList().size());
    }

    private static RLTask sampleTask() {
        RLTask task = new RLTask();
        task.setTaskId("t1");
        task.setOriginTaskId("o1");
        task.setTaskSample(Map.of());
        task.setRoundNum(0);
        return task;
    }
}

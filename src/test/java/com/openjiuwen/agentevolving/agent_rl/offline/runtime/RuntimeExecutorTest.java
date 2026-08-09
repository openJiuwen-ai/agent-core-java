/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.offline.runtime;

import com.openjiuwen.agentevolving.agent_rl.RLRail;
import com.openjiuwen.agentevolving.agent_rl.schemas.RLTask;
import com.openjiuwen.agentevolving.agent_rl.schemas.RolloutMessage;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code RuntimeExecutor} in
 * {@code openjiuwen/agent_evolving/agent_rl/offline/runtime/runtime_executor.py}.
 */
class RuntimeExecutorTest {

    @Test
    void missingAgentFactoryReturnsInitialMessageWithoutRewardPhase() {
        RuntimeExecutor executor = new RuntimeExecutor();
        executor.setRewardFn(message -> Map.of("reward_list", List.of(1), "global_reward", 1));
        RLTask task = task("task-1", Map.of("query", "hello"));

        RolloutMessage message = executor.execute(task);

        assertEquals("task-1", message.getTaskId());
        assertEquals("origin-task-1", message.getOriginTaskId());
        assertNotNull(message.getRolloutId());
        assertNotNull(message.getStartTime());
        assertNull(message.getEndTime());
        assertEquals(0, message.getTurnCount());
        assertEquals(List.of(), message.getRewardList());
        assertEquals(0.0d, message.getGlobalReward());
    }

    @Test
    void agentFactoryTaskDataAndRewardMirrorPythonFlow() {
        CapturingAgent agent = new CapturingAgent();
        RuntimeExecutor executor = new RuntimeExecutor();
        executor.setAgentFactory(task -> agent);
        executor.setTaskDataFn(sample -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("query", sample.get("query"));
            result.put("ground_truth", sample.get("ground_truth"));
            return result;
        });
        executor.setRewardFn(message -> Map.of("reward_list", List.of("0.25", 0.75d), "global_reward", "1.0"));

        RolloutMessage message = executor.execute(task("task-2", Map.of("query", "q", "ground_truth", "gt")));

        assertEquals("rollout-task-2", message.getRolloutId());
        assertEquals("task-2", message.getTaskId());
        assertEquals("origin-task-2", message.getOriginTaskId());
        assertEquals(0, message.getTurnCount());
        assertEquals(List.of(0.25d, 0.75d), message.getRewardList());
        assertEquals(1.0d, message.getGlobalReward());
        assertNotNull(message.getEndTime());
        assertEquals("task-2", agent.lastInputs.get("conversation_id"));
        assertEquals("q", agent.lastInputs.get("query"));
        assertTrue(agent.registered);
        assertTrue(agent.unregistered);
    }

    private static RLTask task(String taskId, Map<String, Object> sample) {
        RLTask task = new RLTask();
        task.setTaskId(taskId);
        task.setOriginTaskId("origin-" + taskId);
        task.setTaskSample(sample);
        task.setRoundNum(3);
        return task;
    }

    static final class CapturingAgent {
        private final AgentCard card = new AgentCard("capturing-agent", "CapturingAgent", "test agent");
        private boolean registered;
        private boolean unregistered;
        private Map<String, Object> lastInputs;

        public AgentCard getCard() {
            return card;
        }

        public void registerRail(RLRail rail) {
            registered = true;
        }

        public void unregisterRail(RLRail rail) {
            unregistered = true;
        }

        public Object invoke(Map<String, Object> inputs, AgentSession session) {
            lastInputs = new LinkedHashMap<>(inputs);
            return Map.of("ok", true);
        }
    }
}

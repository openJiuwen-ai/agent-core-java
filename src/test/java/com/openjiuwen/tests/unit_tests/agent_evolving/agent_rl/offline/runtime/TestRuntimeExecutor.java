/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl.offline.runtime;

import com.openjiuwen.agent_evolving.agent_rl.offline.runtime.RuntimeExecutor;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RLTask;
import com.openjiuwen.agent_evolving.agent_rl.schemas.Rollout;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;
import com.openjiuwen.agent_evolving.trajectory.LLMCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RuntimeExecutor.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/agent_evolving/agent_rl/offline/runtime/test_runtime_executor.py}.
 */
@DisplayName("RuntimeExecutor Tests")
class TestRuntimeExecutor {

    @Test
    @DisplayName("execute without agent factory returns empty rollout")
    void testExecuteAsyncNeitherAgentFactoryReturnsEmptyRollout() {
        RLTask sampleTask = sampleTask();
        RuntimeExecutor executor = new RuntimeExecutor();

        RolloutMessage result = executor.execute(sampleTask);

        assertThat(result.getRolloutInfo()).isEmpty();
        assertThat(result.getRewardList()).isEmpty();
        assertThat(result.getTurnCount()).isZero();
        assertThat(result.getTaskId()).isEqualTo(sampleTask.getTaskId());
        assertThat(result.getOriginTaskId()).isEqualTo(sampleTask.getOriginTaskId());
        assertThat(result.getGlobalReward()).isEqualTo(0.0d);
    }

    @Test
    @DisplayName("agent factory exception returns empty rollout")
    void testExecuteAsyncAgentFactoryExceptionReturnsEmptyRollout() {
        Function<RLTask, Object> agentFactory = task -> {
            throw new IllegalArgumentException("fail");
        };
        RuntimeExecutor executor = new RuntimeExecutor(agentFactory, null, null);

        RolloutMessage result = executor.execute(sampleTask());

        assertThat(result).isNotNull();
        assertThat(result.getRolloutInfo()).isEmpty();
        assertThat(result.getRewardList()).isEmpty();
        assertThat(result.getTurnCount()).isZero();
    }

    @Test
    @DisplayName("execute collects trajectory rollouts and applies reward")
    void testExecuteCollectsTrajectoryAndAppliesReward() {
        FakeAgent agent = new FakeAgent();
        Function<RLTask, Object> agentFactory = task -> agent;
        Function<Map<String, Object>, Map<String, Object>> taskDataFn = sample -> {
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("query", sample.getOrDefault("query", ""));
            inputs.put("ground_truth", sample.getOrDefault("ground_truth", ""));
            return inputs;
        };
        Function<RolloutMessage, Map<String, Object>> rewardFn = message -> Map.of(
                "reward_list", List.of("1.5"),
                "global_reward", 1.5d
        );
        RuntimeExecutor executor = new RuntimeExecutor(agentFactory, taskDataFn, rewardFn);
        RLTask task = new RLTask("t1", "o1", Map.of("query", "question", "ground_truth", "answer"), 2);

        RolloutMessage result = executor.execute(task);

        assertThat(agent.registeredRail).isNotNull();
        assertThat(agent.unregisteredRail).isSameAs(agent.registeredRail);
        assertThat(agent.seenInputs).containsEntry("query", "question");
        assertThat(agent.seenInputs).containsEntry("conversation_id", "t1");

        assertThat(result.getRolloutId()).isEqualTo("rollout-t1");
        assertThat(result.getTaskId()).isEqualTo("t1");
        assertThat(result.getOriginTaskId()).isEqualTo("o1");
        assertThat(result.getRoundNum()).isEqualTo(2);
        assertThat(result.getTurnCount()).isEqualTo(1);
        assertThat(result.getRewardList()).containsExactly(1.5d);
        assertThat(result.getGlobalReward()).isEqualTo(1.5d);
        assertThat(result.getEndTime()).isNotBlank();

        Rollout rollout = result.getRolloutInfo().get(0);
        assertThat(rollout.getTurnId()).isZero();
        assertThat(rollout.getInputPrompt()).containsEntry("ground_truth", "answer");
        assertThat(rollout.getOutputResponse()).containsEntry("content", "agent response");
        assertThat(rollout.getLlmConfig()).containsEntry("model", "agentrl");
        assertThat(rollout.getInputPromptIds()).containsExactly(1, 2);
        assertThat(rollout.getOutputResponseIds()).containsExactly(3, 4);
    }

    @Test
    @DisplayName("executeWithParams wraps prompt as task")
    void testExecuteWithParamsWrapsPromptAsTask() {
        RuntimeExecutor executor = new RuntimeExecutor();

        RolloutMessage result = (RolloutMessage) executor.executeWithParams("hello", Map.of(
                "task_id", "task-param",
                "origin_task_id", "origin-param",
                "round_num", 3
        ));

        assertThat(result.getTaskId()).isEqualTo("task-param");
        assertThat(result.getOriginTaskId()).isEqualTo("origin-param");
        assertThat(result.getRoundNum()).isEqualTo(3);
        assertThat(result.getRolloutInfo()).isEmpty();
    }

    @Test
    @DisplayName("executeAsync returns future with rollout message")
    void testExecuteAsyncReturnsFuture() {
        RuntimeExecutor executor = new RuntimeExecutor();

        RolloutMessage result = executor.executeAsync(sampleTask()).join();

        assertThat(result.getTaskId()).isEqualTo("t1");
        assertThat(result.getOriginTaskId()).isEqualTo("o1");
        assertThat(result.getRolloutInfo()).isEmpty();
    }

    private static RLTask sampleTask() {
        return new RLTask("t1", "o1", Map.of(), 0);
    }

    static final class FakeAgent {
        private Object registeredRail;
        private Object unregisteredRail;
        private Map<String, Object> seenInputs;

        public FakeAgent registerRail(Object rail) {
            registeredRail = rail;
            return this;
        }

        public FakeAgent unregisterRail(Object rail) {
            unregisteredRail = rail;
            return this;
        }

        public Object invoke(Object inputs, Object session) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mapInputs = (Map<String, Object>) inputs;
            seenInputs = new LinkedHashMap<>(mapInputs);

            LLMCallDetail detail = LLMCallDetail.builder()
                    .messages(List.of(Map.of("role", "user", "content", seenInputs.get("query"))))
                    .tools(List.of(Map.of("name", "lookup")))
                    .response(Map.of("role", "assistant", "content", "agent response"))
                    .meta(Map.of(
                            "prompt_token_ids", List.of(1, 2),
                            "completion_token_ids", List.of(3, 4)
                    ))
                    .build();
            TrajectoryStep step = TrajectoryStep.builder()
                    .kind("llm")
                    .detail(detail)
                    .meta(Map.of("llm_config", Map.of("model", "agentrl")))
                    .build();
            return Trajectory.builder()
                    .executionId("exec-1")
                    .steps(List.of(step))
                    .build();
        }
    }
}

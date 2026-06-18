/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl;

import com.openjiuwen.agent_evolving.trajectory.InMemoryTrajectoryStore;
import com.openjiuwen.agent_evolving.trajectory.LLMCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.harness.rails.CallbackContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code RLRail} in
 * {@code openjiuwen/agent_evolving/agent_rl/rl_rail.py}.
 */
class RLRailTest {

    @Test
    void modelCallsReceiveRlMetadataWithZeroBasedTurnIds() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        RLRail rail = new RLRail("session-1", "rl_offline", "case-1", store);

        rail.beforeInvoke(ctx("conversation_id", "conv-1"));
        rail.afterModelCall(ctx(Map.of("model", "first", "messages", List.of(Map.of("role", "user", "content", "q1")))));
        rail.afterModelCall(ctx(Map.of("model", "second", "messages", List.of(Map.of("role", "user", "content", "q2")))));

        List<Map<String, Object>> llmSteps = rail.buildTrajectory().stream()
                .filter(step -> "llm".equals(step.get("kind")))
                .toList();

        assertThat(llmSteps).hasSize(2);
        assertThat(meta(llmSteps.get(0))).containsEntry("turn_id", 0)
                .containsEntry("source", "rl_offline")
                .containsEntry("case_id", "case-1")
                .doesNotContainKey("session_id");
        assertThat(meta(llmSteps.get(1))).containsEntry("turn_id", 1)
                .containsEntry("source", "rl_offline")
                .containsEntry("case_id", "case-1");
        assertThat(rail.getLlmStepCount()).isEqualTo(2);

        rail.afterInvoke(ctx("conversation_id", "conv-1"));
        List<Trajectory> trajectories = store.queryBySessionId("conv-1");
        assertThat(trajectories).hasSize(1);
        assertThat(trajectories.getFirst().getSteps()).hasSize(2);
        assertThat(trajectories.getFirst().getSteps().getFirst().getMeta())
                .containsEntry("turn_id", 0)
                .containsEntry("case_id", "case-1");
    }

    @Test
    void beforeInvokeResetsStepCounterAndAfterInvokeClearsTrajectoryWindow() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        RLRail rail = new RLRail("", "rl_offline", null, store);

        rail.beforeInvoke(ctx("conversation_id", "conv-1"));
        rail.afterModelCall(ctx("model", "first"));
        assertThat(rail.buildTrajectory()).isNotEmpty();

        rail.afterInvoke(ctx("conversation_id", "conv-1"));
        assertThat(rail.buildTrajectory()).isEmpty();

        rail.beforeInvoke(ctx("conversation_id", "conv-2"));
        rail.afterModelCall(ctx("model", "new-invoke"));

        Map<String, Object> lastStep = rail.buildTrajectory().getLast();
        assertThat(meta(lastStep)).containsEntry("turn_id", 0)
                .containsEntry("source", "rl_offline")
                .containsEntry("case_id", null);
        assertThat(rail.getLlmStepCount()).isEqualTo(1);
        rail.afterInvoke(ctx("conversation_id", "conv-2"));
        assertThat(store.queryBySessionId("conv-1").getFirst().getSteps()).hasSize(1);
        assertThat(store.queryBySessionId("conv-2").getFirst().getSteps()).hasSize(1);
    }

    @Test
    void toolCallPayloadInModelResponseIsPreserved() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        RLRail rail = new RLRail(null, null, null, store);
        Map<String, Object> response = Map.of(
                "role", "assistant",
                "content", "Thinking...",
                "tool_calls", List.of(Map.of(
                        "id", "tc1",
                        "type", "function",
                        "function", Map.of("name", "test_tool", "arguments", "{\"param\":\"value\"}")
                ))
        );

        rail.beforeInvoke(ctx("conversation_id", "conv"));
        rail.afterModelCall(ctx(Map.of("messages", List.of(Map.of("role", "user", "content", "q")), "response", response)));
        rail.afterInvoke(ctx("conversation_id", "conv"));

        assertThat(rail.getSessionId()).isEmpty();
        assertThat(rail.getSource()).isEqualTo("rl_offline");
        Trajectory trajectory = store.queryBySessionId("conv").getFirst();
        LLMCallDetail detail = (LLMCallDetail) trajectory.getSteps().getFirst().getDetail();
        assertThat(detail.getResponse()).isEqualTo(response);
    }

    @Test
    void singleInvokeKeepsMoreThanDefaultEvolutionWindow() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        RLRail rail = new RLRail("", "rl_offline", null, store);

        rail.beforeInvoke(ctx("conversation_id", "same-session"));
        for (int index = 0; index < 201; index++) {
            rail.afterModelCall(ctx(Map.of(
                    "messages", List.of(Map.of("role", "user", "content", "q" + index)),
                    "response", Map.of("role", "assistant", "content", "a" + index)
            )));
        }
        rail.afterInvoke(ctx("conversation_id", "same-session"));

        Trajectory trajectory = store.queryBySessionId("same-session").getFirst();
        assertThat(trajectory.getSteps()).hasSize(201);
        LLMCallDetail firstDetail = (LLMCallDetail) trajectory.getSteps().getFirst().getDetail();
        assertThat(firstDetail.getMessages().getFirst()).isEqualTo(Map.of("role", "user", "content", "q0"));
    }

    private static CallbackContext ctx(String key, Object value) {
        return new CallbackContext(null, Map.of(key, value));
    }

    private static CallbackContext ctx(Map<String, Object> values) {
        return new CallbackContext(null, new java.util.LinkedHashMap<>(values));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> meta(Map<String, Object> step) {
        return (Map<String, Object>) step.get("meta");
    }
}

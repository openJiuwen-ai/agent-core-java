/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.runtime;

import com.openjiuwen.agent_evolving.agent_rl.RLRail;
import com.openjiuwen.agent_evolving.trajectory.InMemoryTrajectoryStore;
import com.openjiuwen.agent_evolving.trajectory.LLMCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.harness.rails.CallbackContext;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's trajectory collector tests in
 * {@code tests/unit_tests/agent_evolving/agent_rl/offline/runtime/test_collector.py}.
 */
class TrajectoryCollectorMissingTest {

    @Test
    void rlRailUsesEvolutionRailFlow() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        RLRail rail = new RLRail("test-session", "rl_offline", "case-123", store);

        rail.beforeInvoke(ctx("conversation_id", "test-session"));
        rail.afterModelCall(ctx(Map.of(
                "messages", List.of(Map.of("role", "user", "content", "test query")),
                "tools", List.of(Map.of("name", "test_tool", "description", "test tool")))));
        rail.afterModelCall(ctx(Map.of(
                "messages", List.of(Map.of("role", "user", "content", "test query")),
                "tools", List.of(Map.of("name", "test_tool", "description", "test tool")),
                "response", Map.of("content", "test response", "tool_calls", List.of()))));
        rail.afterInvoke(ctx("conversation_id", "test-session"));

        List<Trajectory> trajectories = store.query(null, null, null);
        assertThat(trajectories).hasSize(1);
        assertThat(trajectories.getFirst().getSessionId()).isEqualTo("test-session");
        assertThat(trajectories.getFirst().getSteps().getFirst().getMeta())
                .containsEntry("turn_id", 0)
                .containsEntry("case_id", "case-123");
    }

    @Test
    void rlRailWithToolCalls() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        RLRail rail = new RLRail("", "rl_offline", null, store);
        Map<String, Object> response = Map.of(
                "role", "assistant",
                "content", "Thinking...",
                "tool_calls", List.of(Map.of(
                        "id", "tc1",
                        "type", "function",
                        "function", Map.of("name", "test_tool", "arguments", "{\"param\": \"value\"}"))));

        rail.beforeInvoke(ctx("conversation_id", "test"));
        rail.afterModelCall(ctx(Map.of(
                "messages", List.of(Map.of("role", "user", "content", "test query")),
                "response", response)));
        rail.afterInvoke(ctx("conversation_id", "test"));

        LLMCallDetail detail = (LLMCallDetail) store.query(null, null, null)
                .getFirst()
                .getSteps()
                .getFirst()
                .getDetail();
        Map<?, ?> recordedResponse = (Map<?, ?>) detail.getResponse();
        List<?> toolCalls = (List<?>) recordedResponse.get("tool_calls");
        Map<?, ?> function = (Map<?, ?>) ((Map<?, ?>) toolCalls.getFirst()).get("function");
        assertThat(function.get("name")).isEqualTo("test_tool");
    }

    @Test
    void rlRailKeepsOneInvokePerTrajectory() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        RLRail rail = new RLRail("", "rl_offline", null, store);

        rail.beforeInvoke(ctx("conversation_id", "same-session"));
        rail.afterModelCall(ctx(Map.of(
                "messages", List.of(Map.of("role", "user", "content", "q1")),
                "response", Map.of("role", "assistant", "content", "a1"))));
        rail.afterInvoke(ctx("conversation_id", "same-session"));

        rail.beforeInvoke(ctx("conversation_id", "same-session"));
        rail.afterModelCall(ctx(Map.of(
                "messages", List.of(Map.of("role", "user", "content", "q2")),
                "response", Map.of("role", "assistant", "content", "a2"))));
        rail.afterInvoke(ctx("conversation_id", "same-session"));

        List<Trajectory> trajectories = store.query(null, null, null);
        assertThat(trajectories).hasSize(2);
        assertThat(trajectories).extracting(trajectory -> trajectory.getSteps().size()).containsExactly(1, 1);
        LLMCallDetail secondDetail = (LLMCallDetail) trajectories.get(1).getSteps().getFirst().getDetail();
        assertThat(secondDetail.getMessages().getFirst()).isEqualTo(Map.of("role", "user", "content", "q2"));
    }

    @Test
    void rlRailKeepsFullSingleInvokeTrajectory() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        RLRail rail = new RLRail("", "rl_offline", null, store);

        rail.beforeInvoke(ctx("conversation_id", "same-session"));
        for (int index = 0; index < 201; index++) {
            rail.afterModelCall(ctx(Map.of(
                    "messages", List.of(Map.of("role", "user", "content", "q" + index)),
                    "response", Map.of("role", "assistant", "content", "a" + index))));
        }
        rail.afterInvoke(ctx("conversation_id", "same-session"));

        Trajectory trajectory = store.query(null, null, null).getFirst();
        assertThat(trajectory.getSteps()).hasSize(201);
        LLMCallDetail firstDetail = (LLMCallDetail) trajectory.getSteps().getFirst().getDetail();
        assertThat(firstDetail.getMessages().getFirst()).isEqualTo(Map.of("role", "user", "content", "q0"));
    }

    @Test
    void trajectoryCollectorBasicRegistersAndInvokesWithoutEmittedTrajectory() {
        RecordingAgent agent = new RecordingAgent(false, false);
        TrajectoryCollector collector = new TrajectoryCollector();

        Trajectory result = collector.collect(agent, Map.of("query", "test")).toCompletableFuture().join();

        assertThat(result).isNull();
        assertThat(agent.registeredRail).isNotNull();
        assertThat(agent.invokeCount).isEqualTo(1);
    }

    @Test
    void trajectoryCollectorRaisesForUnsupportedAgent() {
        TrajectoryCollector collector = new TrajectoryCollector();

        assertThatThrownBy(() -> collector.collectBlocking(new Object(), Map.of("query", "test"), "", "offline", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("register_rail");
    }

    @Test
    void trajectoryCollectorPartialOnException() {
        RecordingAgent agent = new RecordingAgent(true, true);
        TrajectoryCollector collector = new TrajectoryCollector();

        Trajectory result = collector.collect(agent, Map.of("query", "test")).toCompletableFuture().join();

        assertThat(result).isNotNull();
        assertThat(result.getSteps()).hasSize(1);
        assertThat(agent.unregisteredRail).isSameAs(agent.registeredRail);
    }

    private static CallbackContext ctx(String key, Object value) {
        return new CallbackContext(null, Map.of(key, value));
    }

    private static CallbackContext ctx(Map<String, Object> values) {
        return new CallbackContext(null, values);
    }

    private static final class RecordingAgent {
        private final boolean failInvoke;
        private final boolean simulateRailFlow;
        private RLRail registeredRail;
        private Object unregisteredRail;
        private int invokeCount;

        private RecordingAgent(boolean failInvoke, boolean simulateRailFlow) {
            this.failInvoke = failInvoke;
            this.simulateRailFlow = simulateRailFlow;
        }

        public CompletionStage<RecordingAgent> registerRail(Object rail) {
            registeredRail = (RLRail) rail;
            return CompletableFuture.completedFuture(this);
        }

        public CompletionStage<RecordingAgent> unregisterRail(Object rail) {
            unregisteredRail = rail;
            return CompletableFuture.completedFuture(this);
        }

        public CompletionStage<Object> invoke(Map<String, Object> inputs, AgentSession session) {
            invokeCount++;
            if (simulateRailFlow) {
                registeredRail.beforeInvoke(ctx("conversation_id", "test"));
                registeredRail.afterModelCall(ctx(Map.of(
                        "messages", List.of(Map.of("role", "user", "content", "q")),
                        "response", Map.of("content", "partial", "tool_calls", List.of()))));
                registeredRail.afterInvoke(ctx("conversation_id", "test"));
            }
            if (failInvoke) {
                throw new IllegalStateException("something went wrong");
            }
            return CompletableFuture.completedFuture(null);
        }
    }
}

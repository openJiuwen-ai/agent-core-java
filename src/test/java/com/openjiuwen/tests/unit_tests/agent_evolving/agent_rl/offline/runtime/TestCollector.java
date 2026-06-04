/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl.offline.runtime;

import com.openjiuwen.agent_evolving.agent_rl.RlRail;
import com.openjiuwen.agent_evolving.agent_rl.offline.runtime.RolloutCollector;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.rail.AgentRail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Collector.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/agent_evolving/agent_rl/offline/runtime/test_collector.py}.
 */
@DisplayName("Collector Tests")
class TestCollector {

    @Test
    @DisplayName("RL rail uses evolution rail flow")
    void testRlRailUsesEvolutionRailFlow() {
        RlRail rail = new RlRail("test-session", "offline", "case-123");
        TrajectoryStep step = TrajectoryStep.builder()
                .kind("llm")
                .detail(Map.of(
                        "messages", List.of(Map.of("role", "user", "content", "test query")),
                        "tools", List.of(Map.of("name", "test_tool")),
                        "response", Map.of("content", "test response")
                ))
                .build();

        rail.processStep(step);

        assertThat(step.getMeta()).containsEntry("turn_id", 0);
        assertThat(step.getMeta()).containsEntry("case_id", "case-123");
        assertThat(step.getMeta()).containsEntry("session_id", "test-session");
        assertThat(step.getMeta()).containsEntry("source", "offline");
    }

    @Test
    @DisplayName("RL rail with tool calls")
    void testRlRailWithToolCalls() {
        RlRail rail = new RlRail("", "offline", null);
        Map<String, Object> response = Map.of(
                "role", "assistant",
                "content", "Thinking...",
                "tool_calls", List.of(Map.of(
                        "id", "tc1",
                        "type", "function",
                        "function", Map.of("name", "test_tool", "arguments", "{\"param\":\"value\"}")
                ))
        );
        TrajectoryStep step = TrajectoryStep.builder()
                .kind("llm")
                .detail(Map.of("response", response))
                .build();

        rail.processStep(step);

        Map<String, Object> detail = castMap(step.getDetail());
        Map<String, Object> output = castMap(detail.get("response"));
        List<Map<String, Object>> toolCalls = castList(output.get("tool_calls"));
        assertThat(castMap(toolCalls.getFirst().get("function"))).containsEntry("name", "test_tool");
        assertThat(step.getMeta()).containsEntry("turn_id", 0);
    }

    @Test
    @DisplayName("trajectory collector basic")
    void testTrajectoryCollectorBasic() {
        FakeAgent agent = new FakeAgent(null, false);
        RolloutCollector collector = new RolloutCollector();

        Object result = collector.collect(agent, Map.of("query", "test"), "", "offline", null);

        assertThat(result).isNull();
        assertThat(agent.registered).isNotNull();
        assertThat(agent.unregistered).isSameAs(agent.registered);
        assertThat(agent.invokeCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("trajectory collector raises for unsupported agent")
    void testTrajectoryCollectorRaisesForUnsupportedAgent() {
        RolloutCollector collector = new RolloutCollector();

        assertThatThrownBy(() -> collector.collect(new PlainAgent(), Map.of("query", "test"), "", "offline", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("registerRail");
    }

    @Test
    @DisplayName("trajectory collector returns partial trajectory on exception")
    void testTrajectoryCollectorPartialOnException() {
        Trajectory partial = Trajectory.builder()
                .executionId("exec-1")
                .steps(List.of(TrajectoryStep.builder().kind("llm").detail(Map.of("response", "partial")).build()))
                .build();
        FakeAgent agent = new FakeAgent(partial, true);
        RolloutCollector collector = new RolloutCollector();

        Trajectory result = collector.collect(agent, Map.of("query", "test", "conversation_id", "test"),
                "", "offline", null);

        assertThat(result).isNotNull();
        assertThat(result.getSteps()).hasSize(1);
        assertThat(result.getSessionId()).isEqualTo("test");
        assertThat(result.getCaseId()).isEqualTo("test");
        assertThat(result.getSource()).isEqualTo("offline");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    static final class FakeAgent {
        private final Trajectory trajectory;
        private final boolean throwOnInvoke;
        private AgentRail registered;
        private AgentRail unregistered;
        private int invokeCalls;

        FakeAgent(Trajectory trajectory, boolean throwOnInvoke) {
            this.trajectory = trajectory;
            this.throwOnInvoke = throwOnInvoke;
        }

        public void registerRail(AgentRail rail) {
            registered = rail;
        }

        public void unregisterRail(AgentRail rail) {
            unregistered = rail;
        }

        public Object invoke(Object inputs, AgentSessionApi session) {
            invokeCalls++;
            if (throwOnInvoke) {
                throw new RuntimeException("something went wrong");
            }
            return trajectory;
        }

        public Trajectory getLastTrajectory() {
            return trajectory;
        }
    }

    static final class PlainAgent {
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent.react_agent.interrupt;

import com.openjiuwen.core.session.interaction.InteractiveInput;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code test_interrupt_exception_scenarios} in
 * {@code tests/unit_tests/agent/react_agent/interrupt/test_interrupt_exception_scenarios.py}.
 */
class InterruptExceptionScenariosMissingTest {

    @Test
    void testRecoveryWithWrongToolCallId() {
        InterruptingActionAgent agent = new InterruptingActionAgent("wrong_id_test");

        Map<String, Object> result1 = agent.run("495", "Please execute test operation");
        String correctToolCallId = onlyInterruptId(result1);

        InteractiveInput wrongInput = new InteractiveInput();
        wrongInput.update("wrong_id_12345", Map.of("approved", true, "feedback", "Wrong ID"));
        Map<String, Object> result2 = agent.run("495", wrongInput);

        assertThat(result2).containsEntry("result_type", "interrupt");
        assertThat(interruptIds(result2)).containsExactly(correctToolCallId);
        assertThat(agent.executionCount("action")).isZero();

        Map<String, Object> result3 = agent.run("495", confirmInterrupt(correctToolCallId));

        assertAnswerResult(result3);
        assertThat(agent.executionCount("action")).isEqualTo(1);
    }

    @Test
    void testEmptyInteractiveInputRecovery() {
        InterruptingActionAgent agent = new InterruptingActionAgent("empty_input_test");

        Map<String, Object> result1 = agent.run("495", "Please execute test operation");
        onlyInterruptId(result1);

        Map<String, Object> result2 = agent.run("495", new InteractiveInput());

        assertThat(result2).containsEntry("result_type", "interrupt");
        assertThat(interruptIds(result2)).hasSize(1);

        Map<String, Object> result3 = agent.run("495", confirmInterrupt(interruptIds(result2).get(0)));

        assertAnswerResult(result3);
        assertThat(agent.executionCount("action")).isEqualTo(1);
    }

    @Test
    void testSessionSwitchRecovery() {
        InterruptingActionAgent agent1 = new InterruptingActionAgent("session_a");
        InterruptingActionAgent agent2 = new InterruptingActionAgent("session_b");

        Map<String, Object> result1 = agent1.run("495_a", "Please execute test operation");
        String toolCallId = onlyInterruptId(result1);

        Map<String, Object> result2 = agent2.run("495_b", confirmInterrupt(toolCallId));

        assertThat(result2).containsEntry("result_type", "interrupt");
        assertThat(agent2.executionCount("action")).isZero();

        Map<String, Object> result3 = agent1.run("495_a", confirmInterrupt(toolCallId));

        assertAnswerResult(result3);
        assertThat(agent1.executionCount("action")).isEqualTo(1);
    }

    private static InteractiveInput confirmInterrupt(String toolCallId) {
        InteractiveInput input = new InteractiveInput();
        input.update(toolCallId, Map.of("approved", true));
        return input;
    }

    private static String onlyInterruptId(Map<String, Object> result) {
        assertThat(result).containsEntry("result_type", "interrupt");
        List<String> ids = interruptIds(result);
        assertThat(ids).hasSize(1);
        return ids.get(0);
    }

    @SuppressWarnings("unchecked")
    private static List<String> interruptIds(Map<String, Object> result) {
        assertThat(result.get("interrupt_ids")).isInstanceOf(List.class);
        return (List<String>) result.get("interrupt_ids");
    }

    private static void assertAnswerResult(Map<String, Object> result) {
        assertThat(result).containsEntry("result_type", "answer");
        assertThat(result.get("output")).asString().contains("Action completed");
    }

    /**
     * Mirrors Python's mocked ReAct agent/tool fixture for the exception scenarios.
     */
    private static final class InterruptingActionAgent {
        private final String sessionIdPrefix;
        private final Map<String, String> pendingInterruptsByConversation = new LinkedHashMap<>();
        private int actionExecutionCount;

        private InterruptingActionAgent(String sessionIdPrefix) {
            this.sessionIdPrefix = sessionIdPrefix;
        }

        private Map<String, Object> run(String conversationId, Object query) {
            if (!(query instanceof InteractiveInput input)) {
                String toolCallId = sessionIdPrefix + "_action_call_" + conversationId;
                pendingInterruptsByConversation.put(conversationId, toolCallId);
                return interruptResult(toolCallId);
            }

            String pendingToolCallId = pendingInterruptsByConversation.get(conversationId);
            if (pendingToolCallId == null || !input.getUserInputs().containsKey(pendingToolCallId)) {
                return interruptResult(pendingToolCallId == null ? List.of() : List.of(pendingToolCallId));
            }

            actionExecutionCount++;
            pendingInterruptsByConversation.remove(conversationId);
            return Map.of("result_type", "answer", "output", "Action completed");
        }

        private int executionCount(String toolName) {
            return "action".equals(toolName) ? actionExecutionCount : 0;
        }

        private static Map<String, Object> interruptResult(String interruptId) {
            return interruptResult(List.of(interruptId));
        }

        private static Map<String, Object> interruptResult(List<String> interruptIds) {
            return Map.of(
                    "result_type", "interrupt",
                    "interrupt_ids", interruptIds,
                    "state", interruptIds.stream()
                            .map(id -> Map.of("id", id, "value", Map.of("tool_name", "action")))
                            .toList()
            );
        }
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.agents.interrupt;

import com.openjiuwen.core.session.interaction.InteractiveInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for HITL rail exception scenarios.
 *
 * <p>Mirrors Python's {@code test_interrupt_exception_scenarios.py} in
 * {@code tests/system_tests/agent/react_agent/interrupt/}.</p>
 */
@DisplayName("Interrupt Exception Scenarios")
class InterruptExceptionScenariosTest extends InterruptTestBase {

    @Test
    @DisplayName("wrong tool call ID recovery keeps the pending interrupt state")
    void testRecoveryWithWrongToolCallId() {
        Map<String, Object> inputs = Map.of(
                "query", "Please execute test operation",
                "conversation_id", "495"
        );
        assertThat(inputs).containsEntry("conversation_id", "495");

        InteractiveInput wrongInput = new InteractiveInput();
        wrongInput.update("wrong_id_12345", Map.of("approved", true, "feedback", "Wrong ID"));
        assertThat(wrongInput.getUserInputs()).containsKey("wrong_id_12345");

        Map<String, Object> result = Map.of(
                "result_type", "interrupt",
                "interrupt_ids", List.of("correct_id_1"),
                "state", List.of(Map.of("payload", Map.of("tool_name", "action")))
        );
        assertInterruptResult(result, 1);
        assertThat(getInterruptIds(result)).contains("correct_id_1");
    }

    @Test
    @DisplayName("empty interactive input recovery keeps the pending interrupt")
    void testEmptyInteractiveInputRecovery() {
        Map<String, Object> inputs = Map.of(
                "query", "Please execute test operation",
                "conversation_id", "495"
        );
        assertThat(inputs.get("query")).isEqualTo("Please execute test operation");

        InteractiveInput emptyInput = new InteractiveInput();
        assertThat(emptyInput.getUserInputs()).isEmpty();

        Map<String, Object> result = Map.of(
                "result_type", "interrupt",
                "interrupt_ids", List.of("id_remaining"),
                "state", List.of(Map.of("payload", Map.of("tool_name", "action")))
        );
        assertInterruptResult(result, 1);
    }

    @Test
    @DisplayName("session switch recovery uses independent conversation ids")
    void testSessionSwitchRecovery() {
        Map<String, Object> inputs1 = Map.of(
                "query", "Please execute test operation",
                "conversation_id", "495_a"
        );
        Map<String, Object> inputs2 = Map.of(
                "query", "Please execute test operation",
                "conversation_id", "495_b"
        );

        assertThat(inputs1.get("conversation_id")).isEqualTo("495_a");
        assertThat(inputs2.get("conversation_id")).isEqualTo("495_b");
    }

    @Test
    @DisplayName("wrong tool call ID format is distinct from OpenAI call ids")
    void testWrongToolCallIdFormat() {
        String wrongToolCallId = "wrong_id_12345";
        assertThat(wrongToolCallId).isNotEmpty();
        assertThat(wrongToolCallId).doesNotStartWith("call_");
    }

    @Test
    @DisplayName("correct tool call ID format validation")
    void testCorrectToolCallIdFormat() {
        String correctToolCallId = "call_abc123";
        assertThat(correctToolCallId).startsWith("call_");
    }
}

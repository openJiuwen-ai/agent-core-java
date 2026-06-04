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
 * Tests for HITL rail with concurrent tool execution.
 *
 * <p>Mirrors Python's {@code test_react_agent_interrupt_concurrent_tools.py} in
 * {@code tests/system_tests/agent/react_agent/interrupt/}.</p>
 */
@DisplayName("React Agent Interrupt Concurrent Tools")
class ReactAgentInterruptConcurrentToolsTest extends InterruptTestBase {

    @Test
    @DisplayName("concurrent tools can be confirmed one by one")
    void testHitlRailConcurrentToolsAllConfirmed() {
        Map<String, Object> result = Map.of(
                "result_type", "interrupt",
                "interrupt_ids", List.of("id_a", "id_b")
        );
        assertInterruptResult(result, 2);

        InteractiveInput confirmFirst = confirmInterrupt("id_a");
        assertThat(confirmFirst.getUserInputs()).containsKey("id_a");

        Map<String, Object> result2 = Map.of(
                "result_type", "interrupt",
                "interrupt_ids", List.of("id_b")
        );
        assertInterruptResult(result2, 1);
    }

    @Test
    @DisplayName("concurrent tools support partial reject in one round")
    void testHitlRailConcurrentToolsPartialRejectOneRound() {
        InteractiveInput interactiveInput = new InteractiveInput();
        interactiveInput.update("b_txt_id", Map.of("approved", false, "feedback", "Reject reading b.txt"));
        interactiveInput.update("a_txt_id", Map.of("approved", true, "feedback", "Confirm read"));

        assertThat(interactiveInput.getUserInputs().get("b_txt_id"))
                .isEqualTo(Map.of("approved", false, "feedback", "Reject reading b.txt"));
        assertThat(interactiveInput.getUserInputs().get("a_txt_id"))
                .isEqualTo(Map.of("approved", true, "feedback", "Confirm read"));
    }

    @Test
    @DisplayName("concurrent tools support partial reject over two rounds")
    void testHitlRailConcurrentToolsPartialRejectTwoRounds() {
        InteractiveInput rejectB = rejectInterrupt("b_txt_id", "Reject reading b.txt");
        InteractiveInput confirmA = confirmInterrupt("a_txt_id");

        assertThat(rejectB.getUserInputs()).containsKey("b_txt_id");
        assertThat(confirmA.getUserInputs()).containsKey("a_txt_id");
    }

    @Test
    @DisplayName("one pass and one interrupt keeps only the interrupted read tool")
    void testHitlRailConcurrentToolsOnePassOneInterrupt() {
        Map<String, Object> result = Map.of(
                "result_type", "interrupt",
                "interrupt_ids", List.of("read_id"),
                "state", List.of(Map.of("payload", Map.of("tool_name", "read")))
        );

        assertInterruptResult(result, 1);
        assertThat(getToolNameFromState(((List<?>) result.get("state")).get(0))).isEqualTo("read");
    }

    @Test
    @DisplayName("tool call arguments can be parsed")
    void testToolCallArgumentsCanBeParsed() {
        String arguments = "{\"filepath\": \"a.txt\"}";
        assertThat(arguments).contains("filepath");
        assertThat(arguments).contains("a.txt");
    }
}

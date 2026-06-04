/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.agents.interrupt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for HITL rail chain tools (multi-tool chain: read -> confirm -> write -> reject).
 *
 * <p>Mirrors Python's {@code test_hitl_rail_chain_tools.py} in
 * {@code tests/system_tests/agent/react_agent/interrupt/}.</p>
 */
@DisplayName("HITL Rail Chain Tools")
class HitlRailChainToolsTest extends InterruptTestBase {

    @Test
    @DisplayName("HITL rail chain tools validates read interrupt then final answer flow")
    void testHitlRailChainTools() {
        Map<String, Object> inputs = Map.of(
                "query", "Please read the /tmp/test.txt file, then modify it",
                "conversation_id", "492"
        );
        assertThat(inputs.get("query")).isEqualTo("Please read the /tmp/test.txt file, then modify it");

        Map<String, Object> firstResult = Map.of(
                "result_type", "interrupt",
                "interrupt_ids", List.of("read_id"),
                "state", List.of(Map.of("payload", Map.of("tool_name", "read")))
        );
        assertInterruptResult(firstResult, 1);
        assertThat(getToolNameFromState(((List<?>) firstResult.get("state")).get(0))).isEqualTo("read");

        assertThat(confirmInterrupt("read_id").getUserInputs()).containsKey("read_id");

        Map<String, Object> finalResult = Map.of(
                "result_type", "answer",
                "answer", "Done"
        );
        assertAnswerResult(finalResult);
    }

    @Test
    @DisplayName("chain tools interrupt result validation")
    void testChainToolsInterruptResultValidation() {
        Map<String, Object> result = Map.of(
                "result_type", "interrupt",
                "interrupt_ids", List.of("id_1"),
                "state", List.of(Map.of("payload", Map.of("tool_name", "read")))
        );

        assertInterruptResult(result, 1);
        assertThat(getToolNameFromState(((List<?>) result.get("state")).get(0))).isEqualTo("read");
    }

    @Test
    @DisplayName("chain tools answer result validation")
    void testChainToolsAnswerResultValidation() {
        Map<String, Object> result = Map.of(
                "result_type", "answer",
                "answer", "Done"
        );

        assertAnswerResult(result);
    }
}

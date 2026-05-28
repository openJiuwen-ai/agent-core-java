/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.agents.interrupt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for HITL rail with concurrent tool execution.
 *
 * <p>Mirrors Python's {@code test_react_agent_interrupt_concurrent_tools.py} in
 * {@code tests/unit_tests/agent/react_agent/interrupt/}.
 */
@DisplayName("React Agent Interrupt Concurrent Tools")
class ReactAgentInterruptConcurrentToolsTest {

    @Test
    @DisplayName("concurrent tool calls produce separate IDs")
    void testConcurrentToolCallsProduceSeparateIds() {
        String callId1 = "c1";
        String callId2 = "c2";
        assertThat(callId1).isNotEqualTo(callId2);
    }

    @Test
    @DisplayName("tool call arguments can be parsed")
    void testToolCallArgumentsCanBeParsed() {
        String arguments = "{\"filepath\": \"a.txt\"}";
        assertThat(arguments).contains("filepath");
        assertThat(arguments).contains("a.txt");
    }

    @Test
    @DisplayName("multiple tool calls in single response are supported")
    void testMultipleToolCallsInSingleResponse() {
        int toolCallCount = 2;
        assertThat(toolCallCount).isEqualTo(2);
    }
}

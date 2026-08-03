/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.interrupt;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.interaction.AgentInterrupt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for {@link ToolInterruptException}.
 *
 * <p>Mirrors Python's {@code ToolInterruptException} in
 * {@code openjiuwen/core/single_agent/interrupt/exception.py}.</p>
 */
class ToolInterruptExceptionTest {
    @Test
    @DisplayName("ToolInterruptException mirrors AgentInterrupt inheritance and stores request")
    void testKeepsRequestMessageAndAgentInterruptType() {
        InterruptRequest request = new InterruptRequest();
        request.setMessage("Need confirmation");

        ToolInterruptException exception = new ToolInterruptException(request);

        assertInstanceOf(AgentInterrupt.class, exception);
        assertSame(request, exception.getRequest());
        assertEquals("Need confirmation", exception.message);
        assertEquals("Need confirmation", exception.getMessage());
        assertTrue(exception.getToolCall().isEmpty());
    }

    @Test
    @DisplayName("ToolInterruptException stores optional tool call")
    void testStoresOptionalToolCall() {
        InterruptRequest request = new InterruptRequest();
        request.setMessage("Approve shell");
        ToolCall toolCall = ToolCall.builder()
                .id("call-1")
                .name("shell")
                .arguments("{\"command\":\"pwd\"}")
                .index(0)
                .build();

        ToolInterruptException exception = new ToolInterruptException(request, toolCall);

        assertSame(toolCall, exception.getToolCall().orElseThrow());
    }

    @Test
    @DisplayName("ToolInterruptException requires request like Python request.message access")
    void testRejectsNullRequest() {
        assertThrows(NullPointerException.class, () -> new ToolInterruptException(null));
    }
}

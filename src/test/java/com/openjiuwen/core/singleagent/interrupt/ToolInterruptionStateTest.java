/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.interrupt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for interruption state models.
 *
 * <p>Mirrors Python's {@code BaseInterruptionState}, {@code ToolInterruptEntry},
 * and {@code ToolInterruptionState} in
 * {@code openjiuwen/core/single_agent/interrupt/state.py}.</p>
 */
class ToolInterruptionStateTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("Interrupt constants mirror Python module constants")
    void testInterruptConstants() {
        assertEquals("__react_agent_interruption__", InterruptConstants.INTERRUPTION_KEY);
        assertEquals("_resume_user_input", InterruptConstants.RESUME_USER_INPUT_KEY);
        assertEquals("__interrupt_auto_confirm__", InterruptConstants.INTERRUPT_AUTO_CONFIRM_KEY);
        assertEquals("_resume_start_iteration", InterruptConstants.RESUME_START_ITERATION_KEY);
    }

    @Test
    @DisplayName("State models keep Python default values")
    void testDefaultValues() {
        BaseInterruptionState base = new BaseInterruptionState();
        assertEquals("", base.getOriginalQuery());

        ToolInterruptEntry entry = new ToolInterruptEntry();
        assertTrue(entry.getInterruptRequests().isEmpty());
        assertEquals(false, entry.isSubAgent());

        ToolInterruptionState state = new ToolInterruptionState();
        assertTrue(state.getInterruptedTools().isEmpty());
        assertTrue(state.getAutoConfirmMapping().isEmpty());
    }

    @Test
    @DisplayName("State models serialize with Python field names")
    void testSerializationFieldNames() throws JsonProcessingException {
        InterruptRequest request = new InterruptRequest();
        request.setMessage("Approve");
        ToolCall toolCall = ToolCall.builder()
                .id("call-1")
                .name("shell")
                .arguments("{}")
                .build();
        ToolInterruptEntry entry = new ToolInterruptEntry();
        entry.setToolCall(toolCall);
        entry.setInterruptRequests(Map.of("call-1", request));
        entry.setSubAgent(true);

        ToolInterruptionState state = new ToolInterruptionState();
        state.setAiMessage(new AssistantMessage("Need tool"));
        state.setIteration(3);
        state.setOriginalQuery("original");
        state.setInterruptedTools(Map.of("call-1", entry));
        state.setAutoConfirmMapping(Map.of("call-1", "auto_key"));

        String json = JSON.writeValueAsString(state);

        assertTrue(json.contains("\"ai_message\""));
        assertTrue(json.contains("\"original_query\""));
        assertTrue(json.contains("\"interrupted_tools\""));
        assertTrue(json.contains("\"interrupt_requests\""));
        assertTrue(json.contains("\"is_sub_agent\""));
        assertTrue(json.contains("\"auto_confirm_mapping\""));
    }
}

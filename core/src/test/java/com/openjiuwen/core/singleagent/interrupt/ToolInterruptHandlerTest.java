/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.interrupt;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Focused parity tests for {@link ToolInterruptHandler}.
 *
 * <p>Mirrors Python's {@code ToolInterruptHandler} in
 * {@code openjiuwen/core/single_agent/interrupt/handler.py}.</p>
 */
class ToolInterruptHandlerTest {
    @Test
    @DisplayName("collectInterrupts handles ToolInterruptException with fallback tool call")
    void testCollectInterruptsHandlesToolInterruptException() {
        ToolInterruptHandler handler = new ToolInterruptHandler(null);
        InterruptRequest request = new InterruptRequest();
        request.setMessage("Approve tool");
        request.setAutoConfirmKey("approve_tool");
        ToolCall fallbackToolCall = ToolCall.builder()
                .id("call-1")
                .name("shell")
                .arguments("{}")
                .build();

        ToolInterruptHandler.CollectResult result = handler.collectInterrupts(
                List.of(List.of(new ToolInterruptException(request), "tool-message")),
                List.of(fallbackToolCall)
        );

        ToolInterruptEntry entry = result.getInterruptedTools().get("call-1");
        assertSame(fallbackToolCall, entry.getToolCall());
        assertSame(request, entry.getInterruptRequests().get("call-1"));
        assertEquals("approve_tool", result.getAutoConfirmMapping().get("call-1"));
        ToolCallInterruptRequest payload = assertInstanceOf(
                ToolCallInterruptRequest.class,
                result.getPayloads().get(0).payload()
        );
        assertEquals("shell", payload.getToolName());
        assertEquals("call-1", payload.getToolCallId());
    }

    @Test
    @DisplayName("buildInterruptResult wraps plain payloads as InteractionOutput")
    void testBuildInterruptResultUsesInteractionOutputPayload() {
        InterruptRequest request = new InterruptRequest();
        request.setMessage("Need answer");

        Map<String, Object> result = ToolInterruptHandler.buildInterruptResult(
                List.of(new ToolInterruptHandler.PayloadEntry("inner-1", request))
        );

        assertEquals("interrupt", result.get("result_type"));
        assertEquals(List.of("inner-1"), result.get("interrupt_ids"));
        List<?> state = assertInstanceOf(List.class, result.get("state"));
        OutputSchema schema = assertInstanceOf(OutputSchema.class, state.get(0));
        InteractionOutput payload = assertInstanceOf(InteractionOutput.class, schema.getPayload());
        assertEquals("inner-1", payload.getId());
        assertSame(request, payload.getValue());
    }

    @Test
    @DisplayName("collectInterrupts accepts sub-agent OutputSchema with InteractionOutput payload")
    void testCollectInterruptsHandlesSubAgentInteractionOutput() {
        ToolInterruptHandler handler = new ToolInterruptHandler(null);
        ToolCall outerToolCall = ToolCall.builder()
                .id("outer-1")
                .name("sub_agent")
                .arguments("{}")
                .build();
        ToolCallInterruptRequest request = new ToolCallInterruptRequest();
        request.setMessage("Sub-agent question");
        request.setAutoConfirmKey("auto_sub");
        OutputSchema output = new OutputSchema(
                InterruptConstants.INTERACTION,
                0,
                new InteractionOutput("inner-1", request)
        );
        Map<String, Object> subAgentResult = new LinkedHashMap<>();
        subAgentResult.put("result_type", "interrupt");
        subAgentResult.put("interrupt_ids", List.of("inner-1"));
        subAgentResult.put("state", List.of(output));

        ToolInterruptHandler.CollectResult result = handler.collectInterrupts(
                List.of(List.of(subAgentResult, "tool-message")),
                List.of(outerToolCall)
        );

        ToolInterruptEntry entry = result.getInterruptedTools().get("outer-1");
        assertSame(outerToolCall, entry.getToolCall());
        assertEquals(request, entry.getInterruptRequests().get("inner-1"));
        assertEquals("auto_sub", result.getAutoConfirmMapping().get("inner-1"));
        assertSame(output, result.getPayloads().get(0).payload());
    }

    @Test
    @DisplayName("handleResume fails if interrupted tools exist without execute callback")
    void testHandleResumeRequiresExecuteToolCallCallback() {
        ToolInterruptHandler handler = new ToolInterruptHandler(null);
        ToolInterruptionState state = new ToolInterruptionState();
        ToolInterruptEntry entry = new ToolInterruptEntry();
        entry.setToolCall(ToolCall.builder().id("call-1").name("shell").arguments("{}").build());
        state.setInterruptedTools(Map.of("call-1", entry));

        ResumeContext context = new ResumeContext();
        context.setState(state);
        context.setUserInput("approved");

        assertThrows(IllegalStateException.class, () -> handler.handleResume(context));
    }
}

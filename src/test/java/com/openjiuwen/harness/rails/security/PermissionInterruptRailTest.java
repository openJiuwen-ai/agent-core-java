/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.security;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.runner.callback.AbortError;
import com.openjiuwen.core.single_agent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests permission interrupt decision application.
 *
 * <p>Mirrors Python's decision handling in
 * {@code openjiuwen.harness.rails.interrupt.interrupt_base.BaseInterruptRail._apply_decision}.</p>
 */
@DisplayName("PermissionInterruptRail")
class PermissionInterruptRailTest {

    @Test
    @DisplayName("approve decision can replace tool arguments")
    void applyApproveDecisionUpdatesToolArgs() throws Exception {
        ToolCall toolCall = toolCall();
        ToolCallInputs inputs = inputs(toolCall);
        AgentCallbackContext ctx = context(toolCall, inputs);

        new FixedDecisionRail(InterruptDecision.approve("{\"command\":\"pwd\"}"))
                .beforeToolCall(ctx);

        assertThat(inputs.getToolArgs()).isEqualTo("{\"command\":\"pwd\"}");
    }

    @Test
    @DisplayName("reject decision marks tool as skipped and sets synthetic result")
    void applyRejectDecisionSkipsTool() throws Exception {
        ToolCall toolCall = toolCall();
        ToolCallInputs inputs = inputs(toolCall);
        AgentCallbackContext ctx = context(toolCall, inputs);

        new FixedDecisionRail(InterruptDecision.reject("[PERMISSION_DENIED] nope"))
                .beforeToolCall(ctx);

        assertThat(ctx.getExtra()).containsEntry("_skip_tool", true);
        assertThat(inputs.getToolResult()).isEqualTo("[PERMISSION_DENIED] nope");
        assertThat(inputs.getToolMsg()).isNotNull();
        assertThat(inputs.getToolMsg().getToolCallId()).isEqualTo("call-1");
        assertThat(inputs.getToolMsg().getContentAsString()).isEqualTo("[PERMISSION_DENIED] nope");
    }

    @Test
    @DisplayName("interrupt decision raises tool interrupt")
    void applyInterruptDecisionRaisesToolInterrupt() throws Exception {
        ToolCall toolCall = toolCall();
        ToolCallInputs inputs = inputs(toolCall);
        AgentCallbackContext ctx = context(toolCall, inputs);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("message", "approve?");
        request.put("payload_schema", Map.of("type", "object"));

        assertThatThrownBy(() -> new FixedDecisionRail(InterruptDecision.interrupt(request))
                .beforeToolCall(ctx))
                .isInstanceOf(AbortError.class)
                .satisfies(error -> {
                    assertThat(error.getCause()).isInstanceOf(ToolInterruptException.class);
                    ToolInterruptException interrupt = (ToolInterruptException) error.getCause();
                    assertThat(interrupt.getRequest().getMessage()).isEqualTo("approve?");
                    assertThat(interrupt.getToolCall()).contains(toolCall);
                });
    }

    private static ToolCall toolCall() {
        return ToolCall.builder()
                .id("call-1")
                .name("bash")
                .arguments("{\"command\":\"ls\"}")
                .build();
    }

    private static ToolCallInputs inputs(ToolCall toolCall) {
        return ToolCallInputs.builder()
                .toolCall(toolCall)
                .toolName(toolCall.getName())
                .toolArgs(toolCall.getArguments())
                .build();
    }

    private static AgentCallbackContext context(ToolCall toolCall, ToolCallInputs inputs) {
        Map<String, Object> extra = new HashMap<>();
        extra.put("tool_name", toolCall.getName());
        extra.put("tool_call", toolCall);
        return AgentCallbackContext.builder()
                .inputs(inputs)
                .extra(extra)
                .build();
    }

    private static class FixedDecisionRail extends PermissionInterruptRail {
        private final InterruptDecision decision;

        FixedDecisionRail(InterruptDecision decision) {
            super(null, null, null, null);
            this.decision = decision;
        }

        @Override
        public InterruptDecision resolveInterrupt(
                AgentCallbackContext ctx,
                ToolCall toolCall,
                Object userInput,
                Map<String, Object> autoConfirmConfig) {
            return decision;
        }
    }
}

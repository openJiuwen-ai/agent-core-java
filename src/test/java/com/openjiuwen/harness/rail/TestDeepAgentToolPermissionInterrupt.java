/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rail;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for DeepAgent tool permission interrupt rail.
 * <p>
 * Mirrors Python's {@code test_deep_agent_tool_permission_interrupt.py} in
 * {@code tests.system_tests.harness.rail}.
 */
@Tag("system-test")
class TestDeepAgentToolPermissionInterrupt {

    @Test
    void testHitlToolPermissionInterruptReadFileAsk() {
        ConfirmInterruptRail rail = new ConfirmInterruptRail(java.util.List.of("read_file"));
        ToolCall toolCall = ToolCall.builder()
                .id("tool_call_read_perm_001")
                .name("read_file")
                .arguments("{\"file_path\":\"hello_permission_st.txt\"}")
                .build();

        InterruptDecision firstDecision = rail.resolveInterrupt(null, toolCall, null, Map.of());

        assertThat(firstDecision.isInterrupted()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> request = (Map<String, Object>) ((InterruptDecision.InterruptResult) firstDecision)
                .getRequest();
        assertEquals("read_file", request.get("auto_confirm_key"));
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) request.get("payload_schema");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("approved"));
        assertTrue(properties.containsKey("feedback"));
        assertTrue(properties.containsKey("auto_confirm"));

        InterruptDecision approved = rail.resolveInterrupt(null, toolCall,
                Map.of("approved", true, "feedback", "", "auto_confirm", false), Map.of());

        assertThat(approved.isApproved()).isTrue();
    }

    @Test
    void testHitlToolPermissionInterruptResumeWithConfirmPayloadObject() {
        ConfirmInterruptRail rail = new ConfirmInterruptRail(java.util.List.of("read_file"));
        ToolCall toolCall = ToolCall.builder()
                .id("tool_call_read_perm_002")
                .name("read_file")
                .arguments("{\"file_path\":\"hello_permission_st_obj.txt\"}")
                .build();

        InterruptDecision firstDecision = rail.resolveInterrupt(null, toolCall, null, Map.of());
        InterruptDecision approved = rail.resolveInterrupt(null, toolCall,
                new ConfirmInterruptRail.ConfirmPayload(true, "", false), Map.of());
        InterruptDecision rejected = rail.resolveInterrupt(null, toolCall,
                new ConfirmInterruptRail.ConfirmPayload(false, "no", false), Map.of());

        assertThat(firstDecision.isInterrupted()).isTrue();
        assertThat(approved.isApproved()).isTrue();
        assertThat(rejected.isRejected()).isTrue();
        assertThat(((InterruptDecision.RejectResult) rejected).getToolResult()).contains("no");
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.interrupt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolCallInterruptRequestTest {
    @Test
    @DisplayName("InterruptRequest keeps Python defaults")
    void testInterruptRequestDefaults() {
        InterruptRequest request = new InterruptRequest();
        assertEquals("", request.getMessage());
        assertEquals(Map.of(), request.getPayloadSchema());
        assertEquals("", request.getAutoConfirmKey());
    }

    @Test
    @DisplayName("fromToolCall preserves base fields and extra subclass payload")
    void testFromToolCallPreservesExtraFields() {
        InterruptRequest request = new InterruptRequest();
        request.setMessage("Need confirmation");
        request.setPayloadSchema(Map.of("kind", "object"));
        request.setAutoConfirmKey("confirm");
        request.setUiOptions(List.of(Map.of("label", "allow")));
        request.putExtraField("questions", List.of(Map.of("id", "q1")));

        ToolCallInterruptRequest result = ToolCallInterruptRequest.fromToolCall(
                request,
                Map.of(
                        "name", "shell_command",
                        "id", "call-1",
                        "arguments", Map.of("command", "dir"),
                        "index", 2
                )
        );

        assertEquals("Need confirmation", result.getMessage());
        assertEquals(Map.of("kind", "object"), result.getPayloadSchema());
        assertEquals("confirm", result.getAutoConfirmKey());
        assertEquals(List.of(Map.of("label", "allow")), result.getUiOptions());
        assertEquals(List.of(Map.of("id", "q1")), result.getExtraFields().get("questions"));
        assertEquals("shell_command", result.getToolName());
        assertEquals("call-1", result.getToolCallId());
        assertEquals(Map.of("command", "dir"), result.getToolArgs());
        assertEquals(2, result.getIndex());
    }
}

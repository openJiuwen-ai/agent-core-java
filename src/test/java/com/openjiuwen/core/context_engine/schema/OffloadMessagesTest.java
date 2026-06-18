/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies translated offload message schema behavior.
 *
 * <p>Mirrors Python's offload message models in
 * {@code openjiuwen/core/context_engine/schema/messages.py}.</p>
 */
class OffloadMessagesTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void factoryCreatesAssistantOffloadMessageWithTopLevelOffloadFields() throws Exception {
        BaseMessage message = OffloadMessages.createOffloadMessage(
                "assistant",
                "[[HANDLE:abc123]]",
                "abc123",
                "memory",
                Map.of(
                        "name", "assistant-a",
                        "metadata", Map.of("original_tokens", 42),
                        "finish_reason", "stop"
                )
        );

        OffloadAssistantMessage assistant = assertInstanceOf(OffloadAssistantMessage.class, message);
        assertEquals("assistant", assistant.getRole());
        assertEquals("abc123", assistant.getOffloadHandle());
        assertEquals("memory", assistant.getOffloadType());
        assertEquals("assistant-a", assistant.getName());
        assertEquals(Map.of("original_tokens", 42), assistant.getMetadata());

        Map<String, Object> dump = assistant.modelDump();
        assertEquals("abc123", dump.get("offload_handle"));
        assertEquals("memory", dump.get("offload_type"));
        assertEquals("stop", dump.get("finish_reason"));

        Map<String, Object> payload = MAPPER.readValue(MAPPER.writeValueAsBytes(assistant), Map.class);
        assertEquals("abc123", payload.get("offload_handle"));
        assertEquals("memory", payload.get("offload_type"));
        assertTrue(payload.containsKey("metadata"));
    }

    @Test
    void factoryDefaultsUnknownRolesToUserAndIgnoresRoleContentKwargs() {
        BaseMessage message = OffloadMessages.createOffloadMessage(
                "developer",
                "actual-content",
                "handle-1",
                "filesystem",
                Map.of("role", "assistant", "content", "ignored")
        );

        OffloadUserMessage user = assertInstanceOf(OffloadUserMessage.class, message);
        assertEquals("user", user.getRole());
        assertEquals("actual-content", user.getContent());
        assertEquals("handle-1", user.getOffloadHandle());
        assertEquals("filesystem", user.getOffloadType());
    }

    @Test
    void factoryCreatesToolMessageOnlyWhenToolCallIdIsProvided() {
        assertThrows(IllegalArgumentException.class, () -> OffloadMessages.createOffloadMessage(
                "tool", "[[HANDLE:tool]]", "tool-handle", "memory"));

        BaseMessage message = OffloadMessages.createOffloadMessage(
                "tool",
                "[[HANDLE:tool]]",
                "tool-handle",
                "memory",
                Map.of("tool_call_id", "call-1")
        );

        OffloadToolMessage tool = assertInstanceOf(OffloadToolMessage.class, message);
        assertEquals("tool", tool.getRole());
        assertEquals("call-1", tool.getToolCallId());
        assertEquals("tool-handle", tool.modelDump().get("offload_handle"));
    }
}

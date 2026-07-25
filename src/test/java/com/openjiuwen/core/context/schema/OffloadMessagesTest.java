/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.context.schema;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link OffloadMessages} factory methods.
 */
class OffloadMessagesTest {

    @Test
    @DisplayName("createOffloadMessage creates user offload message")
    void testCreateUserOffloadMessage() {
        BaseMessage msg = OffloadMessages.createOffloadMessage(
                "user", "test content", "handle1", "in_memory");
        assertNotNull(msg);
        assertEquals("user", msg.getRole());
        assertEquals("test content", msg.getContentAsString());
        assertTrue(msg instanceof OffloadMixin);

        OffloadMixin offload = (OffloadMixin) msg;
        assertEquals("handle1", offload.getOffloadHandle());
        assertEquals("in_memory", offload.getOffloadType());
    }

    @Test
    @DisplayName("createOffloadMessage creates assistant offload message")
    void testCreateAssistantOffloadMessage() {
        BaseMessage msg = OffloadMessages.createOffloadMessage(
                "assistant", "summarized", "handle2", "in_memory");
        assertNotNull(msg);
        assertEquals("assistant", msg.getRole());
        assertTrue(msg instanceof OffloadMixin);
    }

    @Test
    @DisplayName("createOffloadMessage creates system offload message")
    void testCreateSystemOffloadMessage() {
        BaseMessage msg = OffloadMessages.createOffloadMessage(
                "system", "system note", "handle3", "in_memory");
        assertNotNull(msg);
        assertEquals("system", msg.getRole());
    }

    @Test
    @DisplayName("createOffloadMessage creates tool offload message")
    void testCreateToolOffloadMessage() {
        BaseMessage msg = OffloadMessages.createOffloadMessage(
                "tool", "tool result", "handle4", "in_memory");
        assertNotNull(msg);
        assertEquals("tool", msg.getRole());
    }

    @Test
    @DisplayName("createOffloadMessage defaults to user for unknown role")
    void testCreateUnknownRole() {
        BaseMessage msg = OffloadMessages.createOffloadMessage(
                "unknown", "content", "handle5", "in_memory");
        // Default case creates an OffloadUserMessage
        assertNotNull(msg);
        assertTrue(msg instanceof OffloadMixin);
    }

    @Test
    @DisplayName("Offload messages have metadata map")
    void testOffloadMetadata() {
        BaseMessage msg = OffloadMessages.createOffloadMessage(
                "user", "test", "handle", "in_memory");
        assertTrue(msg instanceof OffloadMixin);

        OffloadMixin offload = (OffloadMixin) msg;
        assertNotNull(offload.modelDump());
    }
}

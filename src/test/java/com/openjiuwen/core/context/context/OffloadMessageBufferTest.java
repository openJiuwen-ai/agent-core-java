/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.context.context;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link OffloadMessageBuffer}.
 */
class OffloadMessageBufferTest {

    @Test
    @DisplayName("offload and reload messages")
    void testOffloadAndReload() {
        OffloadMessageBuffer buffer = new OffloadMessageBuffer();
        List<BaseMessage> messages = List.of(new UserMessage("test message"));

        buffer.offload("handle1", "in_memory", messages);

        List<BaseMessage> reloaded = buffer.reload("handle1", "in_memory");
        assertNotNull(reloaded);
        assertEquals(1, reloaded.size());
        assertEquals("test message", reloaded.get(0).getContentAsString());
    }

    @Test
    @DisplayName("reload returns empty list for missing handle")
    void testReloadMissing() {
        OffloadMessageBuffer buffer = new OffloadMessageBuffer();
        List<BaseMessage> result = buffer.reload("nonexistent", "in_memory");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("clear removes specific offloaded messages")
    void testClear() {
        OffloadMessageBuffer buffer = new OffloadMessageBuffer();
        buffer.offload("h1", "in_memory", List.of(new UserMessage("a")));
        buffer.offload("h2", "in_memory", List.of(new UserMessage("b")));

        buffer.clear("h1", "in_memory");

        // h1 should be removed, reload returns empty list
        assertTrue(buffer.reload("h1", "in_memory").isEmpty());
        // h2 should still exist
        assertFalse(buffer.reload("h2", "in_memory").isEmpty());
    }

    @Test
    @DisplayName("getAll returns all stored messages")
    void testGetAll() {
        OffloadMessageBuffer buffer = new OffloadMessageBuffer();
        buffer.offload("h1", "in_memory", List.of(new UserMessage("a")));
        buffer.offload("h2", "in_memory", List.of(new UserMessage("b")));

        Map<String, List<BaseMessage>> all = buffer.getAll();
        assertEquals(2, all.size());
        assertTrue(all.containsKey("h1"));
        assertTrue(all.containsKey("h2"));
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.context.token;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SimpleTokenCounter}.
 */
class SimpleTokenCounterTest {

    private final SimpleTokenCounter counter = new SimpleTokenCounter();

    @Test
    @DisplayName("count returns positive value for non-empty text")
    void testCountText() {
        int tokens = counter.count("Hello, how are you today?");
        assertTrue(tokens > 0, "Token count should be positive for non-empty text");
    }

    @Test
    @DisplayName("count returns 0 for null text")
    void testCountNull() {
        assertEquals(0, counter.count(null));
    }

    @Test
    @DisplayName("count returns 0 for empty text")
    void testCountEmpty() {
        assertEquals(0, counter.count(""));
    }

    @Test
    @DisplayName("countMessages includes framing overhead")
    void testCountMessages() {
        BaseMessage msg = new BaseMessage("user", "Hello world");
        int tokens = counter.countMessages(List.of(msg));
        // Should be more than just the text (framing overhead)
        assertTrue(tokens > counter.count("Hello world"),
                "Message count should include framing overhead");
    }

    @Test
    @DisplayName("countMessages handles multiple messages")
    void testCountMultipleMessages() {
        BaseMessage msg1 = new BaseMessage("user", "Hello");
        BaseMessage msg2 = new BaseMessage("assistant", "Hi there");
        int tokens = counter.countMessages(List.of(msg1, msg2));

        int single1 = counter.countMessages(List.of(msg1));
        int single2 = counter.countMessages(List.of(msg2));
        // Base overhead + individual messages
        assertTrue(tokens > 0);
    }

    @Test
    @DisplayName("countTools returns positive value for tools")
    void testCountTools() {
        ToolInfo tool = ToolInfo.builder()
                .name("test_tool")
                .description("A test tool")
                .build();
        int tokens = counter.countTools(List.of(tool));
        assertTrue(tokens > 0);
    }

    @Test
    @DisplayName("countTools returns 0 for empty list")
    void testCountToolsEmpty() {
        assertEquals(0, counter.countTools(List.of()));
    }
}

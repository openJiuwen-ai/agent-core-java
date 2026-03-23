/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.context;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ContextWindow}.
 */
class ContextWindowTest {

    @Test
    @DisplayName("Default builder creates empty lists")
    void testDefaults() {
        ContextWindow window = ContextWindow.builder().build();
        assertNotNull(window.getSystemMessages());
        assertNotNull(window.getContextMessages());
        assertNotNull(window.getTools());
        assertTrue(window.getSystemMessages().isEmpty());
        assertTrue(window.getContextMessages().isEmpty());
        assertTrue(window.getTools().isEmpty());
    }

    @Test
    @DisplayName("getMessages combines system and context messages")
    void testGetMessages() {
        BaseMessage sys = new BaseMessage("system", "You are helpful");
        BaseMessage user = new BaseMessage("user", "Hello");
        BaseMessage assistant = new BaseMessage("assistant", "Hi there");

        ContextWindow window = ContextWindow.builder()
                .systemMessages(new ArrayList<>(List.of(sys)))
                .contextMessages(new ArrayList<>(List.of(user, assistant)))
                .build();

        List<BaseMessage> messages = window.getMessages();
        assertEquals(3, messages.size());
        assertEquals("system", messages.get(0).getRole());
        assertEquals("user", messages.get(1).getRole());
        assertEquals("assistant", messages.get(2).getRole());
    }

    @Test
    @DisplayName("getToolList returns tools list")
    void testGetToolList() {
        ToolInfo tool = ToolInfo.builder().build();
        ContextWindow window = ContextWindow.builder()
                .tools(new ArrayList<>(List.of(tool)))
                .build();

        assertEquals(1, window.getToolList().size());
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.context.context;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link ContextMessageBuffer}.
 */
class ContextMessageBufferTest {

    @Test
    @DisplayName("New buffer with messages reports correct size")
    void testSize() {
        List<BaseMessage> msgs = List.of(
                new UserMessage("hello"),
                new AssistantMessage("hi"));
        ContextMessageBuffer buffer = new ContextMessageBuffer(new ArrayList<>(msgs), null);
        assertEquals(2, buffer.size());
    }

    @Test
    @DisplayName("addBack appends messages")
    void testAddBack() {
        ContextMessageBuffer buffer = new ContextMessageBuffer(new ArrayList<>(), null);
        buffer.addBack(List.of(new UserMessage("hello")));
        assertEquals(1, buffer.size());

        buffer.addBack(List.of(new AssistantMessage("hi")));
        assertEquals(2, buffer.size());
    }

    @Test
    @DisplayName("getBack returns all messages when size is null")
    void testGetBackAll() {
        List<BaseMessage> msgs = new ArrayList<>(List.of(
                new UserMessage("a"),
                new UserMessage("b"),
                new UserMessage("c")));
        ContextMessageBuffer buffer = new ContextMessageBuffer(msgs, null);

        List<BaseMessage> result = buffer.getBack();
        assertEquals(3, result.size());
    }

    @Test
    @DisplayName("getBack returns last N messages with history")
    void testGetBackN() {
        List<BaseMessage> msgs = new ArrayList<>(List.of(
                new UserMessage("a"),
                new UserMessage("b"),
                new UserMessage("c")));
        ContextMessageBuffer buffer = new ContextMessageBuffer(msgs, null);

        // withHistory=true to include history messages
        List<BaseMessage> result = buffer.getBack(2, true);
        assertEquals(2, result.size());
        assertEquals("b", result.get(0).getContentAsString());
        assertEquals("c", result.get(1).getContentAsString());
    }

    @Test
    @DisplayName("popBack removes last N messages with history")
    void testPopBack() {
        List<BaseMessage> msgs = new ArrayList<>(List.of(
                new UserMessage("a"),
                new UserMessage("b"),
                new UserMessage("c")));
        ContextMessageBuffer buffer = new ContextMessageBuffer(msgs, null);

        // withHistory=true to pop from history too
        List<BaseMessage> popped = buffer.popBack(2, true);
        assertEquals(2, popped.size());
        assertEquals(1, buffer.size());
        assertEquals("a", buffer.getBack().get(0).getContentAsString());
    }

    @Test
    @DisplayName("setMessages replaces non-history messages")
    void testSetMessages() {
        // Start with empty buffer (no history)
        ContextMessageBuffer buffer = new ContextMessageBuffer(new ArrayList<>(), null);
        buffer.addBack(List.of(new UserMessage("old")));

        buffer.setMessages(List.of(new UserMessage("new1"), new UserMessage("new2")), true);
        assertEquals(2, buffer.size());
        assertEquals("new1", buffer.getBack().get(0).getContentAsString());
    }

    @Test
    @DisplayName("Buffer respects maxBufferSize by resizing")
    void testMaxBufferSize() {
        ContextMessageBuffer buffer = new ContextMessageBuffer(new ArrayList<>(), 3);
        buffer.addBack(List.of(
                new UserMessage("a"),
                new UserMessage("b"),
                new UserMessage("c")));
        assertEquals(3, buffer.size());

        buffer.addBack(List.of(new UserMessage("d")));
        // Should have resized old messages to history
        assertEquals(3, buffer.size());
    }

    @Test
    @DisplayName("rebuild replaces all messages maintaining history")
    void testRebuild() {
        List<BaseMessage> initial = new ArrayList<>(List.of(
                new UserMessage("a"),
                new UserMessage("b")));
        ContextMessageBuffer buffer = new ContextMessageBuffer(initial, null);

        buffer.rebuild(List.of(new UserMessage("x"), new UserMessage("y"), new UserMessage("z")));
        assertEquals(3, buffer.size());
    }
}

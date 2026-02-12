/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.process.extract;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ExtractUtils.
 * Corresponds to Python: test_common.py TestBuildModelInput
 */
class ExtractUtilsTest {

    @Test
    void testBuildBasicInput() {
        List<BaseMessage> messages = List.of(new UserMessage("Hello"));

        List<Map<String, Object>> result = ExtractUtils.buildModelInput(
                messages,
                List.of(),
                "System prompt"
        );

        assertEquals(2, result.size());
        assertEquals("system", result.get(0).get("role"));
        assertEquals("System prompt", result.get(0).get("content"));
        assertEquals("user", result.get(1).get("role"));
    }

    @Test
    void testBuildWithHistoryAsString() {
        List<BaseMessage> messages = List.of(new UserMessage("Current message"));

        List<Map<String, Object>> result = ExtractUtils.buildModelInput(
                messages,
                "Previous conversation content",
                "System prompt"
        );

        String userContent = (String) result.get(1).get("content");
        assertTrue(userContent.contains("Previous conversation content"));
        assertTrue(userContent.contains("historical_messages"));
    }

    @Test
    void testBuildWithHistoryAsList() {
        List<BaseMessage> messages = List.of(new UserMessage("Current"));
        List<BaseMessage> history = List.of(
                new UserMessage("Hi"),
                new AssistantMessage("Hello")
        );

        List<Map<String, Object>> result = ExtractUtils.buildModelInput(
                messages,
                history,
                "System prompt"
        );

        String userContent = (String) result.get(1).get("content");
        assertTrue(userContent.contains("user: Hi"));
        assertTrue(userContent.contains("assistant: Hello"));
    }

    @Test
    void testBuildWithoutHistory() {
        List<BaseMessage> messages = List.of(new UserMessage("Only message"));

        List<Map<String, Object>> result = ExtractUtils.buildModelInput(
                messages,
                List.of(),
                "System prompt"
        );

        String userContent = (String) result.get(1).get("content");
        assertFalse(userContent.contains("historical_messages"));
    }

    @Test
    void testBuildWithEmptyStringHistory() {
        List<BaseMessage> messages = List.of(new UserMessage("Message"));

        List<Map<String, Object>> result = ExtractUtils.buildModelInput(
                messages,
                "",
                "System prompt"
        );

        String userContent = (String) result.get(1).get("content");
        assertFalse(userContent.contains("historical_messages"));
    }

    @Test
    void testBuildMultipleCurrentMessages() {
        List<BaseMessage> messages = List.of(
                new UserMessage("First"),
                new AssistantMessage("Second"),
                new UserMessage("Third")
        );

        List<Map<String, Object>> result = ExtractUtils.buildModelInput(
                messages,
                List.of(),
                "System prompt"
        );

        String userContent = (String) result.get(1).get("content");
        assertTrue(userContent.contains("user: First"));
        assertTrue(userContent.contains("assistant: Second"));
        assertTrue(userContent.contains("user: Third"));
    }

    @Test
    void testBuildContainsCurrentMessagesTag() {
        List<BaseMessage> messages = List.of(new UserMessage("Test"));

        List<Map<String, Object>> result = ExtractUtils.buildModelInput(
                messages,
                List.of(),
                "System prompt"
        );

        String userContent = (String) result.get(1).get("content");
        assertTrue(userContent.contains("<current_messages>"));
        assertTrue(userContent.contains("</current_messages>"));
    }

    @Test
    void testBuildSystemPromptUnchanged() {
        String customPrompt = "This is a custom system prompt with 特殊字符";
        List<BaseMessage> messages = List.of(new UserMessage("Test"));

        List<Map<String, Object>> result = ExtractUtils.buildModelInput(
                messages,
                List.of(),
                customPrompt
        );

        assertEquals(customPrompt, result.get(0).get("content"));
    }
}



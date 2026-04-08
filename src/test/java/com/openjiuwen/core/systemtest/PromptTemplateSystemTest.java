/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the PromptTemplate module.
 * Validates placeholder substitution, message conversion, and template composition.
 */
@Tag("system-test")
class PromptTemplateSystemTest {

    @Test
    @DisplayName("PromptTemplate basic placeholder substitution")
    void testBasicPlaceholderSubstitution() {
        PromptTemplate template = PromptTemplate.builder()
                .content("你好，{{name}}！请回答关于{{topic}}的问题。")
                .build();

        PromptTemplate filled = template.format(Map.of("name", "用户", "topic", "Java编程"));
        List<BaseMessage> messages = filled.toMessages();

        assertNotNull(messages);
        assertFalse(messages.isEmpty());
        String content = messages.get(0).getContentAsString();
        assertTrue(content.contains("用户"), "Should contain substituted name");
        assertTrue(content.contains("Java编程"), "Should contain substituted topic");
        assertFalse(content.contains("{{"), "Should not contain unresolved placeholders");
        System.out.println("[PromptTemplate Basic] " + content);
    }

    @Test
    @DisplayName("PromptTemplate converts string to UserMessage")
    void testStringToUserMessage() {
        PromptTemplate template = PromptTemplate.builder()
                .content("Hello World")
                .build();

        List<BaseMessage> messages = template.toMessages();
        assertNotNull(messages);
        assertEquals(1, messages.size());
        assertEquals("Hello World", messages.get(0).getContentAsString());
    }

    @Test
    @DisplayName("PromptTemplate preserves message list")
    void testMessageListPreserved() {
        List<BaseMessage> original = List.of(
                new UserMessage("First message"),
                new UserMessage("Second message"));
        PromptTemplate template = PromptTemplate.builder()
                .content(original)
                .build();

        List<BaseMessage> result = template.toMessages();
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("First message", result.get(0).getContentAsString());
        assertEquals("Second message", result.get(1).getContentAsString());
    }

    @Test
    @DisplayName("PromptTemplate custom delimiters")
    void testCustomDelimiters() {
        PromptTemplate template = PromptTemplate.builder()
                .content("Hello, <<name>>!")
                .placeholderPrefix("<<")
                .placeholderSuffix(">>")
                .build();

        PromptTemplate filled = template.format(Map.of("name", "World"));
        String content = filled.toMessages().get(0).getContentAsString();
        assertEquals("Hello, World!", content);
    }

    @Test
    @DisplayName("PromptTemplate multiple placeholders")
    void testMultiplePlaceholders() {
        PromptTemplate template = PromptTemplate.builder()
                .content("{{role}}需要{{action}}来完成{{task}}")
                .build();

        PromptTemplate filled = template.format(
                Map.of("role", "助手", "action", "分析数据", "task", "报告生成"));
        String content = filled.toMessages().get(0).getContentAsString();
        assertEquals("助手需要分析数据来完成报告生成", content);
    }

    @Test
    @DisplayName("PromptTemplate with unresolved placeholders")
    void testUnresolvedPlaceholders() {
        PromptTemplate template = PromptTemplate.builder()
                .content("Hello {{name}}, your role is {{role}}")
                .build();

        PromptTemplate filled = template.format(Map.of("name", "Alice"));
        String content = filled.toMessages().get(0).getContentAsString();
        assertTrue(content.contains("Alice"));
        System.out.println("[PromptTemplate Unresolved] " + content);
    }
}

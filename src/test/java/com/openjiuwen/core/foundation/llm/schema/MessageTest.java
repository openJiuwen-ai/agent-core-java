// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.schema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试消息模型的关键功能
 */
class MessageTest {

    @Test
    @DisplayName("测试 AssistantMessage 转换 OpenAI 格式的 tool_calls")
    void testAssistantMessageConvertOpenAIToolCallsFormat() {
        // OpenAI 格式：嵌套 function 对象
        Map<String, Object> openaiFormat = Map.of(
            "role", "assistant",
            "content", "",
            "tool_calls", List.of(
                Map.of(
                    "id", "call_1",
                    "type", "function",
                    "function", Map.of(
                        "name", "test_tool",
                        "arguments", "{\"x\": 1}"
                    )
                )
            )
        );
        
        AssistantMessage message = AssistantMessage.fromMap(openaiFormat);
        assertNotNull(message.getToolCalls());
        assertEquals(1, message.getToolCalls().size());
        assertEquals("call_1", message.getToolCalls().get(0).getId());
        assertEquals("test_tool", message.getToolCalls().get(0).getName());
        assertEquals("{\"x\": 1}", message.getToolCalls().get(0).getArguments());
    }

    @Test
    @DisplayName("测试 AssistantMessage.toMap 转换为 OpenAI 格式")
    void testAssistantMessageToMapConvertsToOpenAIFormat() {
        ToolCall toolCall = new ToolCall();
        toolCall.setId("call_1");
        toolCall.setType("function");
        toolCall.setName("test_tool");
        toolCall.setArguments("{\"x\": 1}");
        
        AssistantMessage message = new AssistantMessage();
        message.setContent("");
        message.setToolCalls(List.of(toolCall));
        
        Map<String, Object> dumped = message.toMap();
        assertTrue(dumped.containsKey("tool_calls"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) dumped.get("tool_calls");
        assertEquals("call_1", toolCalls.get(0).get("id"));
        assertEquals("function", toolCalls.get(0).get("type"));
        assertTrue(toolCalls.get(0).containsKey("function"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> function = (Map<String, Object>) toolCalls.get(0).get("function");
        assertEquals("test_tool", function.get("name"));
        assertEquals("{\"x\": 1}", function.get("arguments"));
    }

    @Test
    @DisplayName("测试 AssistantMessage 包含 usage_metadata")
    void testAssistantMessageWithUsageMetadata() {
        UsageMetadata usage = new UsageMetadata();
        usage.setModelName("gpt-4");
        usage.setInputTokens(100);
        usage.setOutputTokens(50);
        usage.setTotalTokens(150);
        usage.setCacheTokens(10);
        
        AssistantMessage message = new AssistantMessage();
        message.setContent("Test");
        message.setUsageMetadata(usage);
        
        assertNotNull(message.getUsageMetadata());
        assertEquals("gpt-4", message.getUsageMetadata().getModelName());
        assertEquals(150, message.getUsageMetadata().getTotalTokens());
        assertEquals(10, message.getUsageMetadata().getCacheTokens());
    }

    @Test
    @DisplayName("测试 AssistantMessage 包含 parser_content")
    void testAssistantMessageWithParserContent() {
        AssistantMessage message = new AssistantMessage();
        message.setContent("{\"result\": \"success\"}");
        message.setParserContent(Map.of("result", "success"));
        
        assertEquals(Map.of("result", "success"), message.getParserContent());
    }

    @Test
    @DisplayName("测试 AssistantMessage 包含 reasoning_content")
    void testAssistantMessageWithReasoningContent() {
        AssistantMessage message = new AssistantMessage();
        message.setContent("Final answer");
        message.setReasoningContent("Step 1: ... Step 2: ...");
        
        assertEquals("Step 1: ... Step 2: ...", message.getReasoningContent());
    }

    @Test
    @DisplayName("测试 ToolMessage 包含 tool_call_id")
    void testToolMessageWithToolCallId() {
        ToolMessage message = new ToolMessage("call_1", "Tool result");
        
        assertEquals("call_1", message.getToolCallId());
        assertEquals("Tool result", message.getContent());
        assertEquals("tool", message.getRole());
    }

    @Test
    @DisplayName("测试 UserMessage 默认 role")
    void testUserMessageDefaultRole() {
        UserMessage message = new UserMessage("Hello");
        assertEquals("user", message.getRole());
    }

    @Test
    @DisplayName("测试 SystemMessage 默认 role")
    void testSystemMessageDefaultRole() {
        SystemMessage message = new SystemMessage("You are a helpful assistant");
        assertEquals("system", message.getRole());
    }

    @Test
    @DisplayName("测试 BaseMessage 包含 name 字段")
    void testBaseMessageWithName() {
        UserMessage message = new UserMessage("Hello", "user_123");
        assertEquals("user_123", message.getName());
    }

    @Test
    @DisplayName("测试 AssistantMessage finish_reason 默认值")
    void testAssistantMessageFinishReason() {
        // 有 tool_calls 时 finish_reason 默认值
        ToolCall toolCall = new ToolCall();
        toolCall.setId("call_1");
        toolCall.setType("function");
        toolCall.setName("test");
        toolCall.setArguments("{}");
        
        AssistantMessage messageWithTools = new AssistantMessage();
        messageWithTools.setContent("");
        messageWithTools.setToolCalls(List.of(toolCall));
        assertEquals("null", messageWithTools.getFinishReason()); // 默认值
        
        // 无 tool_calls 时 finish_reason 默认值
        AssistantMessage messageWithoutTools = new AssistantMessage("Done");
        assertEquals("null", messageWithoutTools.getFinishReason()); // 默认值
    }
}



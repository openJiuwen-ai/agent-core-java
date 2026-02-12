// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.schema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试消息块合并功能
 */
class MessageChunkTest {

    @Test
    @DisplayName("测试 mergeParserContent 基本类型")
    void testMergeParserContentBasicTypes() {
        assertEquals("b", MessageChunkUtils.mergeParserContent(null, "b"));
        assertEquals("a", MessageChunkUtils.mergeParserContent("a", null));
        assertEquals("ab", MessageChunkUtils.mergeParserContent("a", "b"));
        assertEquals(List.of(1, 2), MessageChunkUtils.mergeParserContent(List.of(1), List.of(2)));
        
        Map<String, Object> merged = MessageChunkUtils.mergeParserContent(
            Map.of("a", 1), 
            Map.of("b", 2)
        );
        assertEquals(1, merged.get("a"));
        assertEquals(2, merged.get("b"));
    }

    @Test
    @DisplayName("测试 mergeDicts 递归合并")
    void testMergeDictsRecursive() {
        Map<String, Object> left = Map.of(
            "a", Map.of("x", "1"),
            "b", List.of(1),
            "c", "m"
        );
        Map<String, Object> right = Map.of(
            "a", Map.of("y", "2"),
            "b", List.of(2),
            "c", "n",
            "d", 3
        );
        
        Map<String, Object> merged = MessageChunkUtils.mergeDicts(left, right);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> mergedA = (Map<String, Object>) merged.get("a");
        assertEquals("1", mergedA.get("x"));
        assertEquals("2", mergedA.get("y"));
        
        assertEquals(List.of(1, 2), merged.get("b"));
        assertEquals("mn", merged.get("c"));
        assertEquals(3, merged.get("d"));
    }

    @Test
    @DisplayName("测试 AssistantMessageChunk 合并 tool_calls")
    void testAssistantMessageChunkMergesToolCalls() {
        ToolCall toolCall1 = new ToolCall();
        toolCall1.setId("call_1");
        toolCall1.setType("function");
        toolCall1.setName("foo");
        toolCall1.setArguments("{");
        
        AssistantMessageChunk left = new AssistantMessageChunk();
        left.setContent("hello");
        left.setToolCalls(List.of(toolCall1));
        left.setFinishReason("null");
        
        ToolCall toolCall2 = new ToolCall();
        toolCall2.setId("call_1");
        toolCall2.setType("function");
        toolCall2.setName("bar");
        toolCall2.setArguments("}");
        
        AssistantMessageChunk right = new AssistantMessageChunk();
        right.setContent(" world");
        right.setToolCalls(List.of(toolCall2));
        right.setFinishReason("stop");
        
        AssistantMessageChunk merged = left.merge(right);
        
        assertEquals("hello world", merged.getContent());
        assertEquals(1, merged.getToolCalls().size());
        assertEquals("foobar", merged.getToolCalls().get(0).getName());
        assertEquals("{}", merged.getToolCalls().get(0).getArguments());
        assertEquals("stop", merged.getFinishReason());
    }

    @Test
    @DisplayName("测试 ToolMessageChunk 合并")
    void testToolMessageChunkMerge() {
        ToolMessageChunk left = new ToolMessageChunk("t1", "a");
        ToolMessageChunk right = new ToolMessageChunk("t2", "b");
        
        ToolMessageChunk merged = left.merge(right);
        
        assertEquals("ab", merged.getContent());
        assertEquals("t2", merged.getToolCallId());
    }
}



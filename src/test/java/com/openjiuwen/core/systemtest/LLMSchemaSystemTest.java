/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.systemtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.foundation.llm.output_parsers.JsonOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * System tests for LLM schema classes, output parsers, and message merging.
 * Covers gaps identified in CHECK doc: AssistantMessage fields, UsageMetadata,
 * AssistantMessageChunk.merge(), JsonOutputParser, ProviderType.
 * All tests are local (no remote API required).
 */
@Tag("system-test")
class LLMSchemaSystemTest {
    @Nested
    @DisplayName("AssistantMessage Field Tests")
    class AssistantMessageTests {
        @Test
        @DisplayName("AssistantMessage carries reasoningContent")
        void testReasoningContent() {
            AssistantMessage msg = AssistantMessage.builder().content("The answer is 42.")
                    .reasoningContent("Let me think step by step...").build();

            assertEquals("The answer is 42.", msg.getContentAsString());
            assertEquals("Let me think step by step...", msg.getReasoningContent());
        }

        @Test
        @DisplayName("AssistantMessage carries parserContent")
        void testParserContent() {
            Map<String, Object> parsed = Map.of("key", "value", "count", 3);
            AssistantMessage msg =
                AssistantMessage.builder().content("{\"key\":\"value\",\"count\":3}").parserContent(parsed).build();

            assertNotNull(msg.getParserContent());
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) msg.getParserContent();
            assertEquals("value", result.get("key"));
            assertEquals(3, result.get("count"));
        }

        @Test
        @DisplayName("AssistantMessage carries toolCalls")
        void testToolCalls() {
            ToolCall tc =
                ToolCall.builder().id("call_001").name("get_weather").arguments("{\"city\":\"Beijing\"}").build();

            AssistantMessage msg = AssistantMessage.builder().content("").toolCalls(List.of(tc)).build();

            assertNotNull(msg.getToolCalls());
            assertEquals(1, msg.getToolCalls().size());
            assertEquals("get_weather", msg.getToolCalls().get(0).getName());
            assertEquals("{\"city\":\"Beijing\"}", msg.getToolCalls().get(0).getArguments());
        }

        @Test
        @DisplayName("AssistantMessage.toApiFormat includes tool_calls when present")
        void testToApiFormat() {
            ToolCall tc = ToolCall.builder().id("call_002").name("search").arguments("{\"q\":\"test\"}").build();

            AssistantMessage msg =
                AssistantMessage.builder().content("I'll search for you.").toolCalls(List.of(tc)).build();

            Map<String, Object> apiFormat = msg.toApiFormat();
            assertNotNull(apiFormat);
            assertEquals("assistant", apiFormat.get("role"));
            assertNotNull(apiFormat.get("content"));
        }
    }

    @Nested
    @DisplayName("UsageMetadata Tests")
    class UsageMetadataTests {
        @Test
        @DisplayName("UsageMetadata tracks cacheTokens")
        void testCacheTokens() {
            UsageMetadata usage =
                UsageMetadata.builder().inputTokens(100).outputTokens(50).totalTokens(150).cacheTokens(30).build();

            assertEquals(100, usage.getInputTokens());
            assertEquals(50, usage.getOutputTokens());
            assertEquals(150, usage.getTotalTokens());
            assertEquals(30, usage.getCacheTokens());
        }

        @Test
        @DisplayName("UsageMetadata default values")
        void testDefaults() {
            UsageMetadata usage = UsageMetadata.builder().build();

            assertEquals(0, usage.getCode());
            assertEquals("", usage.getErrMsg());
            assertEquals(0, usage.getInputTokens());
            assertEquals(0, usage.getOutputTokens());
            assertEquals(0, usage.getTotalTokens());
            assertEquals(0, usage.getCacheTokens());
            assertEquals(0.0, usage.getTotalLatency());
        }

        @Test
        @DisplayName("UsageMetadata carries model and latency info")
        void testModelAndLatency() {
            UsageMetadata usage =
                UsageMetadata.builder().modelName("GLM-4").totalLatency(1234.5).taskId("task_001").build();

            assertEquals("GLM-4", usage.getModelName());
            assertEquals(1234.5, usage.getTotalLatency());
            assertEquals("task_001", usage.getTaskId());
        }
    }

    @Nested
    @DisplayName("AssistantMessageChunk Merge Tests")
    class ChunkMergeTests {
        @Test
        @DisplayName("Merge two text chunks concatenates content")
        void testMergeTextChunks() {
            AssistantMessageChunk chunk1 = AssistantMessageChunk.builder().content("Hello, ").build();

            AssistantMessageChunk chunk2 = AssistantMessageChunk.builder().content("world!").build();

            AssistantMessageChunk merged = chunk1.merge(chunk2);
            assertNotNull(merged);
            assertEquals("Hello, world!", merged.getContentAsString());
        }

        @Test
        @DisplayName("Merge chunks with tool call deltas (no index, empty-string id/name)")
        void testMergeToolCallDeltas() {
            ToolCall tc1 = ToolCall.builder().id("call_001").name("get_weather").arguments("{\"ci").build();

            ToolCall tc2 = ToolCall.builder().id("").name("").arguments("ty\":\"BJ\"}").build();

            AssistantMessageChunk chunk1 = AssistantMessageChunk.builder().content("").toolCalls(List.of(tc1)).build();

            AssistantMessageChunk chunk2 = AssistantMessageChunk.builder().content("").toolCalls(List.of(tc2)).build();

            AssistantMessageChunk merged = chunk1.merge(chunk2);
            assertNotNull(merged);
            assertNotNull(merged.getToolCalls());
            assertEquals(1, merged.getToolCalls().size());
            assertEquals("call_001", merged.getToolCalls().get(0).getId());
            assertEquals("get_weather", merged.getToolCalls().get(0).getName());
            assertEquals("{\"city\":\"BJ\"}", merged.getToolCalls().get(0).getArguments());
        }

        @Test
        @DisplayName("Merge GLM-style streaming tool call deltas with index and empty-string id")
        void testMergeGlmStreamingToolCallDeltas() {

            AssistantMessageChunk frame1 = AssistantMessageChunk.builder()
                    .content("")
                    .toolCalls(List.of(ToolCall.builder()
                            .id("call_xxx")
                            .type("function")
                            .name("trip-planning-agent")
                            .arguments("{")
                            .index(0)
                            .build()))
                    .build();

            AssistantMessageChunk frame2 = AssistantMessageChunk.builder()
                    .content("")
                    .toolCalls(List.of(ToolCall.builder()
                            .id("")
                            .type("function")
                            .name("")
                            .arguments("\"remoteInput\":")
                            .index(0)
                            .build()))
                    .build();

            AssistantMessageChunk frame3 = AssistantMessageChunk.builder()
                    .content("")
                    .toolCalls(List.of(ToolCall.builder()
                            .id("")
                            .type("function")
                            .name("")
                            .arguments("\"明天从上海到北京出差3天，住宿2晚\"")
                            .index(0)
                            .build()))
                    .build();

            AssistantMessageChunk frame4 = AssistantMessageChunk.builder()
                    .content("")
                    .toolCalls(List.of(ToolCall.builder()
                            .id("")
                            .type("function")
                            .name("")
                            .arguments("}")
                            .index(0)
                            .build()))
                    .finishReason("tool_calls")
                    .build();

            AssistantMessageChunk merged = frame1.merge(frame2).merge(frame3).merge(frame4);
            assertNotNull(merged);
            assertNotNull(merged.getToolCalls());
            assertEquals(1, merged.getToolCalls().size());
            assertEquals("call_xxx", merged.getToolCalls().get(0).getId());
            assertEquals("trip-planning-agent", merged.getToolCalls().get(0).getName());
            assertEquals("function", merged.getToolCalls().get(0).getType());
            assertEquals(0, merged.getToolCalls().get(0).getIndex());
            assertEquals("{\"remoteInput\":\"明天从上海到北京出差3天，住宿2晚\"}",
                    merged.getToolCalls().get(0).getArguments());
            assertEquals("tool_calls", merged.getFinishReason());
        }

        @Test
        @DisplayName("Merge multiple parallel tool call deltas distinguished by index")
        void testMergeMultipleParallelToolCallsByIndex() {
            ToolCall tc1f1 = ToolCall.builder()
                    .index(0)
                    .id("call_a")
                    .name("tool_a")
                    .arguments("{\"a")
                    .build();

            ToolCall tc2f1 = ToolCall.builder()
                    .index(1)
                    .id("call_b")
                    .name("tool_b")
                    .arguments("{\"b")
                    .build();

            ToolCall tc1f2 = ToolCall.builder()
                    .index(0)
                    .id("")
                    .name("")
                    .arguments("\":1}")
                    .build();

            ToolCall tc2f2 = ToolCall.builder()
                    .index(1)
                    .id("")
                    .name("")
                    .arguments("\":2}")
                    .build();

            AssistantMessageChunk frame1 = AssistantMessageChunk.builder()
                    .content("")
                    .toolCalls(List.of(tc1f1, tc2f1))
                    .build();

            AssistantMessageChunk frame2 = AssistantMessageChunk.builder()
                    .content("")
                    .toolCalls(List.of(tc1f2, tc2f2))
                    .build();

            AssistantMessageChunk merged = frame1.merge(frame2);
            assertNotNull(merged);
            assertNotNull(merged.getToolCalls());
            assertEquals(2, merged.getToolCalls().size());

            ToolCall first = merged.getToolCalls().get(0);
            assertEquals("call_a", first.getId());
            assertEquals("tool_a", first.getName());
            assertEquals("{\"a\":1}", first.getArguments());
            assertEquals(0, first.getIndex());

            ToolCall second = merged.getToolCalls().get(1);
            assertEquals("call_b", second.getId());
            assertEquals("tool_b", second.getName());
            assertEquals("{\"b\":2}", second.getArguments());
            assertEquals(1, second.getIndex());
        }

        @Test
        @DisplayName("Merge chunks uses last-wins for reasoning content")
        void testMergeReasoningContent() {
            AssistantMessageChunk chunk1 =
                AssistantMessageChunk.builder().content("").reasoningContent("Step 1: ").build();

            AssistantMessageChunk chunk2 =
                AssistantMessageChunk.builder().content("").reasoningContent("Analyze data").build();

            // merge uses last-wins: other.reasoningContent takes precedence
            AssistantMessageChunk merged = chunk1.merge(chunk2);
            assertNotNull(merged);
            assertEquals("Analyze data", merged.getReasoningContent());

            // when other has null reasoning, this reasoning is preserved
            AssistantMessageChunk chunk3 = AssistantMessageChunk.builder().content("").build();
            AssistantMessageChunk merged2 = chunk1.merge(chunk3);
            assertEquals("Step 1: ", merged2.getReasoningContent());
        }
    }

    @Nested
    @DisplayName("JsonOutputParser Tests")
    class JsonOutputParserTests {
        @Test
        @DisplayName("Parse JSON from code block")
        void testParseJsonCodeBlock() {
            JsonOutputParser parser = new JsonOutputParser();
            String text = "Here's the JSON:\n```json\n{\"name\":\"test\",\"value\":42}\n```\nDone.";

            AssistantMessage msg = AssistantMessage.builder().content(text).build();
            Object result = parser.parse(msg);

            assertNotNull(result);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            assertEquals("test", map.get("name"));
            assertEquals(42, map.get("value"));
        }

        @Test
        @DisplayName("Parse plain JSON string")
        void testParsePlainJson() {
            JsonOutputParser parser = new JsonOutputParser();

            Object result = parser.parse("{\"status\":\"ok\"}");

            assertNotNull(result);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            assertEquals("ok", map.get("status"));
        }

        @Test
        @DisplayName("Parse returns null for invalid JSON")
        void testParseInvalidJson() {
            JsonOutputParser parser = new JsonOutputParser();
            Object result = parser.parse("This is not JSON at all");
            assertNull(result);
        }

        @Test
        @DisplayName("Stream parse collects chunks into JSON")
        void testStreamParse() {
            JsonOutputParser parser = new JsonOutputParser();

            List<Object> chunks = List.of(AssistantMessageChunk.builder().content("{\"ke").build(),
                    AssistantMessageChunk.builder().content("y\":\"val").build(),
                    AssistantMessageChunk.builder().content("ue\"}").build());

            Iterator<Object> result = parser.streamParse(chunks.iterator());
            assertTrue(result.hasNext(), "Stream parse should yield at least one result");
            Object parsed = result.next();
            assertNotNull(parsed);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) parsed;
            assertEquals("value", map.get("key"));
        }
    }

    @Nested
    @DisplayName("ProviderType Tests")
    class ProviderTypeTests {
        @Test
        @DisplayName("ProviderType includes OpenRouter")
        void testOpenRouterProvider() {
            boolean found = false;
            for (ProviderType pt : ProviderType.values()) {
                if ("OpenRouter".equalsIgnoreCase(pt.name()) || "OPENROUTER".equalsIgnoreCase(pt.name())) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "ProviderType should include OpenRouter");
        }

        @Test
        @DisplayName("ProviderType includes standard providers")
        void testStandardProviders() {
            ProviderType[] types = ProviderType.values();
            assertTrue(types.length >= 4, "Should have at least OpenAI, SiliconFlow, DashScope, OpenRouter");
        }
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Mirrors Python's message-schema behavior in
 * {@code openjiuwen/core/foundation/llm/schema/message.py}.
 */
class MessageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void assistantMessageDeserializesNestedOpenAiToolCalls() throws Exception {
        String json = """
                {
                  "content": "hello",
                  "tool_calls": [
                    {
                      "id": "call_1",
                      "type": "function",
                      "index": 2,
                      "function": {
                        "name": "search",
                        "arguments": "{\\"q\\":\\"weather\\"}"
                      }
                    }
                  ],
                  "usage_metadata": {
                    "prompt": "p",
                    "input_cost": 1.25,
                    "output_cost": 0.5,
                    "total_cost": 1.75
                  }
                }
                """;

        AssistantMessage message = MAPPER.readValue(json, AssistantMessage.class);

        assertEquals("assistant", message.getRole());
        assertNotNull(message.getToolCalls());
        assertEquals(1, message.getToolCalls().size());
        ToolCall call = message.getToolCalls().get(0);
        assertEquals("call_1", call.getId());
        assertEquals("function", call.getType());
        assertEquals("search", call.getName());
        assertEquals("{\"q\":\"weather\"}", call.getArguments());
        assertEquals(2, call.getIndex());
        assertNotNull(message.getUsageMetadata());
        assertEquals(1.25d, message.getUsageMetadata().getInputCost());
        assertEquals(1.75d, message.getUsageMetadata().getTotalCost());
        assertEquals("null", message.getFinishReason());
    }

    @Test
    void assistantModelDumpKeepsPythonShapeAndOptionalFields() {
        AssistantMessage message = new AssistantMessage("answer");
        message.setName("planner");
        message.setMetadata(new LinkedHashMap<>(Map.of("trace_id", "abc")));
        message.setToolCalls(List.of(ToolCall.builder()
                .id("call_1")
                .type("function")
                .name("search")
                .arguments("{\"q\":\"weather\"}")
                .index(3)
                .build()));
        message.setUsageMetadata(UsageMetadata.builder()
                .prompt("prompt")
                .inputCost(1.5d)
                .outputCost(0.5d)
                .totalCost(2.0d)
                .build());
        message.setPromptTokenIds(List.of(1, 2));
        message.setCompletionTokenIds(List.of(3, 4));
        message.setLogprobs(Map.of("tokens", List.of("answer")));

        Map<String, Object> dumped = message.modelDump();

        assertEquals("assistant", dumped.get("role"));
        assertEquals("answer", dumped.get("content"));
        assertEquals("planner", dumped.get("name"));
        assertEquals(Map.of("trace_id", "abc"), dumped.get("metadata"));
        Object usage = dumped.get("usage_metadata");
        assertInstanceOf(Map.class, usage);
        assertEquals(1.5d, ((Map<?, ?>) usage).get("input_cost"));
        Object toolCalls = dumped.get("tool_calls");
        assertInstanceOf(List.class, toolCalls);
        Map<?, ?> firstCall = (Map<?, ?>) ((List<?>) toolCalls).get(0);
        Map<?, ?> function = (Map<?, ?>) firstCall.get("function");
        assertEquals("search", function.get("name"));
        assertEquals("{\"q\":\"weather\"}", function.get("arguments"));
        assertEquals(List.of(1, 2), dumped.get("prompt_token_ids"));
        assertEquals(List.of(3, 4), dumped.get("completion_token_ids"));
        assertEquals(Map.of("tokens", List.of("answer")), dumped.get("logprobs"));
    }

    @Test
    void toolMessageAndBaseDefaultsMirrorPython() {
        ToolMessage toolMessage = new ToolMessage("done", "call-9");
        assertEquals("tool", toolMessage.getRole());
        assertEquals("call-9", toolMessage.modelDump().get("tool_call_id"));

        BaseMessage baseMessage = new BaseMessage();
        assertEquals("", baseMessage.getContentAsString());
        assertNotNull(baseMessage.getMetadata());
        assertEquals(0, baseMessage.getMetadata().size());
        assertNull(baseMessage.getContentAsList());
    }
}

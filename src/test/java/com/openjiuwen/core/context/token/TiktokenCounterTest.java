/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.token;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Focused parity tests for {@link TiktokenCounter}.
 *
 * <p>Mirrors Python's {@code TiktokenCounter} in
 * {@code openjiuwen/core/context_engine/token/tiktoken_counter.py}.</p>
 */
class TiktokenCounterTest {

    @Test
    void defaultConstructorUsesPythonLengthFallbackWhenNoEncoderIsAvailable() {
        TiktokenCounter counter = new TiktokenCounter();

        assertEquals(0, counter.count("abc"));
        assertEquals(1, counter.count("😀😀😀😀"));
        assertEquals("gpt-4", counter.getModel());
        assertEquals("cl100k_base", counter.getEncodingName());
    }

    @Test
    void modelNamesMapToPythonEncodingNames() {
        assertEquals("o200k_base", new TiktokenCounter("gpt-4o").getEncodingName());
        assertEquals("cl100k_base", new TiktokenCounter("unknown-model").getEncodingName());
        assertEquals("cl100k_base", new TiktokenCounter(null).getEncodingName());
    }

    @Test
    void countMessagesUsesOpenAiStylePiecesAndAssistantToolCalls() {
        TiktokenCounter counter = new TiktokenCounter("gpt-4", String::length);
        AssistantMessage assistant = new AssistantMessage("done");
        assistant.setToolCalls(List.of(ToolCall.builder()
                .id("call-1")
                .type("function")
                .name("lookup")
                .arguments("{\"q\":\"x\"}")
                .index(7)
                .build()));

        String userPiece = "<|start|>user\nhello<|end|>";
        String assistantPiece = "<|start|>assistant\ndone<|end|>";
        String toolCallsJson = "[{\"id\": \"call-1\", \"type\": \"function\", "
                + "\"function\": {\"name\": \"lookup\", \"arguments\": \"{\\\"q\\\":\\\"x\\\"}\"}}]";

        int result = counter.countMessages(List.of(new BaseMessage("user", "hello"), assistant));

        assertEquals(userPiece.length() + assistantPiece.length() + toolCallsJson.length() + 3, result);
    }

    @Test
    void countMessagesReturnsZeroForEmptyInputs() {
        TiktokenCounter counter = new TiktokenCounter("gpt-4", String::length);

        assertEquals(0, counter.countMessages(null));
        assertEquals(0, counter.countMessages(List.of()));
    }

    @Test
    void countToolsUsesCompactJsonAndFunctionMessageFormat() {
        TiktokenCounter counter = new TiktokenCounter("gpt-4o", String::length);
        Map<String, Object> querySchema = new LinkedHashMap<>();
        querySchema.put("type", "string");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", querySchema);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        ToolInfo tool = ToolInfo.builder()
                .name("search")
                .description(null)
                .parameters(parameters)
                .build();

        String json = "{\"name\":\"search\",\"description\":\"\",\"parameters\":"
                + "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}}";
        String piece = "<|start|>functions.search:0\n" + json + "<|end|>";

        assertEquals(piece.length() + 3, counter.countTools(List.of(tool), "ignored", Map.of("unused", true)));
    }

    @Test
    void encoderFailureFallsBackToPythonLength() {
        TiktokenCounter counter = new TiktokenCounter("gpt-4", text -> {
            throw new IllegalArgumentException("encoding failed");
        });

        assertEquals(1, counter.count("😀😀😀😀"));
    }
}

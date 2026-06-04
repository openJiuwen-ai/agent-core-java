/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small HTTP response helpers for gateway tests.
 *
 * <p>Mirrors Python's {@code stream_chat_response} in
 * {@code openjiuwen.agent_evolving.agent_rl.online.gateway.app.http_helpers}.</p>
 */
public final class HttpHelpers {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpHelpers() {
    }

    public static List<String> streamChatResponse(Map<String, Object> responseJson, String modelId) {
        Map<String, Object> safeResponse = responseJson != null ? responseJson : Map.of();
        List<String> chunks = new ArrayList<>();
        chunks.add("data: " + toJson(firstChunk(safeResponse, modelId)) + "\n\n");
        chunks.add("data: " + toJson(usageChunk(safeResponse, modelId)) + "\n\n");
        chunks.add("data: [DONE]\n\n");
        return chunks;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstChunk(Map<String, Object> responseJson, String modelId) {
        Map<String, Object> chunk = new LinkedHashMap<>();
        chunk.put("id", responseJson.getOrDefault("id", "chatcmpl"));
        chunk.put("object", "chat.completion.chunk");
        chunk.put("created", responseJson.getOrDefault("created", 0));
        chunk.put("model", modelId != null ? modelId : responseJson.get("model"));
        if (responseJson.containsKey("prompt_token_ids")) {
            chunk.put("prompt_token_ids", responseJson.get("prompt_token_ids"));
        }

        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        Map<String, Object> delta = new LinkedHashMap<>();
        Object choicesObj = responseJson.get("choices");
        if (choicesObj instanceof List<?> choices && !choices.isEmpty() && choices.getFirst() instanceof Map<?, ?> first) {
            Object message = first.get("message");
            if (message instanceof Map<?, ?> messageMap) {
                Object role = messageMap.get("role");
                Object content = messageMap.get("content");
                delta.put("role", role != null ? role : "assistant");
                delta.put("content", content != null ? content : "");
            }
            if (first.containsKey("token_ids")) {
                choice.put("token_ids", first.get("token_ids"));
            }
            if (first.containsKey("logprobs")) {
                choice.put("logprobs", first.get("logprobs"));
            }
        }
        choice.put("delta", delta);
        choice.put("finish_reason", null);
        chunk.put("choices", List.of(choice));
        return chunk;
    }

    private static Map<String, Object> usageChunk(Map<String, Object> responseJson, String modelId) {
        Map<String, Object> chunk = new LinkedHashMap<>();
        chunk.put("id", responseJson.getOrDefault("id", "chatcmpl"));
        chunk.put("object", "chat.completion.chunk");
        chunk.put("created", responseJson.getOrDefault("created", 0));
        chunk.put("model", modelId != null ? modelId : responseJson.get("model"));
        chunk.put("choices", List.of(Map.of("index", 0, "delta", Map.of(), "finish_reason", "stop")));
        chunk.put("usage", responseJson.getOrDefault("usage", Map.of()));
        return chunk;
    }

    private static String toJson(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize streaming chunk", exception);
        }
    }
}

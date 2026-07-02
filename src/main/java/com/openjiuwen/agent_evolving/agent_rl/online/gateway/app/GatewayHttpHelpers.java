/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HTTP-facing helpers for gateway app layer.
 * <p>
 * Mirrors Python's module helpers in
 * {@code openjiuwen/agent_evolving/agent_rl/online/gateway/app/http_helpers.py}.
 */
public final class GatewayHttpHelpers {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private GatewayHttpHelpers() {
    }

    public static void ensureGatewayAuth(String gatewayApiKey, String authorization) {
        if (!pythonTruthy(gatewayApiKey)) {
            return;
        }
        if (authorization == null || !authorization.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            throw new GatewayHttpException(401, "missing bearer token");
        }
        String token = authorization.split(" ", 2)[1].trim();
        if (!token.equals(gatewayApiKey)) {
            throw new GatewayHttpException(403, "invalid bearer token");
        }
    }

    public static Map<String, String> buildUpstreamHeaders(Map<String, ?> requestHeaders, String llmApiKey) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (requestHeaders != null) {
            for (Map.Entry<String, ?> entry : requestHeaders.entrySet()) {
                String key = entry.getKey();
                if (key == null) {
                    continue;
                }
                String lowerKey = key.toLowerCase(Locale.ROOT);
                if ("host".equals(lowerKey) || "content-length".equals(lowerKey) || "connection".equals(lowerKey)) {
                    continue;
                }
                if (lowerKey.startsWith("x-forwarded-")) {
                    continue;
                }
                headers.put(key, entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
            }
        }
        if (pythonTruthy(llmApiKey)) {
            headers.put("Authorization", "Bearer " + llmApiKey);
        }
        return headers;
    }

    public static List<String> streamChatResponse(Map<String, Object> responseJson, String modelId) {
        Map<String, Object> safeResponseJson = responseJson != null ? responseJson : Map.of();
        int created = toIntOrDefault(safeResponseJson.get("created"), (int) (System.currentTimeMillis() / 1000L));
        String responseId = stringOrDefault(safeResponseJson.get("id"), "chatcmpl-gw-" + created);
        String model = stringOrDefault(safeResponseJson.get("model"), modelId);
        Object usage = safeResponseJson.get("usage");
        Object promptTokenIds = safeResponseJson.get("prompt_token_ids");

        Map<String, Object> choice = firstChoice(safeResponseJson.get("choices"));
        Map<String, Object> message = asMap(choice.get("message"));
        String finishReason = stringOrDefault(choice.get("finish_reason"), "stop");
        Object tokenIds = choice.get("token_ids");
        Object logprobs = choice.get("logprobs");

        Map<String, Object> delta = new LinkedHashMap<>();
        Object role = message.get("role");
        if (pythonTruthy(role)) {
            delta.put("role", role);
        }
        Object content = message.get("content");
        if (content instanceof String text && !text.isEmpty()) {
            delta.put("content", text);
        }
        Object toolCalls = message.get("tool_calls");
        if (pythonTruthy(toolCalls)) {
            delta.put("tool_calls", toolCalls);
        }
        Object reasoningContent = message.get("reasoning_content");
        if (pythonTruthy(reasoningContent)) {
            delta.put("reasoning_content", reasoningContent);
        }

        Map<String, Object> firstChoice = new LinkedHashMap<>();
        firstChoice.put("index", 0);
        firstChoice.put("delta", delta);
        firstChoice.put("finish_reason", null);
        firstChoice.put("token_ids", tokenIds);
        firstChoice.put("logprobs", logprobs);

        Map<String, Object> first = new LinkedHashMap<>();
        first.put("id", responseId);
        first.put("object", "chat.completion.chunk");
        first.put("created", created);
        first.put("model", model);
        first.put("choices", List.of(firstChoice));
        if (promptTokenIds != null) {
            first.put("prompt_token_ids", promptTokenIds);
        }

        Map<String, Object> lastChoice = new LinkedHashMap<>();
        lastChoice.put("index", 0);
        lastChoice.put("delta", Map.of());
        lastChoice.put("finish_reason", finishReason);

        Map<String, Object> last = new LinkedHashMap<>();
        last.put("id", responseId);
        last.put("object", "chat.completion.chunk");
        last.put("created", created);
        last.put("model", model);
        last.put("choices", List.of(lastChoice));
        if (usage != null) {
            last.put("usage", usage);
        }

        return List.of(
                "data: " + toPythonJson(first) + "\n\n",
                "data: " + toPythonJson(last) + "\n\n",
                "data: [DONE]\n\n"
        );
    }

    private static Map<String, Object> firstChoice(Object rawChoices) {
        if (!(rawChoices instanceof List<?> choices) || choices.isEmpty()) {
            return Map.of();
        }
        Object first = choices.get(0);
        return asMap(first);
    }

    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return normalized;
        }
        return Map.of();
    }

    private static int toIntOrDefault(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String stringOrDefault(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.isEmpty() ? fallback : text;
    }

    private static boolean pythonTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof CharSequence chars) {
            return !chars.isEmpty();
        }
        if (value instanceof Iterable<?> iterable) {
            return iterable.iterator().hasNext();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) > 0;
        }
        return true;
    }

    private static String toPythonJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String string) {
            return writeJsonString(string);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            List<String> entries = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                entries.add(
                        writeJsonString(String.valueOf(entry.getKey()))
                                + ": "
                                + toPythonJson(entry.getValue())
                );
            }
            return "{" + String.join(", ", entries) + "}";
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> items = new ArrayList<>();
            for (Object item : iterable) {
                items.add(toPythonJson(item));
            }
            return "[" + String.join(", ", items) + "]";
        }
        if (value.getClass().isArray()) {
            List<String> items = new ArrayList<>();
            for (int index = 0; index < Array.getLength(value); index++) {
                items.add(toPythonJson(Array.get(value, index)));
            }
            return "[" + String.join(", ", items) + "]";
        }
        return writeJsonString(String.valueOf(value));
    }

    private static String writeJsonString(String value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to JSON-encode SSE payload", exception);
        }
    }
}

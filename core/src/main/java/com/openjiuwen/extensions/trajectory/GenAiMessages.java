/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.trajectory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Serializes model messages into the GenAI structured-message JSON the
 * trajectory projector consumes.
 *
 * <p>Shape mirrors Python Agent Core
 * {@code OtelCallbackHandler._structured_message}: every message becomes
 * {@code {"role": ..., "parts": [...]}} with part types {@code text},
 * {@code reasoning}, {@code tool_call} and {@code tool_call_response}. The
 * serialized value is stored as the string value of the
 * {@code gen_ai.input.messages} / {@code gen_ai.output.messages} attributes.</p>
 *
 * @since 0.1.18
 */
public final class GenAiMessages {
    private static final Logger LOG = LoggerFactory.getLogger(GenAiMessages.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String PART_TYPE_TEXT = "text";
    private static final String PART_TYPE_REASONING = "reasoning";
    private static final String PART_TYPE_TOOL_CALL = "tool_call";
    private static final String PART_TYPE_TOOL_CALL_RESPONSE = "tool_call_response";

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    private GenAiMessages() {
    }

    /**
     * Serialize model-call input messages into the structured JSON string.
     *
     * @param messages raw message objects ({@link BaseMessage}, maps or strings)
     * @return JSON array text, never null
     */
    public static String serializeInput(List<Object> messages) {
        List<Map<String, Object>> structured = new ArrayList<>();
        if (messages != null) {
            for (Object message : messages) {
                structured.add(structuredInputMessage(message));
            }
        }
        return writeJson(structured);
    }

    /**
     * Serialize a model response into the structured output-message JSON.
     *
     * @param response the raw response object, typically {@link AssistantMessage}
     * @return JSON array text with one assistant message, never null
     */
    public static String serializeOutput(Object response) {
        if (response == null) {
            return writeJson(List.of());
        }
        return writeJson(List.of(structuredOutputMessage(response)));
    }

    private static Map<String, Object> structuredInputMessage(Object raw) {
        if (raw instanceof String text) {
            return message(ROLE_USER, List.of(textPart(text)));
        }
        if (raw instanceof Map<?, ?> map) {
            Object content = map.get("content");
            List<Map<String, Object>> parts = new ArrayList<>();
            if (content instanceof String text && !text.isEmpty()) {
                parts.add(textPart(text));
            }
            appendToolCalls(map.get("tool_calls"), parts);
            return message(stringField(map, "role").orElse(ROLE_USER), parts);
        }
        if (raw instanceof BaseMessage baseMessage) {
            return structuredBaseMessage(baseMessage, false);
        }
        return message(ROLE_USER, List.of(textPart(String.valueOf(raw))));
    }

    private static Map<String, Object> structuredOutputMessage(Object raw) {
        if (raw instanceof AssistantMessage assistantMessage) {
            return structuredBaseMessage(assistantMessage, true);
        }
        if (raw instanceof BaseMessage baseMessage) {
            return structuredBaseMessage(baseMessage, true);
        }
        if (raw instanceof Map<?, ?> map) {
            Object content = map.get("content");
            List<Map<String, Object>> parts = new ArrayList<>();
            if (content instanceof String text && !text.isEmpty()) {
                parts.add(textPart(text));
            }
            appendToolCalls(map.get("tool_calls"), parts);
            return message(stringField(map, "role").orElse(ROLE_ASSISTANT), parts);
        }
        if (raw instanceof String text) {
            return message(ROLE_ASSISTANT, List.of(textPart(text)));
        }
        return message(ROLE_ASSISTANT, List.of(textPart(String.valueOf(raw))));
    }

    private static Map<String, Object> structuredBaseMessage(BaseMessage baseMessage,
                                                             boolean isOutput) {
        String role = baseMessage.getRole() != null && !baseMessage.getRole().isEmpty()
                ? baseMessage.getRole().toLowerCase(Locale.ROOT)
                : (isOutput ? ROLE_ASSISTANT : ROLE_USER);
        List<Map<String, Object>> parts = new ArrayList<>();
        if (isOutput && baseMessage instanceof AssistantMessage assistantMessage
                && assistantMessage.getReasoningContent() != null
                && !assistantMessage.getReasoningContent().isEmpty()) {
            parts.add(reasoningPart(assistantMessage.getReasoningContent()));
        }
        String content = baseMessage.getContentAsString();
        if (content != null && !content.isEmpty()) {
            if ("tool".equals(role)) {
                parts.add(toolCallResponsePart(content, baseMessage));
            } else {
                parts.add(textPart(content));
            }
        }
        if (baseMessage instanceof AssistantMessage assistantMessage) {
            appendTypedToolCalls(assistantMessage.getToolCalls(), parts);
        }
        Map<String, Object> message = message(role, parts);
        if (baseMessage.getName() != null && !baseMessage.getName().isEmpty()) {
            message.put("name", baseMessage.getName());
        }
        return message;
    }

    private static void appendToolCalls(Object rawToolCalls, List<Map<String, Object>> parts) {
        if (!(rawToolCalls instanceof List<?> calls)) {
            return;
        }
        for (Object call : calls) {
            if (call instanceof Map<?, ?> map) {
                parts.add(toolCallPart(stringField(map, "id").orElse(null),
                        stringField(map, "name").orElse(null),
                        map.get("arguments") instanceof String arguments
                                ? arguments : String.valueOf(map.get("arguments"))));
            }
        }
    }

    private static void appendTypedToolCalls(List<ToolCall> toolCalls,
                                             List<Map<String, Object>> parts) {
        if (toolCalls == null) {
            return;
        }
        for (ToolCall call : toolCalls) {
            parts.add(toolCallPart(call.getId(), call.getName(), call.getArguments()));
        }
    }

    private static Map<String, Object> message(String role, List<Map<String, Object>> parts) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("parts", parts);
        return message;
    }

    private static Map<String, Object> textPart(String content) {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("type", PART_TYPE_TEXT);
        part.put("content", content);
        return part;
    }

    private static Map<String, Object> reasoningPart(String content) {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("type", PART_TYPE_REASONING);
        part.put("content", content);
        return part;
    }

    private static Map<String, Object> toolCallPart(String id, String name, String arguments) {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("type", PART_TYPE_TOOL_CALL);
        if (id != null && !id.isEmpty()) {
            part.put("id", id);
        }
        if (name != null && !name.isEmpty()) {
            part.put("name", name);
        }
        part.put("arguments", parseJsonIfPossible(arguments).orElse(null));
        return part;
    }

    private static Map<String, Object> toolCallResponsePart(String content,
                                                            BaseMessage baseMessage) {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("type", PART_TYPE_TOOL_CALL_RESPONSE);
        part.put("response", parseJsonIfPossible(content).orElse(null));
        Optional<String> toolCallId = baseMessage.getMetadata() == null
                ? Optional.empty()
                : stringField(baseMessage.getMetadata(), "tool_call_id");
        toolCallId.filter(id -> !id.isEmpty()).ifPresent(id -> part.put("id", id));
        if (baseMessage.getName() != null && !baseMessage.getName().isEmpty()) {
            part.put("name", baseMessage.getName());
        }
        return part;
    }

    private static Optional<Object> parseJsonIfPossible(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return Optional.of(MAPPER.readValue(trimmed, Object.class));
            } catch (JsonProcessingException ex) {
                LOG.debug("tool payload is not valid JSON; keeping raw string");
            }
        }
        return Optional.of(raw);
    }

    private static Optional<String> stringField(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof String text) {
            return Optional.of(text);
        }
        return Optional.empty();
    }

    private static String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            LOG.warn("failed to serialize GenAI messages: {}", ex.getMessage());
            return "[]";
        }
    }
}

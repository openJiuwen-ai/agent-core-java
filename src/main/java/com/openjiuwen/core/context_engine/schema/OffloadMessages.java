/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.schema;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mirrors Python's {@code create_offload_message} in
 * {@code openjiuwen/core/context_engine/schema/messages.py}.
 */
public final class OffloadMessages {
    private OffloadMessages() {
    }

    public static BaseMessage createOffloadMessage(String role, String content, String offloadHandle,
                                                   String offloadType) {
        return createOffloadMessage(role, content, offloadHandle, offloadType, Map.of());
    }

    public static BaseMessage createOffloadMessage(String role, String content, String offloadHandle,
                                                   String offloadType, Map<String, Object> kwargs) {
        String safeContent = Objects.requireNonNull(content, "content");
        String safeHandle = Objects.requireNonNull(offloadHandle, "offloadHandle");
        String safeType = Objects.requireNonNull(offloadType, "offloadType");
        Map<String, Object> safeKwargs = new LinkedHashMap<>(kwargs == null ? Map.of() : kwargs);
        safeKwargs.remove("role");
        safeKwargs.remove("content");

        if ("assistant".equals(role)) {
            OffloadAssistantMessage message = new OffloadAssistantMessage(safeContent, safeHandle, safeType);
            applyBaseKwargs(message, safeKwargs);
            applyAssistantKwargs(message, safeKwargs);
            return message;
        }
        if ("tool".equals(role)) {
            String toolCallId = requiredString(safeKwargs.get("tool_call_id"), "tool_call_id");
            OffloadToolMessage message = new OffloadToolMessage(safeContent, safeHandle, safeType, toolCallId);
            applyBaseKwargs(message, safeKwargs);
            return message;
        }
        if ("system".equals(role)) {
            OffloadSystemMessage message = new OffloadSystemMessage(safeContent, safeHandle, safeType);
            applyBaseKwargs(message, safeKwargs);
            return message;
        }
        OffloadUserMessage message = new OffloadUserMessage(safeContent, safeHandle, safeType);
        applyBaseKwargs(message, safeKwargs);
        return message;
    }

    static Map<String, Object> appendOffloadFields(Map<String, Object> result, OffloadMessage message) {
        result.put("offload_type", message.getOffloadType());
        result.put("offload_handle", message.getOffloadHandle());
        return result;
    }

    private static void applyBaseKwargs(BaseMessage message, Map<String, Object> kwargs) {
        if (kwargs.get("name") instanceof String name) {
            message.setName(name);
        }
        Object rawMetadata = kwargs.get("metadata");
        if (rawMetadata instanceof Map<?, ?> rawMap) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> metadata.put(String.valueOf(key), value));
            message.setMetadata(metadata);
        }
    }

    private static void applyAssistantKwargs(AssistantMessage message, Map<String, Object> kwargs) {
        Object rawToolCalls = kwargs.get("tool_calls");
        if (rawToolCalls instanceof List<?> toolCalls) {
            message.setToolCallsRaw(toolCalls);
        }
        if (kwargs.get("usage_metadata") instanceof UsageMetadata usageMetadata) {
            message.setUsageMetadata(usageMetadata);
        }
        if (kwargs.get("finish_reason") instanceof String finishReason) {
            message.setFinishReason(finishReason);
        }
        if (kwargs.containsKey("parser_content")) {
            message.setParserContent(kwargs.get("parser_content"));
        }
        if (kwargs.get("reasoning_content") instanceof String reasoningContent) {
            message.setReasoningContent(reasoningContent);
        }
        List<Integer> promptTokenIds = integerList(kwargs.get("prompt_token_ids"));
        if (promptTokenIds != null) {
            message.setPromptTokenIds(promptTokenIds);
        }
        List<Integer> completionTokenIds = integerList(kwargs.get("completion_token_ids"));
        if (completionTokenIds != null) {
            message.setCompletionTokenIds(completionTokenIds);
        }
        if (kwargs.containsKey("logprobs")) {
            message.setLogprobs(kwargs.get("logprobs"));
        }
    }

    private static String requiredString(Object value, String name) {
        if (value instanceof String text) {
            return text;
        }
        throw new IllegalArgumentException(name + " is required");
    }

    private static List<Integer> integerList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return null;
        }
        List<Integer> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Number number) {
                result.add(number.intValue());
            }
        }
        return result;
    }
}

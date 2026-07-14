/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.schema;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backward-compatible facade for the pre-0.1.14 context schema package.
 *
 * <p>Mirrors Python's {@code create_offload_message} in
 * {@code openjiuwen/core/context_engine/schema/messages.py}.</p>
 */
public final class OffloadMessages {
    private OffloadMessages() {
    }

    public static BaseMessage createOffloadMessage(String role, String content, String offloadHandle,
                                                   String offloadType) {
        return createOffloadMessage(role, content, offloadHandle, offloadType, Map.of());
    }

    public static BaseMessage createOffloadMessage(String role, String content, String offloadHandle,
                                                   String offloadType, Map<String, Object> extraFields) {
        Map<String, Object> safeFields = new LinkedHashMap<>(extraFields == null ? Map.of() : extraFields);
        safeFields.remove("role");
        safeFields.remove("content");
        if ("assistant".equals(role)) {
            OffloadAssistantMessage message = new OffloadAssistantMessage(content, offloadHandle, offloadType);
            applyBaseFields(message, safeFields);
            applyAssistantFields(message, safeFields);
            return message;
        }
        if ("tool".equals(role)) {
            String toolCallId = stringValue(safeFields.get("tool_call_id"));
            OffloadToolMessage message = new OffloadToolMessage(content, offloadHandle, offloadType, toolCallId);
            applyBaseFields(message, safeFields);
            return message;
        }
        if ("system".equals(role)) {
            OffloadSystemMessage message = new OffloadSystemMessage(content, offloadHandle, offloadType);
            applyBaseFields(message, safeFields);
            return message;
        }
        OffloadUserMessage message = new OffloadUserMessage(content, offloadHandle, offloadType);
        applyBaseFields(message, safeFields);
        return message;
    }

    private static void applyBaseFields(BaseMessage message, Map<String, Object> extraFields) {
        if (extraFields.get("name") instanceof String name) {
            message.setName(name);
        }
        Object rawMetadata = extraFields.get("metadata");
        if (rawMetadata instanceof Map<?, ?> rawMap) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> metadata.put(String.valueOf(key), value));
            message.setMetadata(metadata);
        }
    }

    private static void applyAssistantFields(AssistantMessage message, Map<String, Object> extraFields) {
        Object toolCalls = extraFields.get("tool_calls");
        if (toolCalls instanceof List<?> list) {
            message.setToolCallsRaw(list);
        }
        if (extraFields.get("usage_metadata") instanceof UsageMetadata usageMetadata) {
            message.setUsageMetadata(usageMetadata);
        }
        if (extraFields.get("finish_reason") instanceof String finishReason) {
            message.setFinishReason(finishReason);
        }
        if (extraFields.containsKey("parser_content")) {
            message.setParserContent(extraFields.get("parser_content"));
        }
        if (extraFields.get("reasoning_content") instanceof String reasoningContent) {
            message.setReasoningContent(reasoningContent);
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * Mirrors Python's {@code OffloadUserMessage} in
     * {@code openjiuwen/core/context_engine/schema/messages.py}.
     */
    public static class OffloadUserMessage
            extends com.openjiuwen.core.context_engine.schema.OffloadUserMessage {
        public OffloadUserMessage() {
        }

        public OffloadUserMessage(String content, String offloadHandle, String offloadType) {
            super(content, offloadHandle, offloadType);
        }

        public OffloadUserMessage(String offloadType, String offloadHandle, Map<String, Object> metadata) {
            setOffloadType(offloadType);
            setOffloadHandle(offloadHandle);
            setMetadata(metadata);
        }
    }

    /**
     * Mirrors Python's {@code OffloadAssistantMessage} in
     * {@code openjiuwen/core/context_engine/schema/messages.py}.
     */
    public static class OffloadAssistantMessage
            extends com.openjiuwen.core.context_engine.schema.OffloadAssistantMessage {
        public OffloadAssistantMessage() {
        }

        public OffloadAssistantMessage(String content, String offloadHandle, String offloadType) {
            super(content, offloadHandle, offloadType);
        }

        public OffloadAssistantMessage(String offloadType, String offloadHandle, Map<String, Object> metadata) {
            setOffloadType(offloadType);
            setOffloadHandle(offloadHandle);
            setMetadata(metadata);
        }
    }

    /**
     * Mirrors Python's {@code OffloadSystemMessage} in
     * {@code openjiuwen/core/context_engine/schema/messages.py}.
     */
    public static class OffloadSystemMessage
            extends com.openjiuwen.core.context_engine.schema.OffloadSystemMessage {
        public OffloadSystemMessage() {
        }

        public OffloadSystemMessage(String content, String offloadHandle, String offloadType) {
            super(content, offloadHandle, offloadType);
        }

        public OffloadSystemMessage(String offloadType, String offloadHandle, Map<String, Object> metadata) {
            setOffloadType(offloadType);
            setOffloadHandle(offloadHandle);
            setMetadata(metadata);
        }
    }

    /**
     * Mirrors Python's {@code OffloadToolMessage} in
     * {@code openjiuwen/core/context_engine/schema/messages.py}.
     */
    public static class OffloadToolMessage
            extends com.openjiuwen.core.context_engine.schema.OffloadToolMessage {
        public OffloadToolMessage() {
        }

        public OffloadToolMessage(String content, String offloadHandle, String offloadType, String toolCallId) {
            super(content, offloadHandle, offloadType, toolCallId);
        }

        public OffloadToolMessage(String offloadType, String offloadHandle, Map<String, Object> metadata) {
            setOffloadType(offloadType);
            setOffloadHandle(offloadHandle);
            setMetadata(metadata);
        }
    }
}

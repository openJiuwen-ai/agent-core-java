/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Input model for the Memory Write component.
 * <p>
 * Mirrors Python's {@code MemoryWriteInput}.
 * 
 * @since 0.1.7
 */
@Data
public class MemoryWriteInput {
    private List<BaseMessage> messages = new ArrayList<>();
    private OffsetDateTime timestamp;

    /**
     * Convert from a map representation to MemoryWriteInput.
     * 
     * @param inputs the input map
     * @return the MemoryWriteInput instance
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public static MemoryWriteInput fromMap(Map<String, Object> inputs) {
        MemoryWriteInput input = new MemoryWriteInput();
        if (inputs == null) {
            return input;
        }

        Object msgObj = inputs.get("messages");
        if (msgObj instanceof List<?> msgList) {
            List<BaseMessage> messages = parseMessagesList(msgList);
            input.setMessages(messages);
        }

        Object tsObj = inputs.get("timestamp");
        if (tsObj instanceof OffsetDateTime) {
            input.setTimestamp((OffsetDateTime) tsObj);
        }
        return input;
    }

    /**
     * parseMessagesList.
     * 
     * @param msgList msgList
     * @return the result
     * @since 0.1.7
     */
    private static List<BaseMessage> parseMessagesList(List<?> msgList) {
        List<BaseMessage> messages = new ArrayList<>();
        for (Object item : msgList) {
            if (item instanceof BaseMessage baseMsg) {
                messages.add(baseMsg);
            } else if (item instanceof Map<?, ?> msgMap) {
                BaseMessage message = parseMessageFromMap(msgMap);
                messages.add(message);
            } else {
                // no-op
            }
        }
        return messages;
    }

    @SuppressWarnings("unchecked")
    /**
     * parseMessageFromMap.
     * 
     * @param msgMap msgMap
     * @return the result
     * @since 0.1.7
     */
    private static BaseMessage parseMessageFromMap(Map<?, ?> msgMap) {
        Map<String, Object> typedMap = (Map<String, Object>) msgMap;
        String role = String.valueOf(typedMap.getOrDefault("role", ""));
        Object content = typedMap.get("content");
        String contentStr = content != null ? content.toString() : "";
        return new BaseMessage(role, contentStr);
    }
}

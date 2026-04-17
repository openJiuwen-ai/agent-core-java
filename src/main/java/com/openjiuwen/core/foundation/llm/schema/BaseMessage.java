/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * Base message class for LLM conversation messages.
 * <p>
 * Mirrors Python's {@code BaseMessage} model. Content can be a simple string
 * or a list of content parts (for multimodal messages).
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseMessage {

    /** Message role (system, user, assistant, tool). */
    private String role;

    /**
     * Message content — either a plain string or a list of content parts.
     * <p>
     * For simple text messages, use {@code String}. For multimodal messages,
     * use a {@code List} of maps containing text/image data.
     */
    private Object content;

    /** Optional name identifier for the message sender. */
    private String name;

    // ==================== Convenience Constructors ====================

    /**
     * Create a message with role and string content.
     */
    public BaseMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    /**
     * Get content as string. Returns empty string if content is not a string.
     */
    public String getContentAsString() {
        if (content instanceof String s) {
            return s;
        }
        return content != null ? content.toString() : "";
    }

    /**
     * Get content as list (for multimodal messages).
     */
    @SuppressWarnings("unchecked")
    public List<Object> getContentAsList() {
        if (content instanceof List<?> list) {
            return (List<Object>) list;
        }
        return null;
    }
}

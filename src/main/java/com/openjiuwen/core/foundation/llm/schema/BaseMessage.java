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
import java.util.ArrayList;

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

    public BaseMessage(String role, Object content, String name) {
        this.role = role;
        this.content = content;
        this.name = name;
    }

    public static BaseMessageBuilder builder() {
        return new BaseMessageBuilder();
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Object getContent() {
        return content;
    }

    public void setContent(Object content) {
        this.content = content;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public static class BaseMessageBuilder {
        private String role;
        private Object content;
        private String name;
        private List<ToolCall> toolCalls;
        private UsageMetadata usageMetadata;

        public BaseMessageBuilder role(String role) {
            this.role = role;
            return this;
        }

        public BaseMessageBuilder content(Object content) {
            this.content = content;
            return this;
        }

        public BaseMessageBuilder name(String name) {
            this.name = name;
            return this;
        }

        public BaseMessageBuilder toolCalls(List<ToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        public BaseMessageBuilder usageMetadata(UsageMetadata usageMetadata) {
            this.usageMetadata = usageMetadata;
            return this;
        }

        public BaseMessage build() {
            AssistantMessage message = new AssistantMessage();
            message.setRole(role);
            message.setContent(content);
            message.setName(name);
            if (toolCalls != null) {
                message.setToolCalls(new ArrayList<>(toolCalls));
            }
            message.setUsageMetadata(usageMetadata);
            return message;
        }
    }
}

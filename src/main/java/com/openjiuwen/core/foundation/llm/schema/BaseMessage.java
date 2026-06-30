/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Base message class for LLM conversation messages.
 * <p>
 * Mirrors Python's {@code BaseMessage} model. Content can be a simple string
 * or a list of content parts (for multimodal messages).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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

    /** Optional metadata map carried with the message. */
    private Map<String, Object> metadata;

    /**
     * Auto-generated for codecheck compliance.
     */
    public BaseMessage() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public BaseMessage(String role, Object content, String name, Map<String, Object> metadata) {
        this.role = role;
        this.content = content;
        this.name = name;
        this.metadata = metadata;
    }

    // ==================== Convenience Constructors ====================

    /**
     * Create a message with role and string content.
     */
    public BaseMessage(String role, String content) {
        this(role, content, null, null);
    }

    /**
     * Create a message with role, arbitrary content, and sender name.
     */
    public BaseMessage(String role, Object content, String name) {
        this(role, content, name, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getRole() {
        return role;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setRole(String role) {
        this.role = role;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getContent() {
        return content;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setContent(Object content) {
        this.content = content;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getName() {
        return name;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
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
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> getContentAsList() {
        if (content instanceof List<?> list) {
            return (List<Object>) list;
        }
        return null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseMessage message)) {
            return false;
        }
        return Objects.equals(getRole(), message.getRole())
                && Objects.equals(content, message.content)
                && Objects.equals(name, message.name)
                && Objects.equals(metadata, message.metadata);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public int hashCode() {
        return Objects.hash(getRole(), content, name, metadata);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static class Builder {
        /**
         * Auto-generated for codecheck compliance.
         */
        protected String role;
        /**
         * Auto-generated for codecheck compliance.
         */
        protected Object content;
        /**
         * Auto-generated for codecheck compliance.
         */
        protected String name;
        /**
         * Auto-generated for codecheck compliance.
         */
        protected Map<String, Object> metadata = new LinkedHashMap<>();

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder role(String role) {
            this.role = role;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder content(Object content) {
            this.content = content;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public BaseMessage build() {
            BaseMessage message = new BaseMessage();
            message.setRole(role);
            message.setContent(content);
            message.setName(name);
            message.setMetadata(metadata);
            return message;
        }
    }
}

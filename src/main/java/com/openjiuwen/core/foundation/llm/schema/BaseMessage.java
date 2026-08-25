/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonInclude;

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
 * 
 * @since 0.1.7
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
    private transient Object content;

    /** Optional name identifier for the message sender. */
    private String name;

    /** Optional metadata map carried with the message. */
    private transient Map<String, Object> metadata;

    /**
     * BaseMessage.
     * 
     * @since 0.1.7
     */
    public BaseMessage() {
    }

    /**
     * BaseMessage.
     * 
     * @param role role
     * @param content content
     * @param name name
     * @param metadata metadata
     * @since 0.1.7
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
     * 
     * @param role role
     * @param content content
     * @since 0.1.7
     */
    public BaseMessage(String role, String content) {
        this(role, content, null, null);
    }

    /**
     * Create a message with role, arbitrary content, and sender name.
     * 
     * @param role role
     * @param content content
     * @param name name
     * @since 0.1.7
     */
    public BaseMessage(String role, Object content, String name) {
        this(role, content, name, null);
    }

    /**
     * getRole.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getRole() {
        return role;
    }

    /**
     * setRole.
     * 
     * @param role role
     * @since 0.1.7
     */
    public void setRole(String role) {
        this.role = role;
    }

    /**
     * getContent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getContent() {
        return content;
    }

    /**
     * setContent.
     * 
     * @param content content
     * @since 0.1.7
     */
    public void setContent(Object content) {
        this.content = content;
    }

    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getName() {
        return name;
    }

    /**
     * setName.
     * 
     * @param name name
     * @since 0.1.7
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * getMetadata.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * setMetadata.
     * 
     * @param metadata metadata
     * @since 0.1.7
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    /**
     * Get content as string. Returns empty string if content is not a string.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getContentAsString() {
        if (content instanceof String s) {
            return s;
        }
        return content != null ? content.toString() : "";
    }

    /**
     * getContentAsList.
     * 
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public List<Object> getContentAsList() {
        if (content instanceof List<?> list) {
            return (List<Object>) list;
        }
        return java.util.Collections.emptyList();
    }

    /**
     * equals.
     * 
     * @param other other
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseMessage message)) {
            return false;
        }
        return Objects.equals(getRole(), message.getRole()) && Objects.equals(content, message.content)
                && Objects.equals(name, message.name) && Objects.equals(metadata, message.metadata);
    }

    /**
     * hashCode.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int hashCode() {
        return Objects.hash(getRole(), content, name, metadata);
    }

    /**
     * builder.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
     */
    public static class Builder {
        /**
         * role.
         * 
         * @since 0.1.7
         */
        protected String role;

        /**
         * content.
         * 
         * @since 0.1.7
         */
        protected Object content;

        /**
         * name.
         * 
         * @since 0.1.7
         */
        protected String name;

        /**
         * metadata.
         * 
         * @since 0.1.7
         */
        protected Map<String, Object> metadata = new LinkedHashMap<>();

        /**
         * role.
         * 
         * @param role role
         * @return the result
         * @since 0.1.7
         */
        public Builder role(String role) {
            this.role = role;
            return this;
        }

        /**
         * content.
         * 
         * @param content content
         * @return the result
         * @since 0.1.7
         */
        public Builder content(Object content) {
            this.content = content;
            return this;
        }

        /**
         * name.
         * 
         * @param name name
         * @return the result
         * @since 0.1.7
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * metadata.
         * 
         * @param metadata metadata
         * @return the result
         * @since 0.1.7
         */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
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

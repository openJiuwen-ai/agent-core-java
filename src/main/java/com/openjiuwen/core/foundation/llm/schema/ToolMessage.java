/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Tool response message in an LLM conversation.
 * <p>
 * Mirrors Python's {@code ToolMessage} model.
 * 
 * @since 0.1.7
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolMessage extends BaseMessage {
    @JsonProperty("tool_call_id")
    private String toolCallId;

    /**
     * ToolMessage.
     * 
     * @since 0.1.7
     */
    public ToolMessage() {
    }

    /**
     * ToolMessage.
     * 
     * @param role role
     * @param content content
     * @param name name
     * @param metadata metadata
     * @param toolCallId toolCallId
     * @since 0.1.7
     */
    public ToolMessage(String role, Object content, String name, java.util.Map<String, Object> metadata,
            String toolCallId) {
        super(role, content, name, metadata);
        this.toolCallId = toolCallId;
    }

    /**
     * Creates a tool message with the given content and tool call ID.
     * 
     * @param content the message content
     * @param toolCallId the ID of the tool call this message is responding to
     * @since 0.1.7
     */
    public ToolMessage(String content, String toolCallId) {
        super("tool", content);
        this.toolCallId = toolCallId;
    }

    /**
     * Creates a tool message with the given content, tool call ID, and name.
     * 
     * @param content the message content
     * @param toolCallId the ID of the tool call this message is responding to
     * @param name the sender name
     * @since 0.1.7
     */
    public ToolMessage(String content, String toolCallId, String name) {
        this(content, toolCallId);
        setName(name);
    }

    /**
     * getRole.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getRole() {
        String r = super.getRole();
        return r != null ? r : "tool";
    }

    /**
     * getToolCallId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getToolCallId() {
        return toolCallId;
    }

    /**
     * setToolCallId.
     * 
     * @param toolCallId toolCallId
     * @since 0.1.7
     */
    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
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
    public static class Builder extends BaseMessage.Builder {
        /**
         * toolCallId.
         * 
         * @since 0.1.7
         */
        protected String toolCallId;

        /**
         * role.
         * 
         * @param role role
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder role(String role) {
            super.role(role);
            return this;
        }

        /**
         * content.
         * 
         * @param content content
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder content(Object content) {
            super.content(content);
            return this;
        }

        /**
         * name.
         * 
         * @param name name
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder name(String name) {
            super.name(name);
            return this;
        }

        /**
         * metadata.
         * 
         * @param metadata metadata
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder metadata(java.util.Map<String, Object> metadata) {
            super.metadata(metadata);
            return this;
        }

        /**
         * toolCallId.
         * 
         * @param toolCallId toolCallId
         * @return the result
         * @since 0.1.7
         */
        public Builder toolCallId(String toolCallId) {
            this.toolCallId = toolCallId;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public ToolMessage build() {
            return new ToolMessage(role, content, name, metadata, toolCallId);
        }
    }
}

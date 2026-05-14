/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Tool response message in an LLM conversation.
 * <p>
 * Mirrors Python's {@code ToolMessage} model.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolMessage extends BaseMessage {

    /** The ID of the tool call this message is responding to. */
    @JsonProperty("tool_call_id")
    private String toolCallId;

    public ToolMessage() {
        super();
    }

    /**
     * Creates a tool message with the given content and tool call ID.
     *
     * @param content   the message content
     * @param toolCallId the ID of the tool call this message is responding to
     */
    public ToolMessage(String content, String toolCallId) {
        super("tool", content);
        this.toolCallId = toolCallId;
    }

    /**
     * Creates a tool message with the given content, tool call ID, and name.
     *
     * @param content   the message content
     * @param toolCallId the ID of the tool call this message is responding to
     * @param name      the sender name
     */
    public ToolMessage(String content, String toolCallId, String name) {
        this(content, toolCallId);
        setName(name);
    }

    public static ToolMessageBuilder builder() {
        return new ToolMessageBuilder();
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    @Override
    public String getRole() {
        String r = super.getRole();
        return r != null ? r : "tool";
    }

    public static class ToolMessageBuilder {
        private String role;
        private Object content;
        private String name;
        private String toolCallId;

        public ToolMessageBuilder role(String role) {
            this.role = role;
            return this;
        }

        public ToolMessageBuilder content(Object content) {
            this.content = content;
            return this;
        }

        public ToolMessageBuilder name(String name) {
            this.name = name;
            return this;
        }

        public ToolMessageBuilder toolCallId(String toolCallId) {
            this.toolCallId = toolCallId;
            return this;
        }

        public ToolMessage build() {
            ToolMessage message = new ToolMessage();
            message.setRole(role);
            message.setContent(content);
            message.setName(name);
            message.setToolCallId(toolCallId);
            return message;
        }
    }
}

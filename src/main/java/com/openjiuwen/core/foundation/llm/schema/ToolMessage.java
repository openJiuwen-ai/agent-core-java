/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
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

    public ToolMessage(String content, String toolCallId) {
        super("tool", content);
        this.toolCallId = toolCallId;
    }

    public ToolMessage(String content, String toolCallId, String name) {
        this(content, toolCallId);
        setName(name);
    }

    @Override
    public String getRole() {
        String r = super.getRole();
        return r != null ? r : "tool";
    }
}

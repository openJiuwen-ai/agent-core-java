/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Input data for before/after tool call events.
 *
 * <p>Mirrors Python's {@code ToolCallInputs} in
 * {@code openjiuwen/core/single_agent/rail/base.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolCallInputs implements EventInputs {
    @JsonProperty("tool_call")
    private Object toolCall;

    @JsonProperty("tool_name")
    private String toolName = "";

    @JsonProperty("tool_args")
    private Object toolArgs;

    @JsonProperty("tool_result")
    private Object toolResult;

    @JsonProperty("tool_msg")
    private Object toolMsg;

    public Object getToolCall() {
        return toolCall;
    }

    public void setToolCall(Object toolCall) {
        this.toolCall = toolCall;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName == null ? "" : toolName;
    }

    public Object getToolArgs() {
        return toolArgs;
    }

    public void setToolArgs(Object toolArgs) {
        this.toolArgs = toolArgs;
    }

    public Object getToolResult() {
        return toolResult;
    }

    public void setToolResult(Object toolResult) {
        this.toolResult = toolResult;
    }

    public Object getToolMsg() {
        return toolMsg;
    }

    public void setToolMsg(Object toolMsg) {
        this.toolMsg = toolMsg;
    }
}

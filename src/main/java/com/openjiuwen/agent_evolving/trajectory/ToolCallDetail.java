/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

import java.util.Map;

/**
 * Mirrors Python's openjiuwen.agent_evolving.trajectory.types.ToolCallDetail.
 * Complete tool call execution data.
 */
public class ToolCallDetail {

    private String toolName;
    private Object callArgs;
    private Object callResult;
    private String toolDescription;
    private Map<String, Object> toolSchema;
    private String toolCallId;

    public ToolCallDetail() {
    }

    public ToolCallDetail(String toolName, Object callArgs, Object callResult,
                          String toolDescription, Map<String, Object> toolSchema, String toolCallId) {
        this.toolName = toolName;
        this.callArgs = callArgs;
        this.callResult = callResult;
        this.toolDescription = toolDescription;
        this.toolSchema = toolSchema;
        this.toolCallId = toolCallId;
    }

    public static Builder builder() { return new Builder(); }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public Object getCallArgs() { return callArgs; }
    public void setCallArgs(Object callArgs) { this.callArgs = callArgs; }

    public Object getCallResult() { return callResult; }
    public void setCallResult(Object callResult) { this.callResult = callResult; }

    public String getToolDescription() { return toolDescription; }
    public void setToolDescription(String toolDescription) { this.toolDescription = toolDescription; }

    public Map<String, Object> getToolSchema() { return toolSchema; }
    public void setToolSchema(Map<String, Object> toolSchema) { this.toolSchema = toolSchema; }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    public static final class Builder {
        private String toolName;
        private Object callArgs;
        private Object callResult;
        private String toolDescription;
        private Map<String, Object> toolSchema;
        private String toolCallId;

        private Builder() {
        }

        public Builder toolName(String toolName) { this.toolName = toolName; return this; }
        public Builder callArgs(Object callArgs) { this.callArgs = callArgs; return this; }
        public Builder callResult(Object callResult) { this.callResult = callResult; return this; }
        public Builder toolDescription(String toolDescription) { this.toolDescription = toolDescription; return this; }
        public Builder toolSchema(Map<String, Object> toolSchema) { this.toolSchema = toolSchema; return this; }
        public Builder toolCallId(String toolCallId) { this.toolCallId = toolCallId; return this; }

        public ToolCallDetail build() {
            return new ToolCallDetail(toolName, callArgs, callResult, toolDescription, toolSchema, toolCallId);
        }
    }
}
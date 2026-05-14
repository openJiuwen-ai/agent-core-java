/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input data for BEFORE/AFTER_TOOL_CALL events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallInputs implements EventInputs {
    private ToolCall toolCall;
    @Builder.Default
    private String toolName = "";
    private Object toolArgs;
    private Object toolResult;
    private ToolMessage toolMsg;

    public ToolCallInputs() {
    }

    public ToolCallInputs(ToolCall toolCall,
                          String toolName,
                          Object toolArgs,
                          Object toolResult,
                          ToolMessage toolMsg) {
        this.toolCall = toolCall;
        this.toolName = toolName;
        this.toolArgs = toolArgs;
        this.toolResult = toolResult;
        this.toolMsg = toolMsg;
    }

    public static ToolCallInputsBuilder builder() {
        return new ToolCallInputsBuilder();
    }

    public ToolCall getToolCall() {
        return toolCall;
    }

    public void setToolCall(ToolCall toolCall) {
        this.toolCall = toolCall;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
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

    public ToolMessage getToolMsg() {
        return toolMsg;
    }

    public void setToolMsg(ToolMessage toolMsg) {
        this.toolMsg = toolMsg;
    }

    public static final class ToolCallInputsBuilder {
        private ToolCall toolCall;
        private String toolName = "";
        private Object toolArgs;
        private Object toolResult;
        private ToolMessage toolMsg;

        public ToolCallInputsBuilder toolCall(ToolCall toolCall) {
            this.toolCall = toolCall;
            return this;
        }

        public ToolCallInputsBuilder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public ToolCallInputsBuilder toolArgs(Object toolArgs) {
            this.toolArgs = toolArgs;
            return this;
        }

        public ToolCallInputsBuilder toolResult(Object toolResult) {
            this.toolResult = toolResult;
            return this;
        }

        public ToolCallInputsBuilder toolMsg(ToolMessage toolMsg) {
            this.toolMsg = toolMsg;
            return this;
        }

        public ToolCallInputs build() {
            return new ToolCallInputs(toolCall, toolName, toolArgs, toolResult, toolMsg);
        }
    }
}

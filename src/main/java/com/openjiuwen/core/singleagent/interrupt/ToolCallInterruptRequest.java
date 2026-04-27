/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.interrupt;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;

/**
 * Interrupt request enriched with tool-call metadata.
 *
 * @since 0.1.7
 */
public class ToolCallInterruptRequest extends InterruptRequest implements java.io.Serializable {
    private String toolCallId;
    private String toolName;

    /**
     * Return the interrupted tool call id.
     *
     * @return tool call id
     */
    public String getToolCallId() {
        return toolCallId;
    }

    /**
     * Set the interrupted tool call id.
     *
     * @param toolCallId tool call id
     */
    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    /**
     * Return the interrupted tool name.
     *
     * @return tool name
     */
    public String getToolName() {
        return toolName;
    }

    /**
     * Set the interrupted tool name.
     *
     * @param toolName tool name
     */
    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    /**
     * Build a tool-call-aware request from a plain interruption request.
     *
     * @param request original interruption request
     * @param toolCall tool call metadata
     * @return enriched interruption request
     */
    public static ToolCallInterruptRequest fromToolCall(InterruptRequest request, ToolCall toolCall) {
        ToolCallInterruptRequest result = new ToolCallInterruptRequest();
        if (request != null) {
            result.setInterruptId(request.getInterruptId());
            result.setMessage(request.getMessage());
            result.setContext(request.getContext());
            result.setPayloadSchema(request.getPayloadSchema());
            result.setAutoConfirmKey(request.getAutoConfirmKey());
        }
        if (toolCall != null) {
            result.setToolCallId(toolCall.getId());
            result.setToolName(toolCall.getName());
        }
        return result;
    }
}

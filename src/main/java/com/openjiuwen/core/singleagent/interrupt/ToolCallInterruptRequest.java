/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.interrupt;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;

import java.io.Serial;

/**
 * Interrupt request enriched with tool-call metadata.
 * 
 * @since 0.1.7
 */
public class ToolCallInterruptRequest extends InterruptRequest implements java.io.Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private String toolCallId;
    private String toolName;
    private transient java.util.List<java.util.Map<String, Object>> questions;

    /**
     * Return the interrupted tool call id.
     * 
     * @return tool call id
     * @since 0.1.7
     */
    public String getToolCallId() {
        return toolCallId;
    }

    /**
     * Set the interrupted tool call id.
     * 
     * @param toolCallId tool call id
     * @since 0.1.7
     */
    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    /**
     * Return the interrupted tool name.
     * 
     * @return tool name
     * @since 0.1.7
     */
    public String getToolName() {
        return toolName;
    }

    /**
     * Set the interrupted tool name.
     * 
     * @param toolName tool name
     * @since 0.1.7
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
     * @since 0.1.7
     */
    public static ToolCallInterruptRequest fromToolCall(InterruptRequest request, ToolCall toolCall) {
        ToolCallInterruptRequest result = new ToolCallInterruptRequest();
        if (request != null) {
            result.setInterruptId(request.getInterruptId());
            result.setMessage(request.getMessage());
            result.setContext(request.getContext());
            result.setPayloadSchema(request.getPayloadSchema());
            result.setAutoConfirmKey(request.getAutoConfirmKey());
            // Preserve AskUserRequest.questions (Python ToolCallInterruptRequest.model_dump parity)
            if (request instanceof AskUserRequest askUserRequest) {
                result.setQuestions(askUserRequest.getQuestions());
            }
        }
        if (toolCall != null) {
            result.setToolCallId(toolCall.getId());
            result.setToolName(toolCall.getName());
        }
        return result;
    }

    /**
     * Return structured ask-user questions when present.
     *
     * @return questions list or null
     * @since 0.1.14
     */
    public java.util.List<java.util.Map<String, Object>> getQuestions() {
        return questions;
    }

    /**
     * Set structured ask-user questions.
     *
     * @param questions questions to present
     * @since 0.1.14
     */
    public void setQuestions(java.util.List<java.util.Map<String, Object>> questions) {
        this.questions = questions;
    }
}

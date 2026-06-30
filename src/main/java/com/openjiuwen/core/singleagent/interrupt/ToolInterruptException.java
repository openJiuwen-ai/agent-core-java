/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.interrupt;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;

/**
 * Runtime exception used to signal a tool interruption inside the ReAct loop.
 *
 * @since 0.1.7
 */
public class ToolInterruptException extends RuntimeException {
    private final InterruptRequest request;
    private final ToolCall toolCall;

    /**
     * Create a tool interruption exception.
     *
     * @param request interruption request payload
     * @param toolCall interrupted tool call
     */
    public ToolInterruptException(InterruptRequest request, ToolCall toolCall) {
        super(request != null ? request.getMessage() : "Tool execution interrupted");
        this.request = request;
        this.toolCall = toolCall;
    }

    /**
     * Return the interruption request.
     *
     * @return interruption request
     */
    public InterruptRequest getRequest() {
        return request;
    }

    /**
     * Return the interrupted tool call.
     *
     * @return interrupted tool call
     */
    public ToolCall getToolCall() {
        return toolCall;
    }
}

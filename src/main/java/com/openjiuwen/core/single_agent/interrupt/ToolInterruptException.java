/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.interrupt;

import com.openjiuwen.core.session.interaction.AgentInterrupt;

import java.util.Optional;

/**
 * Exception raised when a tool needs user confirmation.
 *
 * <p>Attributes:</p>
 * <ul>
 *   <li>request: The interrupt request containing confirmation message and schema</li>
 *   <li>toolCall: The ToolCall object that was intercepted (optional, set by Rail)</li>
 * </ul>
 *
 * <p>Mirrors Python's {@code ToolInterruptException} in
 * {@code openjiuwen.core.single_agent.interrupt.exception}.</p>
 */
public class ToolInterruptException extends AgentInterrupt {

    private final InterruptRequest request;
    private Object toolCall;

    /**
     * Create ToolInterruptException.
     *
     * @param request the interrupt request containing confirmation message and schema
     */
    public ToolInterruptException(InterruptRequest request) {
        super(request.getMessage());
        this.request = request;
        this.toolCall = null;
    }

    /**
     * Create ToolInterruptException with tool call.
     *
     * @param request  the interrupt request
     * @param toolCall the ToolCall object that was intercepted
     */
    public ToolInterruptException(InterruptRequest request, Object toolCall) {
        super(request.getMessage());
        this.request = request;
        this.toolCall = toolCall;
    }

    /**
     * Get the interrupt request.
     *
     * @return the interrupt request
     */
    public InterruptRequest getRequest() {
        return request;
    }

    /**
     * Get the tool call that was intercepted.
     *
     * @return Optional containing the tool call, or empty if not set
     */
    public Optional<Object> getToolCall() {
        return Optional.ofNullable(toolCall);
    }

    /**
     * Set the tool call (used by Rail).
     *
     * @param toolCall the tool call to set
     */
    public void setToolCall(Object toolCall) {
        this.toolCall = toolCall;
    }
}
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.interrupt;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.interaction.AgentInterrupt;

import java.util.Objects;
import java.util.Optional;

/**
 * Exception raised when a tool requires user confirmation.
 *
 * <p>Mirrors Python's {@code ToolInterruptException} in
 * {@code openjiuwen/core/single_agent/interrupt/exception.py}.</p>
 */
public class ToolInterruptException extends AgentInterrupt {
    private final InterruptRequest request;
    private final ToolCall toolCall;

    public ToolInterruptException(InterruptRequest request) {
        this(request, null);
    }

    public ToolInterruptException(InterruptRequest request, ToolCall toolCall) {
        super(messageOf(request));
        this.request = request;
        this.toolCall = toolCall;
    }

    private static String messageOf(InterruptRequest request) {
        return String.valueOf(Objects.requireNonNull(request, "request").getMessage());
    }

    public InterruptRequest getRequest() {
        return request;
    }

    public Optional<ToolCall> getToolCall() {
        return Optional.ofNullable(toolCall);
    }
}

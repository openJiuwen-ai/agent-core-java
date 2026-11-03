/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.common.exception.AgentError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;

import java.util.Map;

/**
 * Exception wrapper for ability/tool execution failures.
 *
 * <p>Mirrors Python's {@code AbilityExecutionError} in
 * {@code openjiuwen/core/single_agent/ability_manager.py}.</p>
 */
public class AbilityExecutionError extends AgentError {
    private final ToolMessage toolMessage;

    public AbilityExecutionError(StatusCode status, String message, Object details,
                                 Throwable cause, ToolMessage toolMessage) {
        super(status, message, details, cause, Map.of());
        this.toolMessage = toolMessage;
    }

    public static AbilityExecutionError of(ToolCall toolCall, String message) {
        return of(toolCall, message, null);
    }

    public static AbilityExecutionError of(ToolCall toolCall, String message, Throwable cause) {
        String text = message == null ? "" : message;
        ToolMessage toolMessage = new ToolMessage(
                text,
                toolCall == null ? null : toolCall.getId(),
                toolCall == null ? null : toolCall.getName()
        );
        return new AbilityExecutionError(
                StatusCode.AGENT_TOOL_EXECUTION_ERROR,
                text,
                Map.of("error_msg", text),
                cause,
                toolMessage
        );
    }

    public ToolMessage getToolMessage() {
        return toolMessage;
    }
}

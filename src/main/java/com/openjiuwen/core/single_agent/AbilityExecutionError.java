/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent;

import com.openjiuwen.core.common.exception.AgentError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;

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
        super(status, message, details, cause, java.util.Map.of());
        this.toolMessage = toolMessage;
    }

    public ToolMessage getToolMessage() {
        return toolMessage;
    }
}

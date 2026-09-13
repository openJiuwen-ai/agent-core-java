/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.common.exception.AgentError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;

import java.util.Map;

/**
 * Unified exception for ability execution failures.
 * 
 * @since 0.1.7
 */
public class AbilityExecutionError extends AgentError {
    private final ToolMessage toolMessage;

    /**
     * AbilityExecutionError.
     * 
     * @param status status
     * @param msg msg
     * @param toolMessage toolMessage
     * @since 0.1.7
     */
    public AbilityExecutionError(StatusCode status, String msg, ToolMessage toolMessage) {
        super(status, msg, null, null, Map.of("error_msg", msg));
        this.toolMessage = toolMessage;
    }

    /**
     * AbilityExecutionError.
     * 
     * @param status status
     * @param msg msg
     * @param cause cause
     * @param toolMessage toolMessage
     * @since 0.1.7
     */
    public AbilityExecutionError(StatusCode status, String msg, Throwable cause, ToolMessage toolMessage) {
        super(status, msg, null, cause, Map.of("error_msg", msg));
        this.toolMessage = toolMessage;
    }

    /**
     * getToolMessage.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ToolMessage getToolMessage() {
        return toolMessage;
    }
}

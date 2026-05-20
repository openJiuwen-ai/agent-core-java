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
 */
public class AbilityExecutionError extends AgentError {

    private final ToolMessage toolMessage;

    /**
     * Auto-generated for codecheck compliance.
     */
    public AbilityExecutionError(StatusCode status, String msg, ToolMessage toolMessage) {
        super(status, msg, null, null, Map.of("error_msg", msg));
        this.toolMessage = toolMessage;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public AbilityExecutionError(StatusCode status, String msg, Throwable cause, ToolMessage toolMessage) {
        super(status, msg, null, cause, Map.of("error_msg", msg));
        this.toolMessage = toolMessage;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ToolMessage getToolMessage() {
        return toolMessage;
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.tool_call;

import com.openjiuwen.core.session.Session;

/**
 * Functional interface for executing a tool call.
 *
 * @since 0.1.7
 */
@FunctionalInterface
public interface ToolExecutor {

    /**
     * Execute a tool call.
     *
     * @param toolCall the tool call object
     * @param session  the session context
     * @return the execution result
     */
    ToolExecutionResult execute(Object toolCall, Session session);
}

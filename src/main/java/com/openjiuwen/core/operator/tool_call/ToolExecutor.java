/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.tool_call;

import com.openjiuwen.core.session.Session;

/**
 * Router-mode executor for tool call batches.
 * 
 * @since 0.1.7
 */
@FunctionalInterface
public interface ToolExecutor {
    /**
     * execute.
     * 
     * @param toolCall toolCall
     * @param session session
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    ToolExecutionResult execute(Object toolCall, Session session) throws Exception;
}

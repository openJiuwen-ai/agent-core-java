/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.operator.tool_call;

import com.openjiuwen.core.session.Session;

/**
 * Router-mode executor for tool call batches.
 */
@FunctionalInterface
public interface ToolExecutor {

    ToolExecutionResult execute(Object toolCall, Session session) throws Exception;
}

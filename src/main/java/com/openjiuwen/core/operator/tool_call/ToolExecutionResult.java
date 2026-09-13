/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.tool_call;

import com.openjiuwen.core.foundation.llm.schema.ToolMessage;

/**
 * Result wrapper for router-mode tool execution.
 * 
 * @since 0.1.7
 */
public record ToolExecutionResult(Object result, ToolMessage toolMessage) {
}

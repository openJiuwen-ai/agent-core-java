/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.tool_call;

import com.openjiuwen.core.foundation.llm.schema.ToolMessage;

/**
 * Result of a single tool execution.
 *
 * @param result      the raw execution result
 * @param toolMessage the tool message for LLM context
 * @since 0.1.7
 */
public record ToolExecutionResult(Object result, ToolMessage toolMessage) {
}

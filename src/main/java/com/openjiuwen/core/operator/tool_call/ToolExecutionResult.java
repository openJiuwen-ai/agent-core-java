/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.operator.tool_call;

import com.openjiuwen.core.foundation.llm.schema.ToolMessage;

/**
 * Result wrapper for router-mode tool execution.
 *
 * @param result tool execution payload
 * @param toolMessage tool response message emitted alongside the payload
 */
public record ToolExecutionResult(Object result, ToolMessage toolMessage) {
}

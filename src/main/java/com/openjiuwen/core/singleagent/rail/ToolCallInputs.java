/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input data for BEFORE/AFTER_TOOL_CALL events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallInputs implements EventInputs {
    private ToolCall toolCall;
    @Builder.Default
    private String toolName = "";
    private Object toolArgs;
    private Object toolResult;
    private ToolMessage toolMsg;
}

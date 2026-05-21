/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.rail;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data for BEFORE/AFTER_TOOL_CALL events.
 *
 * <p>Mirrors Python's {@code ToolCallInputs} in
 * {@code openjiuwen.core.single_agent.rail.base}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallInputs implements EventInputs {

    /** Tool call object. */
    private Object toolCall;

    /** Tool execution result. */
    private Object result;
}
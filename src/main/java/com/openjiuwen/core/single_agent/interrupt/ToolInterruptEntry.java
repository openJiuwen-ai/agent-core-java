/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.interrupt;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Entry for a tool interruption.
 *
 * <p>Mirrors Python's {@code ToolInterruptEntry} in
 * {@code openjiuwen.core.single_agent.interrupt.state}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolInterruptEntry {

    /** The tool call that was interrupted. */
    private ToolCall toolCall;

    /** Mapping of inner_id to interrupt request. */
    @Builder.Default
    private Map<String, InterruptRequest> interruptRequests = new HashMap<>();

    /** Whether this is a sub-agent interruption. */
    @Builder.Default
    private boolean isSubAgent = false;
}
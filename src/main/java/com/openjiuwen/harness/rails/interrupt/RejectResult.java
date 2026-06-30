/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import com.openjiuwen.core.foundation.llm.schema.ToolMessage;

/**
 * Skip tool execution and inject a synthetic tool result/message.
 *
 * @since 0.1.7
 */
public class RejectResult extends InterruptDecision {
    private final Object toolResult;
    private final ToolMessage toolMessage;

    /**
     * Create a rejection decision.
     *
     * @param toolResult synthetic tool result
     * @param toolMessage synthetic tool message
     */
    public RejectResult(Object toolResult, ToolMessage toolMessage) {
        this.toolResult = toolResult;
        this.toolMessage = toolMessage;
    }

    /**
     * Return the synthetic tool result.
     *
     * @return synthetic tool result
     */
    public Object getToolResult() {
        return toolResult;
    }

    /**
     * Return the synthetic tool message.
     *
     * @return synthetic tool message
     */
    public ToolMessage getToolMessage() {
        return toolMessage;
    }
}

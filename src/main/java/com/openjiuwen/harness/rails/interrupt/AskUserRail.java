/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;

import java.util.List;

/**
 * Public class AskUserRail used by the Java parity implementation.
 *
 * @since 1.0
 */
public class AskUserRail extends BaseInterruptRail {
    /**
     * Auto-generated for codecheck compliance.
     */
    public AskUserRail() {
        super(List.of("ask_user"));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    protected InterruptDecision resolveInterrupt(AgentCallbackContext ctx, ToolCall toolCall, Object userInput) {
        if (userInput != null) {
            return approve();
        }
        return interrupt(InterruptRequest.builder()
                .message("ask_user")
                .build());
    }
}

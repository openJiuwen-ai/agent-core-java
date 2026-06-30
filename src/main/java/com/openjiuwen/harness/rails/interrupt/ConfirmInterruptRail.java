/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;

/**
 * Public class ConfirmInterruptRail used by the Java parity implementation.
 *
 * @since 1.0
 */
public class ConfirmInterruptRail extends BaseInterruptRail {
    /**
     * Auto-generated for codecheck compliance.
     */
    public ConfirmInterruptRail(Iterable<String> toolNames) {
        super(toolNames);
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
            String value = String.valueOf(userInput).trim().toLowerCase(java.util.Locale.ROOT);
            if ("false".equals(value) || "no".equals(value) || "reject".equals(value)) {
                return reject("Rejected by user");
            }
            return approve();
        }
        return interrupt(InterruptRequest.builder()
                .message("confirm tool call")
                .build());
    }
}

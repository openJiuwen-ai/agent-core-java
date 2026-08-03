/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import com.openjiuwen.core.foundation.llm.schema.ToolMessage;

/**
 * Decision to reject tool execution.
 *
 * <p>Mirrors Python's {@code RejectResult} in
 * {@code openjiuwen/harness/rails/interrupt/interrupt_base.py}.</p>
 *
 * @param toolResult result to surface instead of executing the tool
 * @param toolMessage optional tool message override
 */
public record RejectResult(Object toolResult, ToolMessage toolMessage) implements InterruptDecision {

    public RejectResult() {
        this(null, null);
    }

    public RejectResult(Object toolResult) {
        this(toolResult, null);
    }
}

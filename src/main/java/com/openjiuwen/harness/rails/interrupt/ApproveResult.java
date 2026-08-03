/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

/**
 * Decision to continue tool execution.
 *
 * <p>Mirrors Python's {@code ApproveResult} in
 * {@code openjiuwen/harness/rails/interrupt/interrupt_base.py}.</p>
 *
 * @param newArgs optional replacement tool arguments
 */
public record ApproveResult(String newArgs) implements InterruptDecision {

    public ApproveResult() {
        this(null);
    }
}

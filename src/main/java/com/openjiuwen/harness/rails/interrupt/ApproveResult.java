/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

/**
 * Continue tool execution, optionally overriding tool arguments.
 * 
 * @since 0.1.7
 */
public class ApproveResult extends InterruptDecision {
    private final String newArgs;

    /**
     * Create an approval decision.
     * 
     * @param newArgs optional replacement tool arguments
     * @since 0.1.7
     */
    public ApproveResult(String newArgs) {
        this.newArgs = newArgs;
    }

    /**
     * Return replacement tool arguments.
     * 
     * @return replacement arguments or null
     * @since 0.1.7
     */
    public String getNewArgs() {
        return newArgs;
    }
}

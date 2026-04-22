/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;

/**
 * Interrupt tool execution and wait for user input.
 *
 * @since 0.1.7
 */
public class InterruptResult extends InterruptDecision {
    private final InterruptRequest request;

    /**
     * Create an interrupt decision.
     *
     * @param request interruption request
     */
    public InterruptResult(InterruptRequest request) {
        this.request = request;
    }

    /**
     * Return the interruption request payload.
     *
     * @return interruption request
     */
    public InterruptRequest getRequest() {
        return request;
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.condition;

import com.openjiuwen.core.session.BaseSession;

import java.util.function.BooleanSupplier;

/**
 * Condition that wraps a callable predicate.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.condition.condition.FuncCondition}.
 * 
 * @since 0.1.7
 */
public class FuncCondition extends Condition {
    private final BooleanSupplier func;

    /**
     * FuncCondition.
     * 
     * @param func func
     * @since 0.1.7
     */
    public FuncCondition(BooleanSupplier func) {
        super();
        this.func = func;
    }

    /**
     * doInvoke.
     * 
     * @param inputs inputs
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object doInvoke(Object inputs, BaseSession session) {
        return func.getAsBoolean();
    }

    /**
     * traceInfo.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object traceInfo(BaseSession session) {
        return func.toString();
    }
}

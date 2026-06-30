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
 */
public class FuncCondition extends Condition {

    private final BooleanSupplier func;

    /**
     * Auto-generated for codecheck compliance.
     */
    public FuncCondition(BooleanSupplier func) {
        super();
        this.func = func;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object doInvoke(Object inputs, BaseSession session) {
        return func.getAsBoolean();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object traceInfo(BaseSession session) {
        return func.toString();
    }
}

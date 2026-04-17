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

    public FuncCondition(BooleanSupplier func) {
        super();
        this.func = func;
    }

    @Override
    public Object doInvoke(Object inputs, BaseSession session) {
        return func.getAsBoolean();
    }

    @Override
    public Object traceInfo(BaseSession session) {
        return func.toString();
    }
}

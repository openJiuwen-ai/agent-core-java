/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow.condition;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.BaseSession;

/**
 * Loop condition based on iteration count with limit stored directly (not from schema).
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.condition.number.NumberConditionInSession}.
 */
public class NumberConditionInSession extends Condition {

    private final int limit;

    public NumberConditionInSession(int limit) {
        super();
        this.limit = limit;
    }

    @Override
    public Object doInvoke(Object inputs, BaseSession session) {
        Object currentIdxObj = session.state().get(Constant.INDEX);
        int currentIdx = (currentIdxObj instanceof Number) ? ((Number) currentIdxObj).intValue() : 0;
        return currentIdx < limit;
    }
}

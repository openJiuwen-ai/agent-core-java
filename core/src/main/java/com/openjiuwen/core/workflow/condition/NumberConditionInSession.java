/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.condition;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.BaseSession;

/**
 * Loop condition based on iteration count with limit stored directly (not from schema).
 * <p>
 * Mirrors Python's {@code NumberConditionInSession} in
 * {@code openjiuwen/core/workflow/components/condition/number.py}.
 */
public class NumberConditionInSession extends Condition {

    private final Integer limit;

    public NumberConditionInSession(Integer limit) {
        super();
        this.limit = limit;
    }

    @Override
    public Object doInvoke(Object inputs, BaseSession session) {
        Object currentIdxObj = stateValue(session, Constant.INDEX);
        int currentIdx = requireNumber(currentIdxObj, "index").intValue();
        if (limit == null) {
            throw new IllegalArgumentException("loop_number variable not found or is None");
        }
        return currentIdx < limit;
    }

    private static Number requireNumber(Object value, String name) {
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalArgumentException(name + " must be numeric");
    }
}

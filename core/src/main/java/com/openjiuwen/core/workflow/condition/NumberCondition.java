/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.condition;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.BaseSession;

/**
 * Loop condition based on iteration count, resolving limit from input schema.
 * <p>
 * Mirrors Python's {@code NumberCondition} in
 * {@code openjiuwen/core/workflow/components/condition/number.py}.
 */
public class NumberCondition extends Condition {

    private final Object limit;

    public NumberCondition(Object limit) {
        super(limit);
        this.limit = limit;
    }

    @Override
    public Object doInvoke(Object inputs, BaseSession session) {
        Object currentIdxObj = stateValue(session, Constant.INDEX);
        int currentIdx = requireNumber(currentIdxObj, "index").intValue();
        int limitNum = requireNumber(inputs, "limit").intValue();
        return currentIdx < limitNum;
    }

    private static Number requireNumber(Object value, String name) {
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalArgumentException(name + " must be numeric");
    }
}

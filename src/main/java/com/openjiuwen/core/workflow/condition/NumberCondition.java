/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow.condition;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.BaseSession;

/**
 * Loop condition based on iteration count, resolving limit from input schema.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.condition.number.NumberCondition}.
 */
public class NumberCondition extends Condition {

    private final Object limit;

    public NumberCondition(Object limit) {
        super(limit);
        this.limit = limit;
    }

    @Override
    public Object doInvoke(Object inputs, BaseSession session) {
        Object currentIdxObj = session.state().get(Constant.INDEX);
        int currentIdx = (currentIdxObj instanceof Number) ? ((Number) currentIdxObj).intValue() : 0;
        int limitNum;
        if (inputs instanceof Number) {
            limitNum = ((Number) inputs).intValue();
        } else {
            limitNum = 0;
        }
        return currentIdx < limitNum;
    }
}

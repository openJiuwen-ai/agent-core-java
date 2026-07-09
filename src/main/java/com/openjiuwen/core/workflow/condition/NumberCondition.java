/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.condition;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.BaseSession;

/**
 * Loop condition based on iteration count, resolving limit from input schema.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.condition.number.NumberCondition}.
 * 
 * @since 0.1.7
 */
public class NumberCondition extends Condition {
    private final Object limit;

    /**
     * NumberCondition.
     * 
     * @param limit limit
     * @since 0.1.7
     */
    public NumberCondition(Object limit) {
        super(limit);
        this.limit = limit;
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

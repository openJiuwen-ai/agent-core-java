/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.condition;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.BaseSession;

/**
 * Loop condition based on iteration count with limit stored directly (not from schema).
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.condition.number.NumberConditionInSession}.
 * 
 * @since 0.1.7
 */
public class NumberConditionInSession extends Condition {
    private final int limit;

    /**
     * NumberConditionInSession.
     * 
     * @param limit limit
     * @since 0.1.7
     */
    public NumberConditionInSession(int limit) {
        super();
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
        return currentIdx < limit;
    }
}

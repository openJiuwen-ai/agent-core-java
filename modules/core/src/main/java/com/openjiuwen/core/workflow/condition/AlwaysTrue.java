/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.condition;

import com.openjiuwen.core.session.BaseSession;

/**
 * Condition that always evaluates to true.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.condition.condition.AlwaysTrue}.
 * 
 * @since 0.1.7
 */
public class AlwaysTrue extends Condition {
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
        return true;
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
        return "True";
    }
}

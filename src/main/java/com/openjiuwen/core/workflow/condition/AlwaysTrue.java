/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.condition;

import com.openjiuwen.core.session.BaseSession;

/**
 * Condition that always evaluates to true.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.condition.condition.AlwaysTrue}.
 */
public class AlwaysTrue extends Condition {

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object doInvoke(Object inputs, BaseSession session) {
        return true;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object traceInfo(BaseSession session) {
        return "True";
    }
}

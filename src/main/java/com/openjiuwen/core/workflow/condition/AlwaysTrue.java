/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
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
    public Object doInvoke(Object inputs, BaseSession session) {
        return true;
    }

    @Override
    public Object traceInfo(BaseSession session) {
        return "True";
    }
}

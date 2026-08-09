/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.WorkflowComponent;

/**
 * Entry point component that passes inputs through as-is.
 * <p>
 * Mirrors Python's {@code Start} in
 * {@code openjiuwen/core/workflow/components/flow/start_comp.py}.
 */
public class Start extends WorkflowComponent<Object, Object> {

    @Override
    public Object invoke(Object inputs, BaseSession session, ModelContext context) {
        return inputs;
    }

    public Object invoke(Object inputs, NodeSessionApi session, com.openjiuwen.core.context.ModelContext context) {
        return invoke(inputs, session.getInner(), context == null ? null : context.unwrap());
    }
}

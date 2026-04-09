/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.WorkflowComponent;

/**
 * Entry point component that passes inputs through as-is.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.start_comp.Start}.
 */
public class Start extends WorkflowComponent {

    @Override
    public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
        return inputs;
    }
}

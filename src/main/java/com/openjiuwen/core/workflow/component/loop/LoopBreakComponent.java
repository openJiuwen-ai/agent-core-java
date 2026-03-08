/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow.component.loop;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.WorkflowComponent;

import java.util.HashMap;

/**
 * Component that breaks out of the current loop when invoked.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.loop.loop_comp.LoopBreakComponent}.
 */
public class LoopBreakComponent extends WorkflowComponent {

    private LoopController loopController;

    public void setController(LoopController loopController) {
        this.loopController = loopController;
    }

    @Override
    public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
        if (loopController == null) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LOOP_BREAK_EXECUTION_ERROR,
                    "reason", "failed to initialize loop controller",
                    "comp", session.getComponentId());
        }
        loopController.breakLoop();
        return new HashMap<>();
    }
}

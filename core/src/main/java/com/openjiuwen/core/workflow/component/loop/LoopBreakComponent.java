/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.internal.WorkflowSessionSupport;

import java.util.LinkedHashMap;

/**
 * Component that delegates break requests to the active loop controller.
 *
 * <p>Mirrors Python's {@code LoopBreakComponent} in
 * {@code openjiuwen/core/workflow/components/flow/loop/loop_comp.py}.</p>
 */
public class LoopBreakComponent extends WorkflowComponent<Object, Object> {

    private LoopController loopController;

    public void setController(LoopController loopController) {
        this.loopController = loopController;
    }

    public LoopController getController() {
        return loopController;
    }

    public void breakLoop() {
        if (loopController == null) {
            throw new IllegalStateException("failed to initialize loop controller");
        }
        loopController.breakLoop();
    }

    @Override
    public Object invoke(Object inputs, BaseSession session, ModelContext context) {
        if (loopController == null) {
            if (!LoopRuntime.requestBreak(session)) {
                String componentId = WorkflowSessionSupport.componentId(session);
                throw ErrorHelper.buildError(StatusCode.COMPONENT_LOOP_BREAK_EXECUTION_ERROR,
                        "reason", "failed to initialize loop controller",
                        "comp", componentId);
            }
        } else {
            loopController.breakLoop();
        }
        return new LinkedHashMap<String, Object>();
    }

    @Override
    public Object invoke(Object inputs, NodeSessionApi session, com.openjiuwen.core.context.ModelContext context) {
        return invoke(inputs, session.getInner(), context == null ? null : context.unwrap());
    }

    /**
     * Python-compatible snake_case bridge for reflected callers.
     *
     * @return executable loop-break component
     */
    public Executable<?, ?> to_executable() {
        return toExecutable();
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop;

import com.openjiuwen.core.workflow.component.WorkflowComponent;

/**
 * Component that delegates break requests to the active loop controller.
 *
 * <p>Mirrors Python's {@code LoopBreakComponent} in
 * {@code openjiuwen/core/workflow/components/flow/loop/loop_comp.py}.</p>
 */
public class LoopBreakComponent extends WorkflowComponent {

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
}

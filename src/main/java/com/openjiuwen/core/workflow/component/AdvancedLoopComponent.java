/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.workflow.ComponentComposable;
import com.openjiuwen.core.workflow.HasDrawable;

/**
 * Interface for advanced loop components that contain a body subgraph.
 *
 * <p>Stub interface for the graph visualization module. Will be fully implemented
 * when the workflow module is converted from Python.</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.core.workflow.components.flow.loop.loop_comp.AdvancedLoopComponent}.</p>
 */
public interface AdvancedLoopComponent extends ComponentComposable {

    /**
     * Gets the loop body (inner graph).
     *
     * @return the has-drawable body
     */
    HasDrawable getBody();
}

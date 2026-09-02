/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.workflow.ComponentComposable;
import com.openjiuwen.core.workflow.HasDrawable;
import com.openjiuwen.core.workflow.component.loop.callback.LoopCallback;

/**
 * Interface for advanced loop components that contain a body subgraph.
 * <p>
 * Stub interface for the graph visualization module. Will be fully implemented
 * when the workflow module is converted from Python.
 * </p>
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.loop.loop_comp.AdvancedLoopComponent}.
 * </p>
 * 
 * @since 0.1.7
 */
public interface AdvancedLoopComponent extends ComponentComposable {
    /**
     * getBody.
     * 
     * @return the result
     * @since 0.1.7
     */
    HasDrawable getBody();

    /**
     * Register a loop callback after construction.
     * 
     * @param callback callback
     * @since 0.1.7
     */
    void registerCallback(LoopCallback callback);
}

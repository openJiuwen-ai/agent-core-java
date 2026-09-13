/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.workflow.ComponentComposable;
import com.openjiuwen.core.workflow.HasDrawable;

/**
 * Interface for loop components that contain a repeatable subgraph.
 * <p>
 * Stub interface for the graph visualization module. Will be fully implemented
 * when the workflow module is converted from Python.
 * </p>
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.LoopComponent}.
 * </p>
 * 
 * @since 0.1.7
 */
public interface LoopComponent extends ComponentComposable {
    /**
     * getLoopGroup.
     * 
     * @return the result
     * @since 0.1.7
     */
    HasDrawable getLoopGroup();
}

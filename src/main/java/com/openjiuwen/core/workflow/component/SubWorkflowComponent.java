/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.workflow.ComponentComposable;
import com.openjiuwen.core.workflow.HasDrawable;

/**
 * Interface for sub-workflow components that wrap an inner workflow graph.
 *
 * <p>Stub interface for the graph visualization module. Will be fully implemented
 * when the workflow module is converted from Python.</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.core.workflow.components.flow.workflow_comp.SubWorkflowComponent}.</p>
 */
public interface SubWorkflowComponent extends ComponentComposable {

    /**
     * Gets the internal drawable of the sub-workflow.
     *
     * <p>In Python: {@code component.sub_workflow._internal.drawable}</p>
     *
     * @return the has-drawable internal representation
     */
    HasDrawable getSubWorkflowInternal();
}

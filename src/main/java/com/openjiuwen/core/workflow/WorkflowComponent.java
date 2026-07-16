/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.graph.Graph;

/**
 * Standard implementation combining both execution and graph construction.
 * This is the most common base class for user-defined workflow components.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.component.WorkflowComponent}.
 * 
 * @since 0.1.7
 */
public abstract class WorkflowComponent extends ComponentExecutable implements ComponentComposable {
    /**
     * addComponent.
     * 
     * @param graph graph
     * @param nodeId nodeId
     * @param waitForAll waitForAll
     * @since 0.1.7
     */
    @Override
    public void addComponent(Graph graph, String nodeId, boolean waitForAll) {
        graph.addNode(nodeId, this, waitForAll);
    }
}

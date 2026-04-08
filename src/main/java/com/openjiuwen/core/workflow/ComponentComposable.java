/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.graph.Graph;

/**
 * Interface for workflow graph construction.
 * Separates graph construction logic from execution logic (ComponentExecutable).
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.component.ComponentComposable}.
 */
public interface ComponentComposable {

    /**
     * Add this component to a workflow graph.
     *
     * @param graph      the workflow graph to add this component to
     * @param nodeId     unique identifier for this component node
     * @param waitForAll if true, wait for all predecessor outputs before execution
     */
    default void addComponent(Graph graph, String nodeId, boolean waitForAll) {
        graph.addNode(nodeId, toExecutable(), waitForAll);
    }

    /**
     * Convert this composable component to an executable instance.
     *
     * @return an Executable instance
     */
    default Executable<?, ?> toExecutable() {
        if (this instanceof Executable) {
            return (Executable<?, ?>) this;
        }
        throw new UnsupportedOperationException(
                "Component '" + getClass().getSimpleName() + "' does not implement toExecutable().");
    }
}

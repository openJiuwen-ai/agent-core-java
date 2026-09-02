/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.graph.Graph;

/**
 * Interface for workflow graph construction.
 * Separates graph construction logic from execution logic (ComponentExecutable).
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.component.ComponentComposable}.
 * 
 * @since 0.1.7
 */
public interface ComponentComposable {
    /**
     * addComponent.
     * 
     * @param graph graph
     * @param nodeId nodeId
     * @param waitForAll waitForAll
     * @since 0.1.7
     */
    default void addComponent(Graph graph, String nodeId, boolean waitForAll) {
        graph.addNode(nodeId, toExecutable(), waitForAll);
    }

    /**
     * Convert this composable component to an executable instance.
     * 
     * @return an Executable instance
     * @since 0.1.7
     */
    default Executable<?, ?> toExecutable() {
        if (this instanceof Executable) {
            return (Executable<?, ?>) this;
        }
        throw new UnsupportedOperationException(
                "Component '" + getClass().getSimpleName() + "' does not implement toExecutable().");
    }
}

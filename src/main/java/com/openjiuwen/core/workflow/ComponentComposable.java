/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.graph.Graph;

/**
 * Mirrors Python's {@code ComponentComposable} in
 * {@code openjiuwen/core/workflow/components/component.py}.
 */
public interface ComponentComposable {

    /**
     * Add this component to a workflow graph.
     *
     * @param graph workflow graph
     * @param nodeId component node id
     * @param waitForAll whether the graph waits for all predecessor outputs
     */
    default void addComponent(Graph graph, String nodeId, boolean waitForAll) {
        graph.addNode(nodeId, toExecutable(), waitForAll);
    }

    /**
     * Add this component to a workflow graph with Python's default
     * {@code wait_for_all=False}.
     *
     * @param graph workflow graph
     * @param nodeId component node id
     */
    default void addComponent(Graph graph, String nodeId) {
        addComponent(graph, nodeId, false);
    }

    /**
     * Convert this workflow component to an executable instance.
     *
     * @return executable component
     */
    default Executable<?, ?> toExecutable() {
        if (this instanceof Executable<?, ?> executable) {
            return executable;
        }
        String className = getClass().getSimpleName();
        throw new UnsupportedOperationException(
                "Component '" + className + "' is missing required method: to_executable()\n"
                        + "  -> Expected signature: def to_executable(self) -> Executable");
    }
}

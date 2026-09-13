/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.graph.Graph;
import com.openjiuwen.core.workflow.ComponentComposable;

/**
 * Memory Retrieval workflow component (composable wrapper).
 * <p>
 * Creates a {@link MemoryRetrievalExecutable} for graph execution.
 * <p>
 * Mirrors Python's {@code MemoryRetrievalComponent}.
 * 
 * @since 0.1.7
 */
public class MemoryRetrievalComponent implements ComponentComposable {
    private final MemoryRetrievalCompConfig config;

    /**
     * Create a MemoryRetrievalComponent with the given configuration.
     * 
     * @param config the component configuration
     * @since 0.1.7
     */
    public MemoryRetrievalComponent(MemoryRetrievalCompConfig config) {
        this.config = config;
    }

    /**
     * addComponent.
     * 
     * @param graph graph
     * @param nodeId nodeId
     * @param isWaitForAll isWaitForAll
     * @since 0.1.7
     */
    @Override
    public void addComponent(Graph graph, String nodeId, boolean isWaitForAll) {
        graph.addNode(nodeId, toExecutable(), isWaitForAll);
    }

    /**
     * toExecutable.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Executable<?, ?> toExecutable() {
        return new MemoryRetrievalExecutable(config);
    }
}

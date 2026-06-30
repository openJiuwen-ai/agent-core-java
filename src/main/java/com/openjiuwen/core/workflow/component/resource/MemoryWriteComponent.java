/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.graph.Graph;
import com.openjiuwen.core.workflow.ComponentComposable;

/**
 * Memory Write workflow component (composable wrapper).
 * <p>
 * Creates a {@link MemoryWriteExecutable} for graph execution.
 * <p>
 * Mirrors Python's {@code MemoryWriteComponent}.
 *
 * @since 1.0.0
 */
public class MemoryWriteComponent implements ComponentComposable {
    private final MemoryWriteCompConfig config;

    /**
     * Create a MemoryWriteComponent with the given configuration.
     *
     * @param config the component configuration
     */
    public MemoryWriteComponent(MemoryWriteCompConfig config) {
        this.config = config;
    }

    @Override
    public void addComponent(Graph graph, String nodeId, boolean isWaitForAll) {
        graph.addNode(nodeId, toExecutable(), isWaitForAll);
    }

    @Override
    public Executable<?, ?> toExecutable() {
        return new MemoryWriteExecutable(config);
    }
}

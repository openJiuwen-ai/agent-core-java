/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.graph.Graph;
import com.openjiuwen.core.workflow.ComponentComposable;

/**
 * Composable long-term memory write workflow component.
 *
 * <p>Mirrors Python's {@code MemoryWriteComponent} in
 * {@code openjiuwen/core/workflow/components/resource/memory_write_comp.py}.</p>
 */
public class MemoryWriteComponent implements ComponentComposable {

    private final MemoryWriteCompConfig config;

    public MemoryWriteComponent() {
        this(null);
    }

    public MemoryWriteComponent(MemoryWriteCompConfig config) {
        this.config = config;
    }

    @Override
    public void addComponent(Graph graph, String nodeId, boolean waitForAll) {
        graph.addNode(nodeId, toExecutable(), waitForAll);
    }

    @Override
    public Executable<?, ?> toExecutable() {
        return new MemoryWriteExecutable(config);
    }
}

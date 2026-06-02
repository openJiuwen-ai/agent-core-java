/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm.react;

import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.graph.Graph;
import com.openjiuwen.core.workflow.ComponentComposable;

/**
 * ReAct agent workflow component.
 * <p>
 * Mirrors Python's {@code ReActAgentComp} in
 * {@code openjiuwen.core.workflow.components.llm.react.react_component}.
 */
public class ReActAgentComp implements ComponentComposable {

    private final ReActAgentCompConfig config;
    private ReActAgentCompExecutable executable;

    public ReActAgentComp(ReActAgentCompConfig config) {
        this.config = config;
    }

    public ReActAgentCompConfig getConfig() {
        return config;
    }

    public ReActAgentCompExecutable getExecutable() {
        if (executable == null) {
            executable = (ReActAgentCompExecutable) toExecutable();
        }
        return executable;
    }

    @Override
    public Executable<?, ?> toExecutable() {
        return new ReActAgentCompExecutable(config);
    }

    @Override
    public void addComponent(Graph graph, String nodeId, boolean waitForAll) {
        graph.addNode(nodeId, getExecutable(), waitForAll);
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.components.llm.react;

import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.graph.Graph;
import com.openjiuwen.core.workflow.ComponentComposable;

/**
 * Composable workflow component for the ReAct agent executable.
 *
 * <p>Mirrors Python's {@code ReActAgentComp} in
 * {@code openjiuwen/core/workflow/components/llm/react/react_component.py}.</p>
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
            executable = toReActExecutable();
        }
        return executable;
    }

    @Override
    public Executable<?, ?> toExecutable() {
        return toReActExecutable();
    }

    public ReActAgentCompExecutable toReActExecutable() {
        return new ReActAgentCompExecutable(config);
    }

    @Override
    public void addComponent(Graph graph, String nodeId, boolean waitForAll) {
        graph.addNode(nodeId, getExecutable(), waitForAll);
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.workflow.ComponentComposable;

/**
 * Workflow component that wraps a ReActAgent for use in workflow graphs.
 * <p>
 * Implements {@link ComponentComposable} — the component is added to the graph
 * via its executable representation (see {@link #toExecutable()}).
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.react.ReActAgentComp}.
 *
 * <pre>{@code
 * ReActAgentCompConfig config = ReActAgentCompConfig.builder()
 *         .modelClientConfig(clientConfig)
 *         .modelConfigObj(modelConfig)
 *         .maxIterations(5)
 *         .build();
 * ReActAgentComp reactComp = new ReActAgentComp(config);
 * reactComp.getExecutable().getAbilityManager().add(toolCard);
 * workflow.addWorkflowComp("react", reactComp, ...);
 * }</pre>
 *
 * @since 1.0.0
 */
public class ReActAgentComp implements ComponentComposable {
    private ReActAgentCompExecutable executable;
    private final ReActAgentCompConfig config;

    /**
     * Create a ReActAgentComp with the given configuration.
     *
     * @param config component configuration
     */
    public ReActAgentComp(ReActAgentCompConfig config) {
        this.config = config;
    }

    /**
     * Get the executable, creating it lazily on first access.
     *
     * @return the executable instance
     */
    public ReActAgentCompExecutable getExecutable() {
        if (executable == null) {
            executable = new ReActAgentCompExecutable(config);
        }
        return executable;
    }

    @Override
    public Executable<?, ?> toExecutable() {
        return getExecutable();
    }
}

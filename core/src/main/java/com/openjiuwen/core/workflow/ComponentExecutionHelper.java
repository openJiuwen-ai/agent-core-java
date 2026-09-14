/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.graph.Vertex;
import com.openjiuwen.core.graph.pregel.PregelConfig;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeSession;

import java.util.Map;

/**
 * Utility for executing a single workflow component outside the full graph.
 * <p>
 * Mirrors Python's {@code execute_single_component} function in
 * {@code openjiuwen/core/workflow/_workflow.py}.
 */
public final class ComponentExecutionHelper {

    private ComponentExecutionHelper() {
    }

    /**
     * Execute a single component and return the execution result.
     *
     * @param params component execution parameters
     * @return execution result, or {@code null} if no output
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> executeSingleComponent(ComponentExecutionParams params) {
        String nodeId = params.getNodeId();
        ComponentExecutable executor = params.getExecutor();
        Object inputs = params.getInputs();

        // 1. Create workflow runtime session.
        WorkflowRuntimeSession workflowSession = new WorkflowRuntimeSession(
                nodeId,
                null,
                nodeId,
                InMemoryState.create(),
                null);

        // 3. Create Vertex
        Vertex vertex = new Vertex(nodeId, executor);

        // 4. Initialize Vertex
        vertex.init(workflowSession, Map.of("context", params.getContext() != null ? params.getContext() : ""));

        // 5. Submit input data, matching Python's non-InteractiveInput branch.
        if (!(inputs instanceof InteractiveInput)) {
            workflowSession.state().commitUserInputs(inputs);
        }

        // 6. Execute component – invoke directly
        Object result = executor.invoke(
                inputs != null ? inputs : Map.of(),
                params.getSession(),
                params.getContext());

        return result instanceof Map ? (Map<String, Object>) result : null;
    }
}

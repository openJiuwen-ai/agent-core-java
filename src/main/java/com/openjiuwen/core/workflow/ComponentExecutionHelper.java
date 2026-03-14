/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.graph.ExecutableGraph;
import com.openjiuwen.core.graph.PregelGraph;
import com.openjiuwen.core.graph.Vertex;
import com.openjiuwen.core.graph.pregel.PregelConfig;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.session.state.WorkflowStateCollection;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility for executing a single workflow component outside the full graph.
 * <p>
 * Mirrors Python's {@code execute_single_component(params)} function from {@code _workflow.py}.
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
        Map<String, Object> inputs = params.getInputs();

        // 1. Create WorkflowSession
        WorkflowSession workflowSession = new WorkflowSession(
                nodeId,
                null,
                nodeId,
                InMemoryState.create(),
                null);

        // 2. Create NodeSession
        NodeSession nodeSession = new NodeSession(workflowSession, nodeId, executor.getClass().getSimpleName());

        // 3. Create Vertex
        Vertex vertex = new Vertex(nodeId, executor);

        // 4. Initialize Vertex
        vertex.init(workflowSession, Map.of("context", params.getContext() != null ? params.getContext() : ""));

        // 5. Submit input data
        if (workflowSession.state() instanceof WorkflowStateCollection stateCollection) {
            stateCollection.commitUserInputs(inputs != null ? inputs : Map.of());
        }

        // 6. Execute component – invoke directly
        Object result = executor.invoke(
                inputs != null ? inputs : Map.of(),
                params.getSession(),
                params.getContext());

        return result instanceof Map ? (Map<String, Object>) result : null;
    }
}

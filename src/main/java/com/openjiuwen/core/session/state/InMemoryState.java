/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.state;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating in-memory workflow states (CommitState instances backed by InMemoryCommitState).
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.state.workflow_state.InMemoryState}.
 * 
 * @since 0.1.7
 */
public final class InMemoryState {
    /**
     * InMemoryState.
     * 
     * @since 0.1.7
     */
    private InMemoryState() {
        // utility class
    }

    /**
     * Create a new WorkflowCommitState backed by in-memory commit states.
     * 
     * @param ioState initial IO state, nullable
     * @param globalState initial global state, nullable
     * @param compState initial component state, nullable
     * @param workflowState initial workflow state, nullable
     * @param traceState initial trace state, nullable
     * @return a new WorkflowCommitState instance
     * @since 0.1.7
     */
    public static WorkflowCommitState create(Map<String, Object> ioState, Map<String, Object> globalState,
            Map<String, Object> compState, Map<String, Object> workflowState, Map<String, Object> traceState) {
        return new WorkflowCommitState(new InMemoryCommitState(new InMemoryStateLike(ioState)),
                new InMemoryCommitState(new InMemoryStateLike(globalState)),
                new InMemoryCommitState(new InMemoryStateLike(compState)),
                new InMemoryCommitState(new InMemoryStateLike(workflowState)),
                traceState != null ? traceState : new HashMap<>(), "", State.DEFAULT_NODE_ID);
    }

    /**
     * Create a new WorkflowCommitState with empty states.
     * 
     * @return a new WorkflowCommitState instance
     * @since 0.1.7
     */
    public static WorkflowCommitState create() {
        return create(null, null, null, null, null);
    }

    /**
     * fromMap.
     * 
     * @param stateMap stateMap
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public static WorkflowCommitState fromMap(Map<String, Object> stateMap) {
        if (stateMap == null) {
            return create();
        }
        return create((Map<String, Object>) stateMap.get(State.IO_STATE_KEY),
                (Map<String, Object>) stateMap.get(State.GLOBAL_STATE_KEY),
                (Map<String, Object>) stateMap.get(State.COMP_STATE_KEY),
                (Map<String, Object>) stateMap.get(State.WORKFLOW_STATE_KEY),
                (Map<String, Object>) stateMap.get(State.TRACE_STATE_KEY));
    }
}

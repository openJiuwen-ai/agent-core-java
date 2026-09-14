/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.session.state;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating in-memory workflow commit states.
 * <p>
 * Mirrors Python's {@code InMemoryState} in
 * {@code openjiuwen/core/session/state/workflow_state.py}.
 * </p>
 */
public final class InMemoryState {

    private InMemoryState() {
    }

    public static WorkflowCommitState create(Map<String, Object> ioState,
                                             Map<String, Object> globalState,
                                             Map<String, Object> compState,
                                             Map<String, Object> workflowState,
                                             Map<String, Object> traceState) {
        return new WorkflowCommitState(
                new InMemoryCommitState(new InMemoryStateLike(ioState)),
                new InMemoryCommitState(new InMemoryStateLike(globalState)),
                new InMemoryCommitState(new InMemoryStateLike(compState)),
                new InMemoryCommitState(new InMemoryStateLike(workflowState)),
                traceState != null ? traceState : new HashMap<>(),
                "",
                State.DEFAULT_NODE_ID
        );
    }

    public static WorkflowCommitState create() {
        return create(null, null, null, null, null);
    }

    @SuppressWarnings("unchecked")
    public static WorkflowCommitState fromMap(Map<String, Object> stateMap) {
        if (stateMap == null) {
            return create();
        }
        return create(
                (Map<String, Object>) stateMap.get(State.IO_STATE_KEY),
                (Map<String, Object>) stateMap.get(State.GLOBAL_STATE_KEY),
                (Map<String, Object>) stateMap.get(State.COMP_STATE_KEY),
                (Map<String, Object>) stateMap.get(State.WORKFLOW_STATE_KEY),
                (Map<String, Object>) stateMap.getOrDefault("trace_state", null)
        );
    }
}

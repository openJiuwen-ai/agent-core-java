/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.state.WorkflowStateCollection;

import java.util.Map;

/**
 * Abstract atomic node that validates the session, invokes the inner logic,
 * and commits component state.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.atomic_node.AsyncAtomicNode}.
 * Java uses synchronous execution with Virtual Threads, so there is no separate sync variant.
 */
public abstract class AtomicNode {

    /**
     * Execute the atomic node operation with session validation and state commit.
     *
     * @param kwargs keyword arguments (must include "session")
     * @return the result of the atomic operation
     */
    public Object atomicInvoke(Map<String, Object> kwargs) {
        BaseSession session = (BaseSession) kwargs.get("session");
        WorkflowStateCollection state = validateSessionAndState(session);
        Object result = doAtomicInvoke(kwargs);
        state.commitCmp();
        return result;
    }

    /**
     * Internal atomic invoke logic to be implemented by subclasses.
     *
     * @param kwargs keyword arguments
     * @return the result
     */
    protected abstract Object doAtomicInvoke(Map<String, Object> kwargs);

    /**
     * Validate that the session and its state are suitable for atomic operations.
     *
     * @param session the session to validate
     */
    private static WorkflowStateCollection validateSessionAndState(BaseSession session) {
        if (session == null) {
            throw ErrorHelper.buildError(StatusCode.GRAPH_STATE_COMMIT_ERROR,
                    "reason", "session is None");
        }

        Object state = session.state();
        if (!(state instanceof WorkflowStateCollection)) {
            throw ErrorHelper.buildError(StatusCode.GRAPH_STATE_COMMIT_ERROR,
                    "reason", "session does not support commit state");
        }
        return (WorkflowStateCollection) state;
    }
}

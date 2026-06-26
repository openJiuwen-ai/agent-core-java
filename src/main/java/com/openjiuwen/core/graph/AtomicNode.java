/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.session.state.WorkflowCommitState;

import java.util.Map;

/**
 * Synchronous atomic graph node wrapper.
 *
 * <p>Mirrors Python's {@code AtomicNode} and {@code _validate_session_and_state} in
 * {@code openjiuwen/core/graph/atomic_node.py}.</p>
 */
public abstract class AtomicNode {

    /**
     * Validate session state, invoke the node, commit component state, and return the result.
     *
     * @param kwargs keyword-style invocation arguments
     * @return node result
     * @throws Exception when the concrete node invocation fails
     */
    public Object atomicInvoke(Map<String, Object> kwargs) throws Exception {
        Map<String, Object> safeKwargs = kwargs != null ? kwargs : Map.of();
        WorkflowCommitState state = validateSessionAndState(safeKwargs.get("session"));
        Object result = atomicInvokeInternal(safeKwargs);
        state.commitCmp();
        return result;
    }

    /**
     * Concrete node body.
     *
     * @param kwargs keyword-style invocation arguments
     * @return node result
     * @throws Exception when invocation fails
     */
    protected abstract Object atomicInvokeInternal(Map<String, Object> kwargs) throws Exception;

    static WorkflowCommitState validateSessionAndState(Object session) {
        if (session == null) {
            throw ErrorHelper.buildError(StatusCode.GRAPH_STATE_COMMIT_ERROR, "reason", "session is None");
        }
        if (!(session instanceof GraphSession graphSession)) {
            throw ErrorHelper.buildError(StatusCode.GRAPH_STATE_COMMIT_ERROR, "reason", "session is not base session");
        }
        Object state = graphSession.state();
        if (!(state instanceof WorkflowCommitState commitState)) {
            throw ErrorHelper.buildError(StatusCode.GRAPH_STATE_COMMIT_ERROR,
                    "reason", "session is not support commit state");
        }
        return commitState;
    }
}

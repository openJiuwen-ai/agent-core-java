/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import com.openjiuwen.core.session.state.WorkflowCommitState;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Asynchronous atomic graph node wrapper.
 *
 * <p>Mirrors Python's {@code AsyncAtomicNode} and {@code _validate_session_and_state} in
 * {@code openjiuwen/core/graph/atomic_node.py}.</p>
 */
public abstract class AsyncAtomicNode {

    /**
     * Validate session state, invoke the async node, commit component state, and return the result stage.
     *
     * @param kwargs keyword-style invocation arguments
     * @return result stage
     */
    public CompletionStage<Object> atomicInvoke(Map<String, Object> kwargs) {
        Map<String, Object> safeKwargs = kwargs != null ? kwargs : Map.of();
        WorkflowCommitState state = AtomicNode.validateSessionAndState(safeKwargs.get("session"));
        return atomicInvokeInternal(safeKwargs).thenApply(result -> {
            state.commitCmp();
            return result;
        });
    }

    /**
     * Concrete async node body.
     *
     * @param kwargs keyword-style invocation arguments
     * @return result stage
     */
    protected abstract CompletionStage<Object> atomicInvokeInternal(Map<String, Object> kwargs);
}

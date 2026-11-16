/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import com.openjiuwen.core.session.BaseSession;

/**
 * Holds the session a compilation's routers resolve against.
 *
 * <p>Routers are shared definitions, but a route decision must be based on the
 * state of the run being executed. Each compilation receives its own scope, so
 * the branch selectors registered on that compilation's engine read this
 * scope instead of a workflow-level proxy that any later compile can overwrite.
 *
 * <p>The scope is written once before {@code pregel.run} and only read while
 * routing, which keeps it immutable for the duration of the run.
 *
 * @since 0.1.17
 */
public final class RunRoutingScope {
    private final BaseSession compileSession;
    private volatile BaseSession session;

    /**
     * Creates a scope that initially resolves against the compile session.
     *
     * @param compileSession session captured at compilation time
     */
    public RunRoutingScope(BaseSession compileSession) {
        this.compileSession = compileSession;
        this.session = compileSession;
    }

    /**
     * Points this scope at the session of the invocation that is starting.
     *
     * @param invocationSession session of the current invocation, may be
     *                          {@code null} to fall back to the compile session
     */
    public void bind(BaseSession invocationSession) {
        this.session = invocationSession != null ? invocationSession : compileSession;
    }

    /**
     * Evaluates a branch with the session of the node that owns it.
     *
     * <p>Routing must observe the state and identity of the source node, not
     * the workflow. The node's session carries the run it was initialized for,
     * which already isolates overlapping runs, and it names the component so
     * the branch route trace lands on that component's span instead of being
     * dropped as an anonymous invoke. Falls back to the bound invocation
     * session when the node has not been initialized.</p>
     *
     * @param branch branch to evaluate
     * @param source vertex owning the conditional edge, may be {@code null}
     * @return the routing result
     */
    public Object route(Branch branch, Vertex source) {
        BaseSession nodeSession = source == null ? null : source.routingSession();
        return branch.route(nodeSession != null ? nodeSession : session);
    }
}

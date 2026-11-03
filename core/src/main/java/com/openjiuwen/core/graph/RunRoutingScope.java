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
 */
public final class RunRoutingScope {

    private final BaseSession compileSession;
    private volatile BaseSession session;

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
     * Evaluates a branch against this scope's session.
     *
     * @param branch branch to evaluate
     * @return the routing result
     */
    public Object route(Branch branch) {
        return branch.route(session);
    }
}

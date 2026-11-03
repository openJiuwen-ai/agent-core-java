/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import com.openjiuwen.core.session.BaseSession;

/**
 * A router definition that can be evaluated against an explicit run session.
 *
 * <p>A router registered on a graph is shared by every run of that graph, so it
 * must not keep the session of the run that happened to compile last. Routers
 * implementing this interface receive the session of the current run at
 * dispatch time instead, which keeps the shared definition free of run state.
 *
 * @since 0.1.17
 */
public interface RunScopedRouter {
    /**
     * Routes using the session of the current run.
     *
     * <p>Implementations must not store {@code runSession}; the same router
     * instance is evaluated by concurrent runs.
     *
     * @param runSession session of the run evaluating this router
     * @return the routing result
     */
    Object routeWith(BaseSession runSession);
}

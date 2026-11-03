/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.graph.Router;
import com.openjiuwen.core.graph.RunScopedRouter;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.ProxySession;
import com.openjiuwen.core.workflow.internal.RouterSession;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Adapts a user router function into a graph router.
 *
 * <p>The graph dispatches a conditional edge without arguments, so the router
 * function cannot receive its session as a call argument. This adapter is
 * therefore evaluated through {@link RunScopedRouter}, which supplies the
 * session of the run currently executing. The workflow-level proxy is only a
 * fallback for callers that route outside a run.
 */
final class FunctionRouterAdapter implements Router, Function<Object, Object>, RunScopedRouter {

    private final Function<Object, Object> delegate;
    private final Supplier<BaseSession> fallbackSession;

    FunctionRouterAdapter(Function<Object, Object> delegate, Supplier<BaseSession> fallbackSession) {
        this.delegate = delegate;
        this.fallbackSession = fallbackSession;
    }

    /**
     * Variadic entry point used by the graph router contract.
     *
     * <p>A {@link BaseSession} argument is treated as the run being dispatched;
     * the graph itself invokes this with no arguments, in which case the run
     * session is resolved by {@link #routeWith(BaseSession)}.
     *
     * @param args optional router arguments
     * @return the routing result
     */
    @Override
    public Object route(Object... args) {
        BaseSession runSession = null;
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof BaseSession baseSession) {
                    runSession = baseSession;
                    break;
                }
            }
        }
        return routeWith(runSession);
    }

    /**
     * Legacy entry point used when the router is invoked with a session
     * argument, or with none at all.
     *
     * @param input a session, or {@code null} when dispatched by the graph
     * @return the routing result
     */
    @Override
    public Object apply(Object input) {
        return routeWith(input instanceof BaseSession baseSession ? baseSession : null);
    }

    @Override
    public Object routeWith(BaseSession runSession) {
        BaseSession effective = runSession != null ? runSession : fallbackSession.get();
        return delegate.apply(toRouterSession(effective));
    }

    private static RouterSession toRouterSession(BaseSession session) {
        if (session instanceof ProxySession proxySession) {
            return new RouterSession(proxySession);
        }
        return RouterSession.of(session);
    }
}

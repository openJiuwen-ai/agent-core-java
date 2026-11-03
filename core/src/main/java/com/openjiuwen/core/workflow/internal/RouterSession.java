/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.ProxySession;
import com.openjiuwen.core.session.stream.StreamWriter;

import java.util.Map;

/**
 * Mirrors Python's router session wrapper used by
 * {@code openjiuwen/core/workflow/_workflow.py}.
 *
 * <p>A router function receives this wrapper as its session argument. The
 * wrapper must expose the state of the run being executed, so an instance
 * created with {@link #of(BaseSession)} is bound to that run for its whole
 * lifetime and never consults shared, mutable state.</p>
 */
public class RouterSession extends com.openjiuwen.core.session.internal.RouterSession {

    private final ProxySession proxySession;
    private final BaseSession boundSession;

    /**
     * Creates a wrapper that resolves the session through a proxy.
     *
     * <p>Kept for callers that bind the proxy after construction. Workflow
     * routing uses {@link #of(BaseSession)} instead so concurrent runs never
     * resolve each other's session.
     *
     * @param proxySession delegating session, may be {@code null}
     */
    public RouterSession(ProxySession proxySession) {
        super(proxySession);
        this.proxySession = proxySession;
        this.boundSession = null;
    }

    private RouterSession(BaseSession boundSession) {
        super(boundSession);
        this.proxySession = null;
        this.boundSession = boundSession;
    }

    /**
     * Creates a wrapper bound to the session of one specific run.
     *
     * @param runSession session of the run evaluating the router
     * @return a router session that never resolves another run's state
     */
    public static RouterSession of(BaseSession runSession) {
        return new RouterSession(runSession);
    }

    public BaseSession innerSession() {
        if (boundSession != null) {
            return boundSession;
        }
        return proxySession != null ? proxySession.getSession() : null;
    }

    public Object getGlobal(String key) {
        if (boundSession != null) {
            return boundSession.state() == null ? null : boundSession.state().getGlobal(key);
        }
        return proxySession != null ? proxySession.getGlobal(key) : null;
    }

    @Override
    public String executableId() {
        BaseSession current = innerSession();
        return current == null ? "" : current.sessionId();
    }

    @Override
    public String sessionId() {
        BaseSession current = innerSession();
        return current == null ? "" : current.sessionId();
    }

    @Override
    public void updateState(Map<String, Object> data) {
        BaseSession current = innerSession();
        if (current != null) {
            current.updateState(data);
        }
    }

    @Override
    public Object getState(Object key) {
        BaseSession current = innerSession();
        return current == null || current.state() == null ? null : current.state().get(key);
    }

    @Override
    public void updateGlobalState(Map<String, Object> data) {
        BaseSession current = innerSession();
        if (current != null && current.state() != null) {
            current.state().updateGlobal(data);
        }
    }

    @Override
    public Object getGlobalState(Object key) {
        return getGlobal(String.valueOf(key));
    }

    @Override
    public StreamWriter<?> streamWriter() {
        return null;
    }

    @Override
    public StreamWriter<?> customWriter() {
        return null;
    }

    @Override
    public void writeStream(Object data) {
    }

    @Override
    public void writeCustomStream(Map<String, Object> data) {
    }

    @Override
    public void trace(Map<String, Object> data) {
    }

    @Override
    public void traceError(Throwable error) {
    }

    @Override
    public void interact(Object value) {
    }
}

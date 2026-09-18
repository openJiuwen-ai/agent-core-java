/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.stream.StreamWriterManager;

/**
 * Delegating session used when the backing session is bound after construction.
 *
 * <p>Mirrors Python's {@code ProxySession} in
 * {@code openjiuwen/core/session/session.py}.</p>
 *
 * <p>Unbound calls return {@link BaseSession} defaults so workflow compilation
 * can hold a proxy before {@link #setSession(BaseSession)} runs.</p>
 */
public class ProxySession extends BaseSession {

    private BaseSession stub;

    public ProxySession() {
        this(null);
    }

    public ProxySession(BaseSession stub) {
        this.stub = stub;
    }

    public void setSession(BaseSession stub) {
        this.stub = stub;
    }

    public BaseSession getSession() {
        return stub;
    }

    /**
     * Read a global-state key from the bound session, or {@code null} when unbound.
     */
    public Object getGlobal(String key) {
        State state = state();
        return state == null ? null : state.getGlobal(key);
    }

    @Override
    public Config config() {
        return stub == null ? super.config() : stub.config();
    }

    @Override
    public State state() {
        return stub == null ? null : stub.state();
    }

    @Override
    public Object tracer() {
        return stub == null ? null : stub.tracer();
    }

    @Override
    public StreamWriterManager streamWriterManager() {
        return stub == null ? null : stub.streamWriterManager();
    }

    @Override
    public String sessionId() {
        return stub == null ? "" : stub.sessionId();
    }

    @Override
    public Object checkpointer() {
        return stub == null ? null : stub.checkpointer();
    }

    @Override
    public Object actorManager() {
        return stub == null ? null : stub.actorManager();
    }

    @Override
    public CallbackManager callbackManager() {
        return stub == null ? null : stub.callbackManager();
    }
}

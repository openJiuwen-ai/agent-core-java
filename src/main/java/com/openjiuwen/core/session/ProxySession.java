/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.session.config.SessionConfigAccess;
import com.openjiuwen.core.session.state.SessionStateAccess;

/**
 * Delegating session used when the backing session is bound after construction.
 *
 * <p>Mirrors Python's {@code ProxySession} in
 * {@code openjiuwen/core/session/session.py}.</p>
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

    @Override
    public SessionConfigAccess config() {
        return requireStub().config();
    }

    @Override
    public SessionStateAccess state() {
        return requireStub().state();
    }

    @Override
    public Object tracer() {
        return requireStub().tracer();
    }

    @Override
    public Object streamWriterManager() {
        return requireStub().streamWriterManager();
    }

    @Override
    public String sessionId() {
        return requireStub().sessionId();
    }

    @Override
    public Object checkpointer() {
        return requireStub().checkpointer();
    }

    private BaseSession requireStub() {
        if (stub == null) {
            throw new IllegalStateException("ProxySession has no backing session");
        }
        return stub;
    }
}

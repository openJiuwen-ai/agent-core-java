/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.stream.StreamWriterManager;

/**
 * Proxy session that delegates all calls to an underlying stub session.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.session.ProxySession}.
 * 
 * @since 0.1.7
 */
public class ProxySession extends BaseSession {
    private BaseSession stub;

    /**
     * ProxySession.
     * 
     * @since 0.1.7
     */
    public ProxySession() {
        this(null);
    }

    /**
     * ProxySession.
     * 
     * @param stub stub
     * @since 0.1.7
     */
    public ProxySession(BaseSession stub) {
        this.stub = stub;
    }

    /**
     * Set the underlying session implementation.
     * 
     * @param stub the session to delegate to
     * @since 0.1.7
     */
    public void setSession(BaseSession stub) {
        this.stub = stub;
    }

    /**
     * Get the underlying session implementation.
     * 
     * @return the stub session
     * @since 0.1.7
     */
    public BaseSession getStub() {
        return stub;
    }

    /**
     * config.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Config config() {
        return stub.config();
    }

    /**
     * state.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public State state() {
        return stub.state();
    }

    /**
     * tracer.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object tracer() {
        return stub.tracer();
    }

    /**
     * streamWriterManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public StreamWriterManager streamWriterManager() {
        return stub.streamWriterManager();
    }

    /**
     * callbackManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public CallbackManager callbackManager() {
        return stub.callbackManager();
    }

    /**
     * sessionId.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String sessionId() {
        return stub.sessionId();
    }

    /**
     * checkpointer.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object checkpointer() {
        return stub.checkpointer();
    }
}

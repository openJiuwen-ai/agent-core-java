/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.session;

import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.stream.StreamWriterManager;

/**
 * Proxy session that delegates all calls to an underlying stub session.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.session.ProxySession}.
 */
public class ProxySession extends BaseSession {

    private BaseSession stub;

    public ProxySession() {
        this(null);
    }

    public ProxySession(BaseSession stub) {
        this.stub = stub;
    }

    /**
     * Set the underlying session implementation.
     *
     * @param stub the session to delegate to
     */
    public void setSession(BaseSession stub) {
        this.stub = stub;
    }

    /**
     * Get the underlying session implementation.
     *
     * @return the stub session
     */
    public BaseSession getStub() {
        return stub;
    }

    @Override
    public Config config() {
        return stub.config();
    }

    @Override
    public State state() {
        return stub.state();
    }

    @Override
    public Object tracer() {
        return stub.tracer();
    }

    @Override
    public StreamWriterManager streamWriterManager() {
        return stub.streamWriterManager();
    }

    @Override
    public CallbackManager callbackManager() {
        return stub.callbackManager();
    }

    @Override
    public String sessionId() {
        return stub.sessionId();
    }

    @Override
    public Object checkpointer() {
        return stub.checkpointer();
    }
}
